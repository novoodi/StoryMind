package com.example.storymind.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.storymind.ai.ChapterProgress
import com.example.storymind.data.Chapter
import com.example.storymind.data.GraphEdge
import com.example.storymind.data.GraphNode
import com.example.storymind.data.WikiEntry
import com.example.storymind.ui.components.SmBadgeType

/** A chapter's manuscript, as typed by the author. This is the immutable source of truth —
 * ingest results are derived from it and stored separately. */
@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val chapterIndex: Int,
    val label: String,
    val title: String?,
    val body: String,
    val ingested: Boolean,
    /** Which ingest engine produced this chapter's wiki/graph data ("local" on-device Gemma today,
     * "cloud" reserved for a future path), or null for drafts and chapters ingested before
     * provenance tracking existed (schema v1). */
    val ingestEngine: String? = null,
    /** [com.example.storymind.ai.IngestSchema.PROMPT_VERSION] at ingest time, so a later prompt
     * change can be detected and old chapters re-ingested if desired; null under the same
     * conditions as [ingestEngine]. */
    val ingestPromptVersion: Int? = null,
)

@Entity(tableName = "wiki_entries")
data class WikiEntryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val desc: String,
    val chapter: String,
)

@Entity(tableName = "graph_nodes")
data class GraphNodeEntity(
    @PrimaryKey val id: String,
    val type: String,
    val label: String,
    val x: Float,
    val y: Float,
)

@Entity(tableName = "graph_edges", primaryKeys = ["fromId", "toId"])
data class GraphEdgeEntity(
    val fromId: String,
    val toId: String,
    /**
     * [GraphEdge.chapters] serialized as newline-joined chapter labels — the edge's per-chapter
     * provenance (schema v3). PK stays `(fromId, toId)`: `merge()` already dedups edges by their
     * two endpoints and unions the chapter sets in memory, so one row per edge is enough and no
     * per-chapter row/PK change is needed.
     *
     * `defaultValue = "?"` must match [MIGRATION_2_3]'s `ADD COLUMN ... DEFAULT '?'`: SQLite can't
     * add a NOT NULL column to a table with existing rows without a default, and Room's schema
     * identity-hash check ([StoryDatabaseMigrationTest]) compares the column default, so the entity
     * has to declare the same one or `runMigrationsAndValidate` fails. The default only ever backs
     * the migration's backfill of legacy v2 edges (which had no provenance) with the `"?"` sentinel;
     * every runtime insert supplies this column explicitly.
     */
    @ColumnInfo(defaultValue = "?") val chapters: String,
)

@Entity(tableName = "orphan_ids")
data class OrphanIdEntity(
    @PrimaryKey val id: String,
)

fun ChapterEntity.toDomain(): Chapter = Chapter(
    label = label,
    title = title,
    paragraphs = body.split(Regex("\n+")).filter { it.isNotBlank() },
)

/**
 * [SmBadgeType.valueOf] 대신 미지의 값을 [SmBadgeType.Character]로 수렴시키는 관용적 매핑.
 * 파생 테이블의 type 문자열은 백업 복원을 거치면 이 앱이 쓴 적 없는 값일 수 있는데(손으로
 * 고친 백업, 미래 버전 앱이 추가한 enum 값 — Room의 구조 검증은 TEXT 내용까지는 안 본다),
 * valueOf가 던지면 첫 화면 로드( loadProgress )부터 매 실행 크래시 루프가 된다. 파생 데이터는
 * 규칙 1에 따라 언제든 재구축 가능하므로, 틀린 배지 하나가 복구 불능 크래시보다 낫다.
 */
private fun badgeTypeOrDefault(raw: String): SmBadgeType =
    SmBadgeType.entries.firstOrNull { it.name == raw } ?: SmBadgeType.Character

fun WikiEntryEntity.toDomain(): WikiEntry = WikiEntry(
    id = id,
    type = badgeTypeOrDefault(type),
    name = name,
    desc = desc,
    chapter = chapter,
)

fun WikiEntry.toEntity(): WikiEntryEntity = WikiEntryEntity(
    id = id,
    type = type.name,
    name = name,
    desc = desc,
    chapter = chapter,
)

fun GraphNodeEntity.toDomain(): GraphNode = GraphNode(
    id = id,
    type = badgeTypeOrDefault(type),
    label = label,
    x = x,
    y = y,
)

fun GraphNode.toEntity(): GraphNodeEntity = GraphNodeEntity(
    id = id,
    type = type.name,
    label = label,
    x = x,
    y = y,
)

/** `chapters` is newline-joined ([GraphEdge.chapters] holds "N화" labels, which never contain a
 * newline). Blank segments are dropped so a legacy sentinel-only edge deserializes to `{"?"}` and
 * an empty column (should never happen — `merge()` drops chapter-less edges) yields an empty set. */
fun GraphEdgeEntity.toDomain(): GraphEdge =
    GraphEdge(from = fromId, to = toId, chapters = chapters.split("\n").filterNot { it.isBlank() }.toSet())

fun GraphEdge.toEntity(): GraphEdgeEntity =
    GraphEdgeEntity(fromId = from, toId = to, chapters = chapters.joinToString("\n"))

data class ChapterProgressSnapshot(
    val wikiEntries: List<WikiEntryEntity>,
    val nodes: List<GraphNodeEntity>,
    val edges: List<GraphEdgeEntity>,
    val orphans: List<OrphanIdEntity>,
) {
    fun toDomain(): ChapterProgress = ChapterProgress(
        wikiEntries = wikiEntries.map { it.toDomain() },
        nodes = nodes.map { it.toDomain() },
        edges = edges.map { it.toDomain() },
        orphanIds = orphans.mapTo(mutableSetOf()) { it.id },
    )
}

fun ChapterProgress.toEntities(): ChapterProgressSnapshot = ChapterProgressSnapshot(
    wikiEntries = wikiEntries.map { it.toEntity() },
    nodes = nodes.map { it.toEntity() },
    edges = edges.map { it.toEntity() },
    orphans = orphanIds.map { OrphanIdEntity(it) },
)