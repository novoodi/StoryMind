package com.example.storymind.ai

import com.example.storymind.data.GraphNode
import kotlin.math.cos
import kotlin.math.sin

private const val CENTER = 50f
private const val RADIUS = 32f
private const val MIN_COORD = 5f
private const val MAX_COORD = 95f

/**
 * Assigns 0-100% canvas coordinates to freshly-ingested nodes, which all arrive as (0,0)
 * placeholders. Spreads them evenly around a circle; real positioning after that is up to
 * the user's drag interactions on [BrainScreen].
 */
fun layoutNodes(nodes: List<GraphNode>): List<GraphNode> {
    if (nodes.size <= 1) {
        return nodes.map { it.copy(x = CENTER, y = CENTER) }
    }

    return nodes.mapIndexed { index, node ->
        val angle = -Math.PI / 2 + 2 * Math.PI * index / nodes.size
        val x = (CENTER + RADIUS * cos(angle)).toFloat().coerceIn(MIN_COORD, MAX_COORD)
        val y = (CENTER + RADIUS * sin(angle)).toFloat().coerceIn(MIN_COORD, MAX_COORD)
        node.copy(x = x, y = y)
    }
}