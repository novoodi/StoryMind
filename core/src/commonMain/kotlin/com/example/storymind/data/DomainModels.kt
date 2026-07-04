package com.example.storymind.data

import com.example.storymind.ui.components.SmBadgeType

/** A Second Brain graph node. Position is a 0–100 percentage of the canvas. */
data class GraphNode(
    val id: String,
    val type: SmBadgeType,
    val label: String,
    val x: Float,
    val y: Float,
)

/**
 * An edge connecting two node ids in the Second Brain graph.
 *
 * [chapters] is the set of chapter labels ("N화") whose ingest produced this relation — the
 * edge's per-chapter provenance, mirroring how a [WikiEntry]'s accumulated `desc` carries one
 * `"N화: ..."` line per chapter. It exists so a re-ingest can retract exactly the relations a
 * chapter no longer supports (see [com.example.storymind.ai.merge]), including the case a bare
 * `(from, to)` edge could never reach: both endpoints still alive, but the only chapter that ever
 * asserted the relation has dropped it.
 *
 * Empty on a freshly-ingested edge in an [com.example.storymind.ai.IngestResult] — the incoming
 * edge is "raw" and [com.example.storymind.ai.merge] stamps the chapter label, exactly as an
 * incoming [WikiEntry.desc] arrives without its `"N화: "` prefix and merge adds it. The set only
 * accumulates in the merged [com.example.storymind.ai.ChapterProgress]. A legacy edge migrated
 * from schema v2 (which had no per-edge provenance) carries the sentinel `"?"` — see that
 * migration and merge's KDoc for why the sentinel is never stripped.
 */
data class GraphEdge(val from: String, val to: String, val chapters: Set<String> = emptySet())

data class WikiEntry(
    val id: String,
    val type: SmBadgeType,
    val name: String,
    val desc: String,
    val chapter: String,
)
