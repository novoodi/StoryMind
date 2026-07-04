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

        val merged = progress.merge(nextChapter)

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

        val merged = progress.merge(reIngestedChapter1)

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

        val merged = progress.merge(reIngestedChapter1)

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

        val merged = progress.merge(chapter)

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

        val merged = progress.merge(nextChapter)

        assertEquals(2, merged.nodes.size)
        assertEquals(50f, merged.nodes.single { it.id == "yul" }.x)
        val jinseongNode = merged.nodes.single { it.id == "jinseong" }
        assertTrue(jinseongNode.x != 0f || jinseongNode.y != 0f)
        assertEquals(1, merged.edges.size)
        assertTrue(merged.orphanIds.isEmpty())
    }
}