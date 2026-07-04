package com.example.storymind.ui.screens

import com.example.storymind.data.GraphEdge
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [chapterFilterLabels] — the pure derivation behind the Brain screen's per-chapter
 * edge-highlight chips. Kept out of the composable's runtime so the ordering/sentinel rules are
 * verified without an instrumented UI test. */
class BrainScreenTest {

    @Test
    fun `labels are the distinct chapters an edge set references, numeric-sorted`() {
        val edges = listOf(
            GraphEdge("a", "b", chapters = setOf("2화")),
            GraphEdge("b", "c", chapters = setOf("1화", "3화")),
            GraphEdge("a", "c", chapters = setOf("2화")), // duplicate 2화 collapses
        )
        assertEquals(listOf("1화", "2화", "3화"), chapterFilterLabels(edges))
    }

    @Test
    fun `numeric sort keeps 11화 after 2화, not before it as string sort would`() {
        val edges = listOf(
            GraphEdge("a", "b", chapters = setOf("11화")),
            GraphEdge("b", "c", chapters = setOf("2화")),
            GraphEdge("c", "d", chapters = setOf("1화")),
        )
        assertEquals(listOf("1화", "2화", "11화"), chapterFilterLabels(edges))
    }

    @Test
    fun `a legacy sentinel edge surfaces the 출처 미상 chip last`() {
        val edges = listOf(
            GraphEdge("a", "b", chapters = setOf("1화")),
            GraphEdge("b", "c", chapters = setOf("?")),
        )
        assertEquals(listOf("1화", "출처 미상"), chapterFilterLabels(edges))
    }

    @Test
    fun `no sentinel means no 출처 미상 chip`() {
        val edges = listOf(GraphEdge("a", "b", chapters = setOf("1화", "2화")))
        assertEquals(listOf("1화", "2화"), chapterFilterLabels(edges))
    }

    @Test
    fun `an all-sentinel edge set yields only the 출처 미상 chip`() {
        val edges = listOf(GraphEdge("a", "b", chapters = setOf("?")))
        assertEquals(listOf("출처 미상"), chapterFilterLabels(edges))
    }

    @Test
    fun `no edges yields an empty label list`() {
        assertEquals(emptyList<String>(), chapterFilterLabels(emptyList()))
    }
}
