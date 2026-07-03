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

/** An edge connecting two node ids in the Second Brain graph. */
data class GraphEdge(val from: String, val to: String)

data class WikiEntry(
    val id: String,
    val type: SmBadgeType,
    val name: String,
    val desc: String,
    val chapter: String,
)
