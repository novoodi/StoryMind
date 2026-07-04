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
 * added as a new dated line onto their wiki entry — a running log of how the entity was
 * described each time it appeared — rather than losing earlier chapters' descriptions, while
 * `chapter` is refreshed to the newest appearance. Node position is kept for existing ids; only
 * ids not seen before are laid out. The orphan set is recomputed over the full accumulated edge
 * set, not just this chapter's.
 *
 * The per-chapter line is **replaced, not appended**, when a line for the same chapter label
 * already exists: re-saving an already-ingested chapter (a completely ordinary flow — save, keep
 * writing the same chapter, save again) re-runs its ingest, and appending would stack a duplicate
 * "N화: ..." line onto every entity of that chapter each time. Replacement is the strongest
 * correction an incremental merge can make — stale *edges/nodes* contributed by the chapter's
 * previous version can't be subtracted here, because the DB keeps only the latest accumulated
 * snapshot with no per-chapter provenance to know what to remove; those are exactly what the
 * full rebuild ([com.example.storymind.work.ReplayWorker]) exists to clean up (rule 1).
 *
 * [incoming.desc][WikiEntry.desc] is flattened to a single line first: the accumulated format is
 * strictly one line per chapter ("N화: ..."), and both this function's own replacement filter and
 * [com.example.storymind.work.LintWorker]'s per-chapter history split identify a chapter's line by
 * its `"N화: "` prefix — a model-emitted newline inside one desc would otherwise smuggle
 * prefix-less continuation lines into the history, which the lint pass then mistakes for earlier
 * chapters' facts (self-confirmation) and a later re-ingest can never replace.
 */
fun ChapterProgress.merge(result: IngestResult): ChapterProgress {
    val wikiById = wikiEntries.associateBy { it.id }.toMutableMap()
    result.wikiEntries.forEach { incoming ->
        val existing = wikiById[incoming.id]
        val chapterLine = "${incoming.chapter}: ${incoming.desc.flattenToSingleLine()}"
        val accumulatedDesc = if (existing == null) {
            chapterLine
        } else {
            val earlierChapterLines = existing.desc
                .split("\n")
                .filterNot { it.startsWith("${incoming.chapter}: ") }
            (earlierChapterLines + chapterLine).joinToString("\n")
        }
        wikiById[incoming.id] = incoming.copy(desc = accumulatedDesc)
    }

    val existingNodeIds = nodes.mapTo(mutableSetOf()) { it.id }
    // existingCount를 넘겨 새 노드가 전역 누적 인덱스로 배치되게 한다 — 화별 상대 인덱스로
    // 배치하면 화마다 같은 자리들이 재사용되어 노드가 포개진다(NodeLayout KDoc 참고).
    val newNodes = layoutNodes(result.nodes.filter { it.id !in existingNodeIds }, existingCount = nodes.size)
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

/** See [merge]'s KDoc: the accumulated desc format is one line per chapter, so a desc that
 * arrives with internal newlines must not introduce new lines of its own. */
private fun String.flattenToSingleLine(): String = replace(Regex("\\s*\\n+\\s*"), " ").trim()