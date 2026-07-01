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

/** Sample data mirroring ui_kits/novel_app/index.html exactly. */
object MockData {
    val nodes = listOf(
        GraphNode("jiwoo", SmBadgeType.Character, "김지우", 25f, 20f),
        GraphNode("minjoon", SmBadgeType.Character, "박민준", 68f, 37f),
        GraphNode("home", SmBadgeType.Place, "집", 14f, 63f),
        GraphNode("cafe", SmBadgeType.Place, "카페 달빛", 52f, 74f),
        GraphNode("letter", SmBadgeType.Item, "손편지", 80f, 18f),
        GraphNode("meeting", SmBadgeType.Event, "첫 만남", 44f, 49f),
        GraphNode("stranger", SmBadgeType.Orphan, "수상한 남자", 82f, 81f),
    )

    val edges = listOf(
        GraphEdge("jiwoo", "minjoon"),
        GraphEdge("jiwoo", "home"),
        GraphEdge("jiwoo", "cafe"),
        GraphEdge("minjoon", "cafe"),
        GraphEdge("jiwoo", "letter"),
        GraphEdge("minjoon", "letter"),
        GraphEdge("meeting", "jiwoo"),
        GraphEdge("meeting", "minjoon"),
        GraphEdge("meeting", "cafe"),
    )

    val wiki = listOf(
        WikiEntry("jiwoo", SmBadgeType.Character, "김지우", "26세 · 작가 지망생 · 빗소리를 좋아함", "1장"),
        WikiEntry("minjoon", SmBadgeType.Character, "박민준", "28세 · 카페 달빛 단골손님", "1장"),
        WikiEntry("cafe", SmBadgeType.Place, "카페 달빛", "두 주인공이 처음 만난 장소", "1장"),
        WikiEntry("home", SmBadgeType.Place, "집", "김지우의 아파트 · 강북구", "2장"),
        WikiEntry("letter", SmBadgeType.Item, "손편지", "박민준이 김지우에게 남긴 편지", "3장"),
        WikiEntry("meeting", SmBadgeType.Event, "첫 만남", "비 오는 날 카페에서의 우연한 만남", "1장"),
    )

    fun wikiFor(nodeId: String): WikiEntry? = wiki.find { it.id == nodeId }

    val chapterTitle = "빗소리"
    val chapterParagraphs = listOf(
        "빗소리가 창문을 두드렸다. 김지우는 커피잔을 두 손으로 감싸 쥐며 창밖을 바라보았다.",
        "그가 이 카페에 처음 들어선 건 아마 삼 년 전이었을 것이다. 정확하게 기억나지 않지만, 그날도 비가 왔다는 것만큼은 확실했다.",
        "\"저기요.\"",
        "낯선 목소리에 고개를 돌렸다. 박민준이 젖은 우산을 접으며 맞은편 자리에 앉고 있었다.",
    )
}