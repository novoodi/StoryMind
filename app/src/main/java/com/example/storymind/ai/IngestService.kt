package com.example.storymind.ai

import com.example.storymind.data.GraphEdge
import com.example.storymind.data.GraphNode
import com.example.storymind.data.WikiEntry

/**
 * Raw text generation, abstracted so [IngestService] can be constructed with a fake in tests
 * instead of the real [OnDeviceEngine]. `OnDeviceEngine::generate` satisfies this signature
 * directly via SAM conversion.
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
class IngestService(private val engine: OnDeviceTextEngine) {

    /**
     * [existingWiki] carries entities already established in earlier chapters. It's forwarded
     * to the model as context so it reuses their ids/names, and also backs a name-based safety
     * net here: if the model still mints a fresh id for something whose name matches an existing
     * entry, that id is rewritten to the established one before nodes/edges are built. The match
     * falls back from exact name equality to a suffix check (either name ending with the other)
     * so a dropped-surname nickname the model reaches for later — "이지민" becoming "지민" — still
     * resolves to the same entity instead of minting a duplicate.
     */
    suspend fun ingest(
        chapterLabel: String,
        title: String,
        paragraphs: List<String>,
        existingWiki: List<WikiEntry> = emptyList(),
    ): IngestResult {
        val prompt = IngestSchema.buildIngestPrompt(title, paragraphs, existingWiki)
        val parsed = generateParsed(prompt)

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
     * Only after [MAX_ATTEMPTS] straight parse failures do we accept [IngestParser.parse]'s empty
     * fallback.
     */
    private suspend fun generateParsed(prompt: String): ParsedIngest {
        lateinit var raw: String
        repeat(MAX_ATTEMPTS) { attempt ->
            raw = engine.generate(prompt)
            IngestParser.parseOrNull(raw)?.let { return it }
            val attemptsLeft = MAX_ATTEMPTS - attempt - 1
            ingestLogger.w(TAG, "Ingest JSON parse failed (attempt ${attempt + 1}/$MAX_ATTEMPTS), $attemptsLeft retries left")
        }
        return IngestParser.parse(raw)
    }

    companion object {
        private const val TAG = "IngestService"
        private const val MAX_ATTEMPTS = 3

        /** Shortest name either side of a suffix match may be, to keep single-character names
         * (rare, but not impossible) from matching almost anything by coincidence. */
        private const val MIN_NAME_SUFFIX_MATCH_LENGTH = 2
    }
}