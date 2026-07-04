package com.example.storymind.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapter(chapter: ChapterEntity)

    @Query("SELECT * FROM chapters ORDER BY chapterIndex ASC")
    fun observeChapters(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters ORDER BY chapterIndex ASC")
    suspend fun loadChapters(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE chapterIndex = :chapterIndex")
    suspend fun loadChapter(chapterIndex: Int): ChapterEntity?

    /**
     * Flips only the ingest flag/provenance columns instead of upserting a whole row, so the
     * manuscript body (the immutable source of truth) is never rewritten by the ingest path.
     */
    @Query(
        "UPDATE chapters SET ingested = 1, ingestEngine = :engine, ingestPromptVersion = :promptVersion " +
            "WHERE chapterIndex = :chapterIndex"
    )
    suspend fun markIngested(chapterIndex: Int, engine: String, promptVersion: Int)

    /**
     * [markIngested]'s inverse, across every chapter at once — the flag half of
     * [com.example.storymind.data.StoryRepository.resetDerivedData]. Same column-targeted UPDATE
     * rationale: bodies are never rewritten by anything on the ingest/replay path.
     */
    @Query("UPDATE chapters SET ingested = 0, ingestEngine = NULL, ingestPromptVersion = NULL")
    suspend fun resetIngestProvenance()

    /**
     * 사용자 드래그가 정한 노드 위치의 영속화. 좌표 컬럼만 겨냥한 UPDATE인 이유는
     * [markIngested]와 같다 — 이 경로가 노드의 타입/라벨을 절대 다시 쓰지 않게 한다.
     * `merge()`가 기존 노드를 그대로 보존하므로(규칙 2 KDoc), 여기 저장된 위치는 이후
     * 인제스트의 replaceProgress를 그대로 통과해 살아남는다.
     */
    @Query("UPDATE graph_nodes SET x = :x, y = :y WHERE id = :id")
    suspend fun updateNodePosition(id: String, x: Float, y: Float)

    @Query("DELETE FROM wiki_entries")
    suspend fun clearWikiEntries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWikiEntries(entries: List<WikiEntryEntity>)

    @Query("DELETE FROM graph_nodes")
    suspend fun clearNodes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<GraphNodeEntity>)

    @Query("DELETE FROM graph_edges")
    suspend fun clearEdges()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdges(edges: List<GraphEdgeEntity>)

    @Query("DELETE FROM orphan_ids")
    suspend fun clearOrphans()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrphans(orphans: List<OrphanIdEntity>)

    @Query("SELECT * FROM wiki_entries")
    suspend fun loadWikiEntries(): List<WikiEntryEntity>

    @Query("SELECT * FROM graph_nodes")
    suspend fun loadNodes(): List<GraphNodeEntity>

    @Query("SELECT * FROM graph_edges")
    suspend fun loadEdges(): List<GraphEdgeEntity>

    @Query("SELECT * FROM orphan_ids")
    suspend fun loadOrphans(): List<OrphanIdEntity>

    /**
     * Replaces the entire accumulated wiki/graph snapshot in one transaction. `merge()` already
     * recomputes all four tables from scratch on every chapter, so a full replace here mirrors
     * that and avoids having to hand-write insert/update/delete diffing for each table.
     */
    @Transaction
    suspend fun replaceProgress(snapshot: ChapterProgressSnapshot) {
        clearWikiEntries()
        insertWikiEntries(snapshot.wikiEntries)
        clearNodes()
        insertNodes(snapshot.nodes)
        clearEdges()
        insertEdges(snapshot.edges)
        clearOrphans()
        insertOrphans(snapshot.orphans)
    }

    suspend fun loadProgress(): ChapterProgressSnapshot = ChapterProgressSnapshot(
        wikiEntries = loadWikiEntries(),
        nodes = loadNodes(),
        edges = loadEdges(),
        orphans = loadOrphans(),
    )
}