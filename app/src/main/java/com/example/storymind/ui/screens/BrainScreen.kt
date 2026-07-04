package com.example.storymind.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.data.GraphEdge
import com.example.storymind.data.GraphNode
import com.example.storymind.data.WikiEntry
import com.example.storymind.ui.components.SmBadge
import com.example.storymind.ui.components.SmBadgeType
import com.example.storymind.ui.components.SmIconButton
import com.example.storymind.ui.components.SmToolbar
import com.example.storymind.ui.icons.SmIcons
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors

private data class NodeVisual(val fill: Color, val bg: Color)

private fun nodeVisual(type: SmBadgeType) = when (type) {
    SmBadgeType.Character -> NodeVisual(SmColors.nodeCharacter, SmColors.nodeCharacterBg)
    SmBadgeType.Place -> NodeVisual(SmColors.nodePlace, SmColors.nodePlaceBg)
    SmBadgeType.Item -> NodeVisual(SmColors.nodeItem, SmColors.nodeItemBg)
    SmBadgeType.Event -> NodeVisual(SmColors.nodeEvent, SmColors.nodeEventBg)
    else -> NodeVisual(SmColors.nodeOrphan, SmColors.nodeOrphanBg)
}

/** 필터 칩 라벨 → 노드 타입. null은 "전체"(필터 없음). 칩 목록과 이 매핑이 함께 움직여야
 * 하므로 칩 라벨 리스트도 이 파일의 FILTER_LABELS를 쓴다. */
private fun filterToType(label: String): SmBadgeType? = when (label) {
    "인물" -> SmBadgeType.Character
    "장소" -> SmBadgeType.Place
    "소품" -> SmBadgeType.Item
    "사건" -> SmBadgeType.Event
    else -> null
}

private val FILTER_LABELS = listOf("전체", "인물", "장소", "소품", "사건")

/** 모든 필터 칩 행의 "필터 없음" 리셋 라벨 — 타입 칩과 화 칩이 같은 의미로 공유한다. */
private const val ALL_LABEL = "전체"

/** 레거시(v2→v3 마이그레이션) 엣지의 provenance는 센티넬 "?"다([GraphEdge.chapters]). 화 칩
 * 줄에서는 사람이 읽는 [SENTINEL_CHAPTER_LABEL]로 보여주고, 강조 시 다시 이 토큰으로 매칭한다. */
private const val SENTINEL_CHAPTER = "?"
private const val SENTINEL_CHAPTER_LABEL = "출처 미상"

/**
 * 화 필터 칩 라벨을 엣지의 provenance([GraphEdge.chapters])에서 파생한다. 화 목록을 따로 받지
 * 않고 엣지에서 뽑는 이유: 필터의 목적이 "엣지를 화 단위로 강조"라 엣지가 곧 SSOT이고, 실제
 * 엣지가 참조하는 화만 칩으로 떠 빈 화 칩이 안 생긴다. "N화"는 **선행 숫자로 정렬**한다 —
 * 문자열 정렬이면 "11화"가 "2화" 앞에 오는 함정을 피하려고. 레거시 센티넬 "?"가 하나라도 있으면
 * [SENTINEL_CHAPTER_LABEL] 칩을 맨 뒤에 붙인다(전체 재구축하면 실제 화 provenance로 바뀌며
 * 자연히 사라지는 과도기 칩). 리셋 라벨 "전체"는 UI가 앞에 붙이므로 여기서는 반환하지 않는다.
 */
internal fun chapterFilterLabels(edges: List<GraphEdge>): List<String> {
    val all = edges.flatMapTo(mutableSetOf()) { it.chapters }
    val real = all.filter { it != SENTINEL_CHAPTER }
        .sortedBy { label -> label.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE }
    return if (SENTINEL_CHAPTER in all) real + SENTINEL_CHAPTER_LABEL else real
}

