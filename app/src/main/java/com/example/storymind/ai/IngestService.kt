package com.example.storymind.ai

import com.example.storymind.data.GraphEdge
import com.example.storymind.data.GraphNode
import com.example.storymind.data.WikiEntry

/**
 * Raw text generation, abstracted so [LintService] can be constructed with a fake in tests
 * instead of the real [OnDeviceEngine]. `OnDeviceEngine::generate` satisfies this signature
 * directly via SAM conversion (its `sampler` parameter defaults to null). [IngestService] uses
 * [IngestTextEngine] instead — see that interface's KDoc for why ingest doesn't share this one.
 */
fun interface OnDeviceTextEngine {
    suspend fun generate(prompt: String): String
}

data class IngestResult(
    val chapterSummary: String,
    val wikiEntries: List<WikiEntry>,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val orphanIds: Set<String>,
)

/**
 * Turns a chapter's manuscript text into wiki data using the on-device model.
 * Node layout (coordinates) and orphan detection are computed here deterministically,
 * never by the model.
 */
class IngestService(private val engine: IngestTextEngine) {

    /**
     * [existingWiki] carries entities already established in earlier chapters. It's forwarded
     * to the model as context so it reuses their ids/names, and also backs a name-based safety
     * net here: if the model still mints a fresh id for something whose name matches an existing
     * entry, that id is rewritten to the established one before nodes/edges are built. The match
     * falls back from exact name equality to a suffix check (either name ending with the other)
     * so a dropped-surname nickname the model reaches for later — "이지민" becoming "지민" — still
     * resolves to the same entity instead of minting a duplicate.
     *
     * Returns null when [generateParsed] exhausts every retry without producing parseable JSON —
     * mirrors [LintService.lint]'s use of [LintResult.succeeded] to keep "the model never gave us
     * anything usable" distinguishable from "a real empty result", except here a plain nullable
     * return is enough: unlike [LintResult], nothing downstream of a successful [IngestResult]
     * ever needs to see the failed case too, so there's no shared success/failure envelope type to
     * thread through [com.example.storymind.data.StoryRepository.commitIngest] and
     * [com.example.storymind.ai.ChapterProgress.merge] just to carry one boolean neither of them
     * would ever act on — [com.example.storymind.work.IngestWorker] checks it right here, before
     * either of those is called at all.
     */
    suspend fun ingest(
        chapterLabel: String,
        title: String,
        paragraphs: List<String>,
        existingWiki: List<WikiEntry> = emptyList(),
    ): IngestResult? {
        val prompt = IngestSchema.buildIngestPrompt(title, paragraphs, existingWiki)
        val parsed = generateParsed(prompt) ?: return null

        val idByName = existingWiki.associateBy({ it.name.trim() }, { it.id })
        fun matchExistingId(name: String): String? {
            val trimmed = name.trim()
            idByName[trimmed]?.let { return it }
            return idByName.entries.firstOrNull { (existingName, _) ->
                minOf(existingName.length, trimmed.length) >= MIN_NAME_SUFFIX_MATCH_LENGTH &&
                    (existingName.endsWith(trimmed) || trimmed.endsWith(existingName))
            }?.value
        }
        val idRemap = parsed.entities
            .filter { it.id !in idByName.values }
            .mapNotNull { entity -> matchExistingId(entity.name)?.let { entity.id to it } }
            .toMap()
        fun resolvedId(id: String) = idRemap[id] ?: id

        // Gemma occasionally lists the same entity twice in one response (under the same id, or
        // under two names that resolve to the same id via matchExistingId). Keeping only the last
        // occurrence stops that chapter's wiki/graph update from double-counting a single entity.
        val dedupedEntities = parsed.entities.associateBy { resolvedId(it.id) }.values.toList()

        val wikiEntries = dedupedEntities.map { entity ->
            WikiEntry(
                id = resolvedId(entity.id),
                type = entity.type,
                name = entity.name,
                desc = entity.desc,
                chapter = chapterLabel,
            )
        }

        val nodes = dedupedEntities.map { entity ->
            GraphNode(id = resolvedId(entity.id), type = entity.type, label = entity.name, x = 0f, y = 0f)
        }

        val knownIds = nodes.mapTo(mutableSetOf()) { it.id }
        val edges = parsed.relations
            .map { GraphEdge(from = resolvedId(it.from), to = resolvedId(it.to)) }
            .filter { it.from in knownIds && it.to in knownIds }

        val connectedIds = edges.flatMapTo(mutableSetOf()) { listOf(it.from, it.to) }
        val orphanIds = knownIds - connectedIds

        return IngestResult(
            chapterSummary = parsed.chapterSummary,
            wikiEntries = wikiEntries,
            nodes = nodes,
            edges = edges,
            orphanIds = orphanIds,
        )
    }

