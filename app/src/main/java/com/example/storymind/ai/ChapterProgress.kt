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
 * exist (same id, thanks to [IngestService]'s name-based remap) get their wiki entry refreshed
 * to the newest chapter's description/last-appearance chapter, keeping their existing node
 * position. Only ids not seen before are laid out. The orphan set is recomputed over the full
 * accumulated edge set, not just this chapter's.
 */
fun ChapterProgress.merge(result: IngestResult): ChapterProgress {
    val wikiById = wikiEntries.associateBy { it.id }.toMutableMap()
    result.wikiEntries.forEach { wikiById[it.id] = it }

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