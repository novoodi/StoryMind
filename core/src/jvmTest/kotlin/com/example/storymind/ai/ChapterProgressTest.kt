package com.example.storymind.ai

import com.example.storymind.data.GraphEdge
import com.example.storymind.data.GraphNode
import com.example.storymind.data.WikiEntry
import com.example.storymind.ui.components.SmBadgeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterProgressTest {

    @Test
    fun `merge keeps an existing node's position and appends this chapter's description`() {
        val progress = ChapterProgress(
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "1장: 1장 설명", "1장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 12f, 34f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )
        val nextChapter = IngestResult(
            chapterSummary = "2장 요약",
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "2장 설명", "2장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 0f, 0f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )

        val merged = progress.merge(nextChapter, "2장")

        assertEquals(1, merged.nodes.size)
        assertEquals(12f, merged.nodes[0].x)
        assertEquals(34f, merged.nodes[0].y)
        assertEquals("1장: 1장 설명\n2장: 2장 설명", merged.wikiEntries.single { it.id == "yul" }.desc)
        assertEquals("2장", merged.wikiEntries.single { it.id == "yul" }.chapter)
    }

    @Test
    fun `re-merging the same chapter replaces its description line instead of duplicating it`() {
        // Save -> ingest -> keep writing the same chapter -> save again re-runs that chapter's
        // ingest; the accumulated desc must end up with exactly one line for that chapter.
        val progress = ChapterProgress(
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "1장: 첫 저장 설명\n2장: 2장 설명", "2장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 12f, 34f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )
        val reIngestedChapter1 = IngestResult(
            chapterSummary = "1장 요약 (수정판)",
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "수정된 설명", "1장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 0f, 0f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )

        val merged = progress.merge(reIngestedChapter1, "1장")

        assertEquals("2장: 2장 설명\n1장: 수정된 설명", merged.wikiEntries.single { it.id == "yul" }.desc)
    }

    @Test
    fun `chapter label prefix match is exact - 1장 does not swallow 11장's line`() {
        val progress = ChapterProgress(
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "1장: 일장 설명\n11장: 십일장 설명", "11장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 12f, 34f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )
        val reIngestedChapter1 = IngestResult(
            chapterSummary = "요약",
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "새 일장 설명", "1장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 0f, 0f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )

        val merged = progress.merge(reIngestedChapter1, "1장")

        assertEquals("11장: 십일장 설명\n1장: 새 일장 설명", merged.wikiEntries.single { it.id == "yul" }.desc)
    }

    @Test
    fun `a model-emitted newline inside desc is flattened to keep one line per chapter`() {
        val progress = ChapterProgress()
        val chapter = IngestResult(
            chapterSummary = "요약",
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "첫 줄\n  둘째 줄", "1장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 0f, 0f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )

        val merged = progress.merge(chapter, "1장")

        assertEquals("1장: 첫 줄 둘째 줄", merged.wikiEntries.single { it.id == "yul" }.desc)
    }

    @Test
    fun `merge lays out only the newly seen nodes and recomputes orphans over the full edge set`() {
        val progress = ChapterProgress(
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "설명", "1장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 50f, 50f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )
        val nextChapter = IngestResult(
            chapterSummary = "요약",
            wikiEntries = listOf(WikiEntry("jinseong", SmBadgeType.Character, "진성", "설명", "2장")),
            nodes = listOf(GraphNode("jinseong", SmBadgeType.Character, "진성", 0f, 0f)),
            edges = listOf(GraphEdge("yul", "jinseong")),
            orphanIds = emptySet(),
        )

        val merged = progress.merge(nextChapter, "2장")

        assertEquals(2, merged.nodes.size)
        assertEquals(50f, merged.nodes.single { it.id == "yul" }.x)
        val jinseongNode = merged.nodes.single { it.id == "jinseong" }
        assertTrue(jinseongNode.x != 0f || jinseongNode.y != 0f)
        assertEquals(1, merged.edges.size)
        assertTrue(merged.orphanIds.isEmpty())
    }

    @Test
    fun `re-ingesting a chapter drops an entity it no longer mentions and its dangling edge`() {
        // Chapter 1 originally introduced both 율 and 그림자 (연결됨). A re-ingest of chapter 1
        // whose new text only mentions 율 must drop 그림자 entirely — it was 1장's sole entity —
        // along with its node and the now-dangling 율→그림자 edge.
        val progress = ChapterProgress(
            wikiEntries = listOf(
                WikiEntry("yul", SmBadgeType.Character, "율", "1장: 주인공", "1장"),
                WikiEntry("shadow", SmBadgeType.Event, "그림자", "1장: 율의 트라우마", "1장"),
            ),
            nodes = listOf(
                GraphNode("yul", SmBadgeType.Character, "율", 30f, 30f),
                GraphNode("shadow", SmBadgeType.Event, "그림자", 70f, 70f),
            ),
            edges = listOf(GraphEdge("yul", "shadow")),
            orphanIds = emptySet(),
        )
        val reIngest = IngestResult(
            chapterSummary = "1장 요약 (수정판)",
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "수정된 주인공 설명", "1장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 0f, 0f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )

        val merged = progress.merge(reIngest, "1장")

        assertEquals(setOf("yul"), merged.wikiEntries.mapTo(mutableSetOf()) { it.id })
        assertEquals(setOf("yul"), merged.nodes.mapTo(mutableSetOf()) { it.id })
        assertTrue("dangling edge to the removed entity must be pruned", merged.edges.isEmpty())
        assertEquals("1장: 수정된 주인공 설명", merged.wikiEntries.single().desc)
        // 위치 보존: 재인제스트에서도 살아남은 노드의 좌표는 유지된다.
        assertEquals(30f, merged.nodes.single().x)
    }

    @Test
    fun `re-ingesting a chapter keeps an entity that other chapters still mention`() {
        // 율 appears in both 1장 and 2장. Re-ingesting 2장 without 율 must keep 율 (still in 1장),
        // dropping only its 2장 line — not the whole entity.
        val progress = ChapterProgress(
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "1장: 주인공\n2장: 재등장", "2장")),
            nodes = listOf(GraphNode("yul", SmBadgeType.Character, "율", 40f, 40f)),
            edges = emptyList(),
            orphanIds = setOf("yul"),
        )
        val reIngestChapter2 = IngestResult(
            chapterSummary = "2장 요약 (율 빠짐)",
            wikiEntries = listOf(WikiEntry("mom", SmBadgeType.Character, "엄마", "새 인물", "2장")),
            nodes = listOf(GraphNode("mom", SmBadgeType.Character, "엄마", 0f, 0f)),
            edges = emptyList(),
            orphanIds = setOf("mom"),
        )

        val merged = progress.merge(reIngestChapter2, "2장")

        assertEquals(setOf("yul", "mom"), merged.wikiEntries.mapTo(mutableSetOf()) { it.id })
        assertEquals("1장: 주인공", merged.wikiEntries.single { it.id == "yul" }.desc)
    }
}