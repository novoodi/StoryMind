package com.example.storymind.ai

import com.example.storymind.data.GraphEdge
import com.example.storymind.data.GraphNode
import com.example.storymind.data.WikiEntry

/** Accumulated wiki/graph state across every chapter ingested so far. */
data class ChapterProgress(
    val wikiEntries: List<WikiEntry> = emptyList(),
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
    val orphanIds: Set<String> = emptySet(),
)

/**
 * Folds one chapter's [IngestResult] into the running [ChapterProgress]: entities that already
 * exist (same id, thanks to [IngestService]'s name-based remap) get this chapter's description
 * appended as a new dated line onto their wiki entry — a running log of how the entity was
 * described each time it appeared — rather than losing earlier chapters' descriptions, while
 * `chapter` is refreshed to the newest appearance. Node position is kept for existing ids; only
 * ids not seen before are laid out. The orphan set is recomputed over the full accumulated edge
 * set, not just this chapter's.
 */
fun ChapterProgress.merge(result: IngestResult): ChapterProgress {
    val wikiById = wikiEntries.associateBy { it.id }.toMutableMap()
    result.wikiEntries.forEach { incoming ->
        val existing = wikiById[incoming.id]
        val accumulatedDesc = if (existing == null) {
            "${incoming.chapter}: ${incoming.desc}"
        } else {
            "${existing.desc}\n${incoming.chapter}: ${incoming.desc}"
        }
        wikiById[incoming.id] = incoming.copy(desc = accumulatedDesc)
    }

    val existingNodeIds = nodes.mapTo(mutableSetOf()) { it.id }
    val newNodes = layoutNodes(result.nodes.filter { it.id !in existingNodeIds })
    val mergedNodes = nodes + newNodes

    val mergedEdges = (edges + result.edges).distinct()

    val knownIds = mergedNodes.mapTo(mutableSetOf()) { it.id }
    val connectedIds = mergedEdges.flatMapTo(mutableSetOf()) { listOf(it.from, it.to) }
    val orphanIds = knownIds - connectedIds

    return ChapterProgress(
        wikiEntries = wikiById.values.toList(),
        nodes = mergedNodes,
        edges = mergedEdges,
        orphanIds = orphanIds,
    )
}