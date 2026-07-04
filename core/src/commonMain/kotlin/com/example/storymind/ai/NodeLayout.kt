package com.example.storymind.ai

import com.example.storymind.data.GraphNode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val CENTER = 50f
private const val MIN_COORD = 5f
private const val MAX_COORD = 95f

/**
 * 황금각(2π/φ², 라디안). 연속 인덱스에 곱해 각도를 만들면 어떤 인덱스 조합에서도 각도가
 * 주기적으로 되풀이되지 않는다 — 해바라기 씨앗 배치와 같은 원리. 예전의 "이번 화 노드끼리만
 * 원형 균등 배치"는 화마다 같은 원(중심 50, 반지름 32) 위 같은 각도들을 다시 쓰기 때문에,
 * 2화의 노드가 1화의 노드 위에 정확히 포개지고, 새 노드가 1개뿐인 화는 전부 정중앙(50,50)에
 * 쌓이는 문제가 있었다(2026-07 코드 감사).
 */
private const val GOLDEN_ANGLE = 2.399963229728653

/** 반지름은 전역 인덱스의 제곱근에 비례해 완만하게 자란다(k=0에서 [RADIUS_STEP], 이후 바깥
 * 고리로). [MAX_RADIUS]에서 멈추는 이유: 캔버스가 0-100% 고정이라 무한히 퍼질 수 없고, 상한에
 * 모인 노드들도 황금각 덕에 서로 다른 각도를 받아 테두리 위에서 흩어진다. */
private const val RADIUS_STEP = 9.0
private const val MAX_RADIUS = 42.0

/**
 * Assigns 0-100% canvas coordinates to freshly-ingested nodes, which all arrive as (0,0)
 * placeholders. Positions are a deterministic function of each node's **global accumulation
 * index** ([existingCount] + list position) alone — never of the model (rule 2), and never of
 * how many nodes happen to arrive together — so an incremental merge and a full rebuild that
 * accumulate the same nodes in the same order produce identical coordinates. Real positioning
 * after that is up to the user's drag interactions on BrainScreen (persisted via
 * `StoryDao.updateNodePosition`; [com.example.storymind.ai.merge] keeps existing nodes untouched).
 */
fun layoutNodes(nodes: List<GraphNode>, existingCount: Int = 0): List<GraphNode> =
    nodes.mapIndexed { index, node ->
        val k = existingCount + index
        val angle = -PI / 2 + k * GOLDEN_ANGLE
        val radius = min(RADIUS_STEP * sqrt((k + 1).toDouble()), MAX_RADIUS)
        val x = (CENTER + radius * cos(angle)).toFloat().coerceIn(MIN_COORD, MAX_COORD)
        val y = (CENTER + radius * sin(angle)).toFloat().coerceIn(MIN_COORD, MAX_COORD)
        node.copy(x = x, y = y)
    }
