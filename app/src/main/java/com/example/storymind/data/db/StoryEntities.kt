package com.example.storymind.data.db

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

fun WikiEntryEntity.toDomain(): WikiEntry = WikiEntry(
    id = id,
    type = SmBadgeType.valueOf(type),
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
    type = SmBadgeType.valueOf(type),
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

fun GraphEdgeEntity.toDomain(): GraphEdge = GraphEdge(from = fromId, to = toId)

fun GraphEdge.toEntity(): GraphEdgeEntity = GraphEdgeEntity(fromId = from, toId = to)

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