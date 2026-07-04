package com.example.storymind.ai

import com.example.storymind.data.GraphNode
import com.example.storymind.ui.components.SmBadgeType
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeLayoutTest {

    private fun node(id: String) = GraphNode(id, SmBadgeType.Character, id, 0f, 0f)

    @Test
    fun `nodes from consecutive chapters do not stack on the same spot`() {
        // Regression (2026-07 코드 감사): the old per-cohort circle layout put every chapter's
        // single new node at dead center (50,50) and re-used the same angles for equal-sized
        // cohorts, stacking chapter 2's nodes exactly on top of chapter 1's.
        val first = layoutNodes(listOf(node("a")), existingCount = 0).single()
        val second = layoutNodes(listOf(node("b")), existingCount = 1).single()
        val third = layoutNodes(listOf(node("c")), existingCount = 2).single()

        val positions = listOf(first, second, third).map { it.x to it.y }
        assertEquals(3, positions.toSet().size)
        positions.zipWithNext { p, q ->
            assertTrue(
                "nodes at $p and $q are too close",
                abs(p.first - q.first) > 1f || abs(p.second - q.second) > 1f,
            )
        }
    }

    @Test
    fun `coordinates depend only on the global accumulation index`() {
        // An incremental merge (one node per chapter) and a full rebuild (all at once) must give
        // each node the same position — that determinism is what makes a rebuild reproduce the
        // exact same graph the incremental path built (rule 2).
        val allAtOnce = layoutNodes(listOf(node("a"), node("b"), node("c")), existingCount = 0)
        val oneByOne = listOf(
            layoutNodes(listOf(node("a")), existingCount = 0).single(),
            layoutNodes(listOf(node("b")), existingCount = 1).single(),
            layoutNodes(listOf(node("c")), existingCount = 2).single(),
        )

        allAtOnce.zip(oneByOne).forEach { (batch, single) ->
            assertEquals(batch.x, single.x, 0f)
            assertEquals(batch.y, single.y, 0f)
        }
    }

    @Test
    fun `coordinates stay inside the canvas bounds`() {
        val nodes = layoutNodes((0 until 60).map { node("n$it") }, existingCount = 0)
        nodes.forEach { n ->
            assertTrue("x out of bounds: ${n.x}", n.x in 5f..95f)
            assertTrue("y out of bounds: ${n.y}", n.y in 5f..95f)
        }
    }
}
