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
     * entry, that id is rewritten to the established one before nodes/edges are built.
     */
    suspend fun ingest(
        chapterLabel: String,
        title: String,
        paragraphs: List<String>,
        existingWiki: List<WikiEntry> = emptyList(),
    ): IngestResult {
        val prompt = IngestSchema.buildIngestPrompt(title, paragraphs, existingWiki)
        val raw = engine.generate(prompt)
        val parsed = IngestParser.parse(raw)

        val idByName = existingWiki.associateBy({ it.name.trim() }, { it.id })
        val idRemap = parsed.entities
            .filter { it.id !in idByName.values }
            .mapNotNull { entity -> idByName[entity.name.trim()]?.let { entity.id to it } }
            .toMap()
        fun resolvedId(id: String) = idRemap[id] ?: id

        val wikiEntries = parsed.entities.map { entity ->
            WikiEntry(
                id = resolvedId(entity.id),
                type = entity.type,
                name = entity.name,
                desc = entity.desc,
                chapter = chapterLabel,
            )
        }

        val nodes = parsed.entities.map { entity ->
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
}