/** The Second Brain relationship graph — mirrors the prototype's BrainScreen. */
@Composable
fun BrainScreen(
    nodes: List<GraphNode>,
    edges: List<GraphEdge>,
    orphanIds: Set<String>,
    wikiEntries: List<WikiEntry>,
    modifier: Modifier = Modifier,
    rebuildBanner: (@Composable () -> Unit)? = null,
    onNodeMoved: (id: String, x: Float, y: Float) -> Unit = { _, _, _ -> },
) {
    var filter by remember { mutableStateOf(ALL_LABEL) }
    var chapterFilter by remember { mutableStateOf(ALL_LABEL) }
    var selected by remember { mutableStateOf<String?>(null) }
    val positions = remember(nodes) {
        mutableStateMapOf<String, Offset>().apply {
            nodes.forEach { put(it.id, Offset(it.x, it.y)) }
        }
    }

    // 필터는 표시만 거른다(파생 데이터 무접촉): 타입이 걸러진 노드는 그 노드에 닿는 엣지와
    // 함께 사라진다 — 한쪽 끝만 보이는 엣지는 허공을 가리키는 선이 되기 때문. 선택된 노드가
    // 필터로 사라지면 선택도 무시해(컴포지션 중 상태를 되쓰는 대신 파생 값으로) 보이지 않는
    // 노드의 툴팁이 남지 않게 한다.
    val filterType = filterToType(filter)
    val visibleNodes = if (filterType == null) nodes else nodes.filter { it.type == filterType }
    val visibleIds = visibleNodes.mapTo(mutableSetOf()) { it.id }
    val visibleEdges = edges.filter { it.from in visibleIds && it.to in visibleIds }
    val visibleSelected = selected?.takeIf { it in visibleIds }

    // 화 칩은 전체 엣지에서 파생해 안정적으로 둔다(타입 필터로 칩이 사라졌다 나타나지 않게).
    // 강조 자체는 아래 GraphCanvas가 받는 visibleEdges 위에서만 일어나 타입 필터를 존중한다.
    // 재구축으로 선택한 화가 사라지면 highlightToken을 null로 무효화(유령 강조 방지) — 컴포지션
    // 중 상태를 되쓰는 대신 파생값으로. "출처 미상" 칩은 다시 센티넬 토큰으로 되돌려 매칭한다.
    val chapterChips = remember(edges) { listOf(ALL_LABEL) + chapterFilterLabels(edges) }
    val highlightToken: String? = when {
        chapterFilter == ALL_LABEL || chapterFilter !in chapterChips -> null
        chapterFilter == SENTINEL_CHAPTER_LABEL -> SENTINEL_CHAPTER
        else -> chapterFilter
    }

    Column(modifier = modifier.fillMaxSize().background(SmColors.surfaceSubtle)) {
        SmToolbar(title = "세컨드 브레인")
        rebuildBanner?.invoke()

        // 타입 칩(표시 대상 필터)과 화 칩(강조 오버레이)은 관심사가 달라 두 줄로 공존한다.
        FilterChipRow(labels = FILTER_LABELS, selected = filter, onSelect = { filter = it })
        // 화 칩은 강조할 엣지가 있을 때만("전체" 외 칩이 하나라도) 렌더. 화 선택은 노드 선택과
        // 상호배타 — 화를 고르면 노드 선택을 풀어 두 딤 오버레이가 겹치지 않게 한다.
        if (chapterChips.size > 1) {
            FilterChipRow(
                labels = chapterChips,
                selected = chapterFilter,
                onSelect = { chapterFilter = it; if (it != ALL_LABEL) selected = null },
            )
        }

        Box(modifier = Modifier.weight(1f, fill = true)) {
            GraphCanvas(
                nodes = visibleNodes,
                edges = visibleEdges,
                orphanIds = orphanIds,
                wikiEntries = wikiEntries,
                positions = positions,
                selected = visibleSelected,
                highlightChapter = highlightToken,
                // 노드 선택도 화 강조와 상호배타 — 노드를 고르면 화 칩을 "전체"로 되돌린다.
                onSelect = { selected = it; if (it != null) chapterFilter = ALL_LABEL },
                onNodeMoved = onNodeMoved,
            )
        }
    }
}

