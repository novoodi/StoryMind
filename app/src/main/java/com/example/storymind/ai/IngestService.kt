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
     * Returns null when [generateResult] exhausts every retry without producing an acceptable
     * result — mirrors [LintService.lint]'s use of [LintResult.succeeded] to keep "the model never
     * gave us anything usable" distinguishable from "a real empty result", except here a plain
     * nullable return is enough: unlike [LintResult], nothing downstream of a successful
     * [IngestResult] ever needs to see the failed case too, so there's no shared success/failure
     * envelope type to thread through [com.example.storymind.data.StoryRepository.commitIngest] and
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
        return generateResult(prompt, chapterLabel, existingWiki)
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
     * An unresolved relation — [buildResult] found a relation whose `from`/`to` doesn't match any
     * id this same response defined in `entities[]` (2026-07 1화 incident: "진성" written as "성"
     * in `relations[]`) — is now treated as the *same class* of problem as a parse failure, sharing
     * its retry budget instead of being silently accepted the moment parsing itself succeeds. Both
     * mean "this generation is incomplete"; only the first was retried before, which was the wrong
     * asymmetry once measured — under the v2 prompt (2026-07, `IdConsistencyPromptSmokeTest`),
     * unresolved-relation responses (2/5) were *more* common than outright parse failure in that
     * sample. On a non-final attempt, an unresolved relation discards this attempt's entire result
     * and moves to the next one exactly like a parse failure would; no attempt budget is added for
     * it; a chapter that alternates parse failures and id mismatches across its [MAX_ATTEMPTS]
     * attempts still costs at most that many generations. Only the *last* attempt accepts an
     * unresolved relation rather than retrying it — there's no attempt left to retry into — and
     * reports it exactly as before via [reportUnresolvedRelations]: the chapter still commits
     * successfully, just missing that one relation.
     *
     * Returns null after [MAX_ATTEMPTS] attempts that were each either unparseable or (on every
     * attempt but the last) had an unresolved relation, instead of falling back to
     * [IngestParser.parse]'s empty result — a silent empty [ParsedIngest] here used to read as "a
     * chapter that genuinely has no entities" once it reached [IngestWorker], which committed it
     * and flipped `ingested = true` exactly like a real success. That's how 2화 lost its wiki
     * data (2026-07): the chapter *looked* done in the UI while nothing had actually been
     * extracted. Returning null lets [IngestWorker] refuse to commit and report failure instead.
     */
    private suspend fun generateResult(
        prompt: String,
        chapterLabel: String,
        existingWiki: List<WikiEntry>,
    ): IngestResult? {
        lateinit var raw: String
        repeat(MAX_ATTEMPTS) { attempt ->
            raw = engine.generate(prompt, samplerForAttempt(attempt))
            val attemptsLeft = MAX_ATTEMPTS - attempt - 1
            val parsed = IngestParser.parseOrNull(raw)
            if (parsed == null) {
                ingestLogger.w(TAG, "Ingest JSON parse failed (attempt ${attempt + 1}/$MAX_ATTEMPTS), $attemptsLeft retries left")
                return@repeat
            }

            val built = buildResult(parsed, chapterLabel, existingWiki)
            val isLastAttempt = attemptsLeft == 0
            if (built.unresolvedEdges.isEmpty() || isLastAttempt) {
                if (built.unresolvedEdges.isNotEmpty()) {
                    reportUnresolvedRelations(built.unresolvedEdges, built.knownIds)
                }
                return built.result
            }
            // Soft failure, not the final attempt: no PartialDropRecorder call here — this
            // attempt's whole result is being discarded and retried, not committed with a drop, so
            // recording it would leave a fixture for a "drop" that never actually reached the
            // author's data. A log line is enough to see it happened.
            ingestLogger.w(
                TAG,
                "Ingest produced ${built.unresolvedEdges.size} unresolved relation id(s) " +
                    "(attempt ${attempt + 1}/$MAX_ATTEMPTS), $attemptsLeft retries left: " +
                    built.unresolvedEdges.joinToString { "${it.from}->${it.to}" },
            )
        }
        // Every retry's raw response is a regression-fixture candidate the moment the model
        // never produces something acceptable — see ingestFailureRecorder's KDoc. Only the last
        // attempt's raw text is kept: it's the one fixture that actually reflects the failure this
        // call is about to report.
        ingestFailureRecorder.record(raw)
        ingestLogger.w(TAG, "Ingest gave up after $MAX_ATTEMPTS attempts, reporting failure instead of an empty result")
        return null
    }

    /** Everything [generateResult] needs to judge one attempt's output and report it either way:
     * the built [result], the relations dropped because their `from`/`to` didn't match this
     * response's own `entities[]` ([unresolvedEdges]), and the id set they were checked against
     * ([knownIds], needed by [reportUnresolvedRelations]'s log message). */
    private data class BuiltResult(
        val result: IngestResult,
        val unresolvedEdges: List<GraphEdge>,
        val knownIds: Set<String>,
    )

    private fun buildResult(
        parsed: ParsedIngest,
        chapterLabel: String,
        existingWiki: List<WikiEntry>,
    ): BuiltResult {
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
        val (edges, unresolvedEdges) = parsed.relations
            .map { GraphEdge(from = resolvedId(it.from), to = resolvedId(it.to)) }
            .partition { it.from in knownIds && it.to in knownIds }

        val connectedIds = edges.flatMapTo(mutableSetOf()) { listOf(it.from, it.to) }
        val orphanIds = knownIds - connectedIds

        val result = IngestResult(
            chapterSummary = parsed.chapterSummary,
            wikiEntries = wikiEntries,
            nodes = nodes,
            edges = edges,
            orphanIds = orphanIds,
        )
        return BuiltResult(result, unresolvedEdges, knownIds)
    }

    /**
     * A relation whose `from`/`to` doesn't match any id in this same response's `entities[]` gets
     * dropped by [buildResult]'s edge filter — the 2026-07 1화 incident happened this way: the
     * model defined "진성" as an entity but wrote two relations against the truncated id "성",
     * which matches nothing. That drop doesn't change [ingest]'s success/failure verdict (the
     * chapter's other entities/relations are fine, so this stays a successful [IngestResult]) —
     * it just stops being invisible. [generateResult] only calls this for the attempt it actually
     * accepts (clean, or the last attempt) — an attempt discarded via the soft-failure retry above
     * never reaches here, so [PartialDropRecorder] only ever sees drops that really happened to
     * committed data. See [PartialDropRecorder]'s KDoc for why this isn't [ingestFailureRecorder].
     */
    private fun reportUnresolvedRelations(dropped: List<GraphEdge>, knownIds: Set<String>) {
        val detail = buildString {
            appendLine("Dropped ${dropped.size} relation(s) referencing an id absent from this response's entities:")
            dropped.forEach { edge ->
                appendLine("  ${edge.from} -> ${edge.to}  [${shadowMatchLine(edge, knownIds)}]")
            }
            appendLine("Known entity ids in this response: ${knownIds.sorted()}")
        }
        ingestLogger.w(TAG, detail)
        partialDropRecorder.record(detail)
    }

    /** Formats [shadowMatchDescription] for whichever side(s) of [edge] are actually unresolved
     * (usually one, but both `from` and `to` could independently miss `knownIds`). Display-only —
     * feeds [reportUnresolvedRelations]'s log/fixture text, never [buildResult]'s edge filter. */
    private fun shadowMatchLine(edge: GraphEdge, knownIds: Set<String>): String =
        listOfNotNull(edge.from.takeIf { it !in knownIds }, edge.to.takeIf { it !in knownIds })
            .joinToString("; ") { unresolvedId -> "$unresolvedId: ${shadowMatchDescription(unresolvedId, knownIds)}" }

    companion object {
        private const val TAG = "IngestService"
        private const val MAX_ATTEMPTS = 3

        /**
         * Shadow mode only: reports what a conservative substring-based recovery *would* have
         * matched for [unresolvedId], without ever applying it — [buildResult]'s edge filter still
         * drops the relation exactly as before. The two incidents on record so far are both
         * substring relationships in one direction or the other: "성" (truncated) is what's left
         * after dropping "진" from "진성" — a suffix — and "엄마음" (an extra trailing syllable) has
         * "엄마" as a prefix. Checking substring containment in *both* directions with a single
         * `contains` (rather than hand-rolling separate prefix/suffix branches, the way
         * [matchExistingId] does with `endsWith`/`startsWith`) catches both shapes without having
         * to predict which one the next incident will be.
         *
         * A match is only reported when exactly one [knownIds] entry qualifies — zero means
         * nothing to recover from, and two or more means the guess is genuinely ambiguous (no
         * principled way to pick one over the other), so both are logged as-is rather than forcing
         * a pick. This is deliberately not wired into the merge path (design decision, this batch):
         * false positives here would misattribute a relation to the wrong entity, which is worse
         * than the current silent drop, so this stays observational until real incident data says
         * otherwise.
         *
         * Deliberately **no** minimum length on [unresolvedId] itself, even though a one-character
         * id is exactly the risky shape a length floor would target (a common Korean given-name
         * syllable can end many unrelated ids) — the motivating incident's own unresolved id, "성",
         * *is* one character, so rejecting short ids outright would exclude the very case this
         * exists to observe. The single-candidate rule above is the actual safety net instead: a
         * common syllable naturally surfaces more than one candidate (see
         * [IngestServiceTest]'s ambiguous-candidate case, where "성" alone matches both "진성" and
         * "완성") and reports ambiguous rather than guessing; a clean single match only survives
         * when the id genuinely isn't ambiguous in context. A blank id *is* still guarded
         * explicitly, since it isn't a length-floor question but a degenerate-input one:
         * `"x".contains("")` is trivially true for every string, which would otherwise turn every
         * known id into a "candidate" instead of none. (In practice [unresolvedId] should never be
         * blank — [IngestParser] already drops blank `from`/`to` before this runs — but the guard
         * costs nothing and keeps this function correct standalone.)
         */
        internal fun shadowMatchDescription(unresolvedId: String, knownIds: Set<String>): String {
            if (unresolvedId.isBlank()) return "no candidate (blank id)"
            val candidates = knownIds
                .filter { it.isNotBlank() }
                .filter { known -> known.contains(unresolvedId) || unresolvedId.contains(known) }
            return when (candidates.size) {
                0 -> "no candidate"
                1 -> "shadow_match: $unresolvedId -> ${candidates.single()} (${classifyMatch(unresolvedId, candidates.single())})"
                else -> "ambiguous: ${candidates.size} candidates ${candidates.sorted()}"
            }
        }

        /** Display label for [shadowMatchDescription]'s single-candidate case — purely descriptive,
         * doesn't affect which candidate was chosen (that's already decided by the time this runs). */
        private fun classifyMatch(unresolvedId: String, known: String): String = when {
            known.startsWith(unresolvedId) || unresolvedId.startsWith(known) -> "prefix"
            known.endsWith(unresolvedId) || unresolvedId.endsWith(known) -> "suffix"
            else -> "substring"
        }

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