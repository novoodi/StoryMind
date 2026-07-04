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
 * Edges carry the same per-chapter provenance ([GraphEdge.chapters]) and fold the same way: this
 * chapter's label is stripped from every existing edge and re-added only to edges the new result
 * still asserts, so an edge whose chapter set empties out is dropped — even when *both* its
 * endpoints are still alive because other chapters mention them (the case a bare `(from, to)` edge
 * could never retract without a full rebuild). Legacy edges migrated from schema v2, which had no
 * per-edge provenance, carry the sentinel `"?"` instead of a real chapter label; since no chapter
 * label ever equals `"?"`, the strip never removes it, so those edges persist exactly as they did
 * before v3 until a full rebuild ([com.example.storymind.work.ReplayWorker]) replaces them with
 * real provenance. A surviving entry dropped from its most-recent chapter keeps its old `chapter`
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
    // 엣지도 위키 desc 줄과 같은 화 단위 provenance(GraphEdge.chapters)로 접는다: 이 화의 태그를
    // 모든 기존 엣지에서 먼저 걷어낸 뒤(chapters - chapterLabel), 새 결과가 여전히 가진 엣지에만
    // 이 화 태그를 다시 붙인다. 그 결과 태그 집합이 0개가 된 엣지 — 이 화만 만들었고 이번 재인제스트가
    // 더는 언급하지 않는 엣지 — 는 양 끝 노드가 모두 살아있어도 사라진다(예전 KDoc의 "닿지 못하던"
    // stale 케이스). 센티넬 "?"(v2에서 마이그레이션된 레거시 엣지)는 어떤 화 라벨과도 같지 않아 절대
    // 안 걷히므로 레거시 엣지는 오늘과 동일하게 유지되고, 전체 재구축이 실제 provenance로 대체한다.
    val edgeChapters = LinkedHashMap<Pair<String, String>, MutableSet<String>>()
    edges.forEach { edge ->
        val kept = edge.chapters - chapterLabel
        if (kept.isNotEmpty()) edgeChapters.getOrPut(edge.from to edge.to) { linkedSetOf() }.addAll(kept)
    }
    result.edges.forEach { edge ->
        edgeChapters.getOrPut(edge.from to edge.to) { linkedSetOf() }.add(chapterLabel)
    }
    // 제거된 엔티티를 가리키던 엣지는 여기서 함께 떨어진다(양 끝이 knownIds에 있어야 유지).
    val mergedEdges = edgeChapters
        .map { (endpoints, chapters) -> GraphEdge(endpoints.first, endpoints.second, chapters) }
        .filter { it.from in knownIds && it.to in knownIds }

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