/** 한 줄짜리 pill 필터 칩 행 — 타입 필터와 화 필터가 같은 시각 스타일을 공유하도록 추출.
 * [selected]는 현재 켜진 라벨(둘 다 [ALL_LABEL]을 "필터 없음"으로 씀). */
@Composable
private fun FilterChipRow(labels: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(SmColors.surfaceBase)
            .padding(vertical = 9.dp, horizontal = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(labels) { f ->
            val on = f == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) SmColors.brand else Color.Transparent)
                    .then(
                        if (!on) Modifier.border(1.dp, SmColors.borderDefault, RoundedCornerShape(50))
                        else Modifier
                    )
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        onSelect(f)
                    }
                    .padding(horizontal = 13.dp, vertical = 5.dp),
            ) {
                Text(
                    text = f,
                    color = if (on) Color.White else SmColors.textSecondary,
                    fontFamily = Pretendard,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun GraphCanvas(
    nodes: List<GraphNode>,
    edges: List<GraphEdge>,
    orphanIds: Set<String>,
    wikiEntries: List<WikiEntry>,
    positions: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Offset>,
    selected: String?,
    highlightChapter: String?,
    onSelect: (String?) -> Unit,
    onNodeMoved: (id: String, x: Float, y: Float) -> Unit,
) {
    var pressed by remember { mutableStateOf<String?>(null) }
    // 딤(강조) 오버레이의 두 구동원 — 노드 선택 포커스와 화 강조 — 는 상호배타다(호출부에서
    // 보장). 화가 켜져 있으면 노드 포커스는 무시하고, 그때 강조 대상은 그 화의 provenance를
    // 가진 엣지와 그 끝 노드들. [dimming]이 참이면(둘 중 하나라도 켜짐) 나머지를 흐리게 한다.
    val chapterActive = highlightChapter != null
    val focusId = if (chapterActive) null else selected
    val dimming = chapterActive || focusId != null
    val connectedSet: Set<String>? = focusId?.let { id ->
        buildSet {
            add(id)
            edges.forEach { (a, b) ->
                if (a == id) add(b)
                if (b == id) add(a)
            }
        }
    }
    // 화 강조 시 밝게 유지할 노드 = 강조된 엣지의 양 끝(코드가 결정, 규칙2와 무관한 표시 계산).
    val highlightedNodeIds: Set<String> = if (chapterActive) {
        edges.filter { highlightChapter in it.chapters }
            .flatMapTo(mutableSetOf()) { listOf(it.from, it.to) }
    } else emptySet()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onSelect(null)
            },
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthPx = maxWidth.value * density.density
        val heightPx = maxHeight.value * density.density

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            edges.forEach { edge ->
                val a = edge.from
                val b = edge.to
                val na = positions[a] ?: return@forEach
                val nb = positions[b] ?: return@forEach
                val hilit = if (chapterActive) highlightChapter in edge.chapters
                else focusId == null || a == focusId || b == focusId
                drawLine(
                    color = if (hilit && dimming) SmColors.brand else SmColors.edgeLine,
                    start = Offset(na.x / 100f * size.width, na.y / 100f * size.height),
                    end = Offset(nb.x / 100f * size.width, nb.y / 100f * size.height),
                    strokeWidth = if (hilit && dimming) 2.dp.toPx() else 1.2.dp.toPx(),
                    alpha = if (dimming) (if (hilit) 1f else 0.12f) else 1f,
                )
            }
        }

        nodes.forEach { node ->
            val pos = positions[node.id] ?: Offset(node.x, node.y)
            val isSel = selected == node.id
            val isPressed = pressed == node.id
            val hilit = if (chapterActive) node.id in highlightedNodeIds
            else focusId == null || connectedSet?.contains(node.id) == true
            val visual = nodeVisual(node.type)

            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else if (isSel) 1.05f else 1f,
                label = "nodeScale",
            )
            val opacity by animateFloatAsState(
                targetValue = if (dimming) (if (hilit) 1f else 0.18f) else 1f,
                label = "nodeOpacity",
            )

            val offsetXDp = maxWidth * (pos.x / 100f) - 28.dp
            val offsetYDp = maxHeight * (pos.y / 100f) - 28.dp

            Column(
                modifier = Modifier
                    .offset(x = offsetXDp, y = offsetYDp)
                    .graphicsLayer {
                        alpha = opacity
                    }
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = { pressed = node.id },
                            // 드래그 종료 시에만 영속화 — 프레임마다 DB에 쓰지 않으면서도
                            // 사용자가 정한 최종 위치가 탭 전환·재시작을 넘어 유지된다.
                            onDragEnd = {
                                pressed = null
                                positions[node.id]?.let { onNodeMoved(node.id, it.x, it.y) }
                            },
                            onDragCancel = { pressed = null },
                        ) { change, dragAmount ->
                            change.consume()
                            val current = positions[node.id] ?: return@detectDragGestures
                            val dxPercent = if (widthPx > 0) dragAmount.x / widthPx * 100f else 0f
                            val dyPercent = if (heightPx > 0) dragAmount.y / heightPx * 100f else 0f
                            positions[node.id] = Offset(
                                (current.x + dxPercent).coerceIn(5f, 95f),
                                (current.y + dyPercent).coerceIn(5f, 95f),
                            )
                        }
                    }
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        onSelect(if (selected == node.id) null else node.id)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val breathe = if (node.id in orphanIds) {
                    val transition = rememberInfiniteTransition(label = "orphanBreathe")
                    val a by transition.animateFloat(
                        initialValue = 1f, targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
                        label = "orphanAlpha",
                    )
                    a
                } else 1f

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = breathe }
                        .clip(CircleShape)
                        .background(visual.bg)
                        .then(
                            if (isSel) Modifier.border(2.5.dp, visual.fill, CircleShape) else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(visual.fill)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = node.label,
                    color = if (node.id in orphanIds) SmColors.nodeOrphan
                    else if (isSel) visual.fill else SmColors.textPrimary,
                    fontFamily = Pretendard,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 10.sp,
                )
            }
        }

        val tooltipEntry = focusId?.let { id -> wikiEntries.find { it.id == id } }
        AnimatedVisibility(
            visible = focusId != null,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 4 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = 60.dp)
                .fillMaxWidth(),
        ) {
            val entryName = tooltipEntry?.name ?: nodes.find { it.id == focusId }?.label ?: ""
            val entryDesc = tooltipEntry?.desc ?: "아직 연결되지 않은 이야기예요."
            val entryChapter = tooltipEntry?.chapter ?: "—"
            val entryType = tooltipEntry?.type ?: SmBadgeType.Orphan
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SmColors.surfaceBase)
                    .border(1.dp, SmColors.borderDefault, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entryName,
                            color = SmColors.textPrimary,
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f, fill = true),
                        )
                        SmBadge(type = entryType)
                        SmIconButton(icon = SmIcons.Close, onClick = { onSelect(null) }, iconSize = 16.dp)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = entryDesc,
                        color = SmColors.textSecondary,
                        fontFamily = Pretendard,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "마지막 등장: $entryChapter",
                        color = SmColors.textTertiary,
                        fontFamily = Pretendard,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // Orphan warning banner
        val orphanLabels = nodes.filter { it.id in orphanIds }.map { it.label }
        if (orphanLabels.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SmColors.nodeOrphanBg)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val transition = rememberInfiniteTransition(label = "banner")
                    val a by transition.animateFloat(
                        initialValue = 1f, targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
                        label = "bannerAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(a)
                            .clip(CircleShape)
                            .background(SmColors.nodeOrphan)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "아직 연결되지 않은 이야기예요 — ${orphanLabels.joinToString(", ")}",
                        color = SmColors.nodeOrphan,
                        fontFamily = Pretendard,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}