    /**
     * Gemma occasionally breaks its own JSON schema in a different way each time (a dropped key,
     * a doubled brace, a stray token) — one-off decoding slips rather than a pattern worth chasing
     * with more regex repairs. Since the same slip is unlikely to repeat on a fresh generation,
     * retrying the whole prompt is cheap insurance against losing a chapter's wiki data to it.
     * [samplerForAttempt] escalates the sampler across those retries instead of reusing the same
     * settings three times — see its KDoc for why a flat retry (this method's shape before
     * sampler control existed) wastes attempts once temperature is low.
     *
     * Returns null after [MAX_ATTEMPTS] straight parse failures instead of falling back to
     * [IngestParser.parse]'s empty result — a silent empty [ParsedIngest] here used to read as "a
     * chapter that genuinely has no entities" once it reached [IngestWorker], which committed it
     * and flipped `ingested = true` exactly like a real success. That's how 2화 lost its wiki
     * data (2026-07): the chapter *looked* done in the UI while nothing had actually been
     * extracted. Returning null lets [IngestWorker] refuse to commit and report failure instead.
     */
    private suspend fun generateParsed(prompt: String): ParsedIngest? {
        lateinit var raw: String
        repeat(MAX_ATTEMPTS) { attempt ->
            raw = engine.generate(prompt, samplerForAttempt(attempt))
            IngestParser.parseOrNull(raw)?.let { return it }
            val attemptsLeft = MAX_ATTEMPTS - attempt - 1
            ingestLogger.w(TAG, "Ingest JSON parse failed (attempt ${attempt + 1}/$MAX_ATTEMPTS), $attemptsLeft retries left")
        }
        // Every retry's raw response is a regression-fixture candidate the moment the model
        // never produces something IngestParser.repairToFixpoint can recover — see
        // ingestFailureRecorder's KDoc. Only the last attempt's raw text is kept: it's the one
        // fixture that actually reflects the failure this call is about to report.
        ingestFailureRecorder.record(raw)
        ingestLogger.w(TAG, "Ingest gave up after $MAX_ATTEMPTS attempts, reporting failure instead of an empty result")
        return null
    }

    companion object {
        private const val TAG = "IngestService"
        private const val MAX_ATTEMPTS = 3

        /** Shortest name either side of a suffix match may be, to keep single-character names
         * (rare, but not impossible) from matching almost anything by coincidence. */
        private const val MIN_NAME_SUFFIX_MATCH_LENGTH = 2

        /**
         * Extraction (as opposed to open-ended writing) is a low-creativity task, so low
         * temperature is the right default — attempt 0's temperature 0.1 is near-deterministic,
         * favoring the reproducible, well-formed response the model is most likely to produce.
         * But a flat retry loop that keeps reusing the same low-temperature settings makes
         * [MAX_ATTEMPTS] mostly pointless once a slip is sampling noise rather than a systematic
         * error: greedy-ish decoding tends to reproduce the same tail-token slip on the next call
         * with the same prompt, so attempts 2 and 3 raise the temperature instead, trading some of
         * attempt 0's reliability for a real chance at a *different* output than the one that just
         * failed to parse. topK/topP are held at reasonable defaults across all three — only
         * temperature (the escalation knob) and seed (see below) change per attempt; these values
         * are a starting point pending on-device measurement (see [IngestSamplingSmokeTest] in
         * androidTest), not a tuned result.
         *
         * `seed` is attempt-derived so retrying the same chapter after a full [MAX_ATTEMPTS]
         * exhaustion (e.g. via [com.example.storymind.ui.StoryViewModel.retryIngest]) doesn't
         * necessarily replay the exact same three generations that just failed.
         */
        private fun samplerForAttempt(attempt: Int): SamplerSettings = when (attempt) {
            0 -> SamplerSettings(topK = 40, topP = 0.9, temperature = 0.1, seed = BASE_SEED)
            1 -> SamplerSettings(topK = 40, topP = 0.9, temperature = 0.4, seed = BASE_SEED + 1)
            else -> SamplerSettings(topK = 40, topP = 0.9, temperature = 0.7, seed = BASE_SEED + 2)
        }

        /** Arbitrary but fixed base so [samplerForAttempt]'s seeds are stable across runs — not
         * chosen for any statistical property, just a fixed starting point to offset from. */
        private const val BASE_SEED = 42
    }
}