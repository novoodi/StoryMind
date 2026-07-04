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
 * Folds one chapter's [IngestResult] into the running [ChapterProgress]. [chapterLabel] is the
 * chapter being merged (e.g. "3화") — passed explicitly rather than read off the result's entries
 * so a re-ingest that produces *no* entities still knows which chapter's contribution to retract.
 *
 * The accumulated wiki desc is one line per chapter ("N화: ..."), which doubles as this fold's
 * per-chapter provenance for entities. On every merge, [chapterLabel]'s line is first stripped
 * from **every** existing entry, then re-added only for entries the new result still mentions:
 * - A first-time ingest of a chapter strips nothing (no entry has its line yet) and just appends —
 *   the ordinary forward case, unchanged.
 * - A **re-ingest** (save an already-ingested chapter, keep writing, save again) replaces that
 *   chapter's line instead of stacking a duplicate onto every entity, and — the reason strip runs
 *   over *all* entries, not just the incoming ones — drops an entity the chapter no longer mentions
 *   the moment its last remaining line is gone (that chapter was its sole source). Its node is
 *   dropped with it, and any edge left dangling to a removed node is filtered out below.
 *
 * The one stale case this can't reach: an edge whose *both* endpoints still exist but which only
 * this chapter's previous ingest produced — there's no per-edge chapter provenance to subtract it,
 * so a full rebuild ([com.example.storymind.work.ReplayWorker]) remains the tool for that narrow
 * case (rule 1). A surviving entry dropped from its most-recent chapter keeps its old `chapter`
 * ("last appearance") field rather than recomputing it — display-only, and rebuild corrects it.
 *
 * [incoming.desc][WikiEntry.desc] is flattened to a single line first: a model-emitted newline
 * inside one desc would otherwise smuggle prefix-less continuation lines into the accumulation,
 * which both this strip/replace filter and [com.example.storymind.work.LintWorker]'s per-chapter
 * history split (keyed on the `"N화: "` prefix) would then mis-attribute.
 *
 * Node position is kept for surviving ids; only ids not seen before are laid out (by global
 * accumulation index, so incremental merge and rebuild agree — see [layoutNodes]). The orphan set
 * is recomputed over the full accumulated edge set, not just this chapter's.
 */
fun ChapterProgress.merge(result: IngestResult, chapterLabel: String): ChapterProgress {
    val chapterPrefix = "$chapterLabel: "
    val incomingById = result.wikiEntries.associateBy { it.id }
    val existingById = wikiEntries.associateBy { it.id }

    val mergedWiki = (existingById.keys + incomingById.keys).mapNotNull { id ->
        val existing = existingById[id]
        val incoming = incomingById[id]
        // 이 화의 줄을 먼저 걷어낸다: 재인제스트면 옛 줄을 지우고(아래에서 새로 붙임), 이 화가
        // 더는 언급하지 않는 엔티티는 이 화가 유일 출처였을 때 줄이 0개가 되어 사라진다.
        val keptLines = existing?.desc?.split("\n").orEmpty().filterNot { it.startsWith(chapterPrefix) }
        val lines = if (incoming != null) {
            keptLines + "$chapterPrefix${incoming.desc.flattenToSingleLine()}"
        } else {
            keptLines
        }
        if (lines.isEmpty()) return@mapNotNull null
        // incoming이 있으면 그것을 기준으로(새 타입/이름/이번 화 등장), 없으면 기존 엔티티를
        // 유지한다. `chapter`(마지막 등장)는 incoming이 있을 때만 갱신한다.
        val template = incoming ?: existing!!
        template.copy(
            desc = lines.joinToString("\n"),
            chapter = incoming?.chapter ?: existing!!.chapter,
        )
    }

    val survivingIds = mergedWiki.mapTo(mutableSetOf()) { it.id }
    val existingNodeIds = nodes.mapTo(mutableSetOf()) { it.id }
    val survivingExistingNodes = nodes.filter { it.id in survivingIds }
    // 새로 등장한 id만 배치. existingCount에 살아남은 기존 노드 수를 넘겨 전역 누적 인덱스로
    // 배치되게 한다(포개짐 방지, NodeLayout KDoc). 재인제스트로 노드가 줄어도 리빌드는 각 화를
    // 한 번씩 전진 병합하므로(제거 없음) survivingExistingNodes.size == nodes.size라 결정성 유지.
    val newNodes = layoutNodes(
        result.nodes.filter { it.id in survivingIds && it.id !in existingNodeIds },
        existingCount = survivingExistingNodes.size,
    )
    val mergedNodes = survivingExistingNodes + newNodes

    val knownIds = mergedNodes.mapTo(mutableSetOf()) { it.id }
    // 제거된 엔티티를 가리키던 엣지는 함께 떨어뜨린다. 양 끝이 모두 살아있지만 이 화만
    // 만들었던 엣지는 provenance가 없어 남는다(위 KDoc의 좁은 예외 — 재구축이 정리).
    val mergedEdges = (edges + result.edges).distinct().filter { it.from in knownIds && it.to in knownIds }

    val connectedIds = mergedEdges.flatMapTo(mutableSetOf()) { listOf(it.from, it.to) }
    val orphanIds = knownIds - connectedIds

    return ChapterProgress(
        wikiEntries = mergedWiki,
        nodes = mergedNodes,
        edges = mergedEdges,
        orphanIds = orphanIds,
    )
}

/** See [merge]'s KDoc: the accumulated desc format is one line per chapter, so a desc that
 * arrives with internal newlines must not introduce new lines of its own. */
private fun String.flattenToSingleLine(): String = replace(Regex("\\s*\\n+\\s*"), " ").trim()