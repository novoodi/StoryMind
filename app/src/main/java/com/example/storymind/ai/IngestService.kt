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

    suspend fun ingest(chapterLabel: String, title: String, paragraphs: List<String>): IngestResult {
        val prompt = IngestSchema.buildIngestPrompt(title, paragraphs)
        val raw = engine.generate(prompt)
        val parsed = IngestParser.parse(raw)

        val wikiEntries = parsed.entities.map { entity ->
            WikiEntry(
                id = entity.id,
                type = entity.type,
                name = entity.name,
                desc = entity.desc,
                chapter = chapterLabel,
            )
        }

        val nodes = parsed.entities.map { entity ->
            GraphNode(id = entity.id, type = entity.type, label = entity.name, x = 0f, y = 0f)
        }

        val knownIds = parsed.entities.mapTo(mutableSetOf()) { it.id }
        val edges = parsed.relations
            .filter { it.from in knownIds && it.to in knownIds }
            .map { GraphEdge(from = it.from, to = it.to) }

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