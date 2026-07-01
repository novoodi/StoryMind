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
    fun `merge keeps an existing node's position and refreshes its wiki entry`() {
        val progress = ChapterProgress(
            wikiEntries = listOf(WikiEntry("yul", SmBadgeType.Character, "율", "1장 설명", "1장")),
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
        assertEquals("2장 설명", merged.wikiEntries.single { it.id == "yul" }.desc)
        assertEquals("2장", merged.wikiEntries.single { it.id == "yul" }.chapter)
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