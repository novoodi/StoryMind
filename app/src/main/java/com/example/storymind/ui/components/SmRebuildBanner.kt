package com.example.storymind.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors

/**
 * Derived-data rebuild progress strip for the Brain/Wiki screens. A separate component from
 * [SmStatusBadge] on purpose: the editor badge answers "what happened to the chapter I just
 * saved", while this answers "why is the wiki suddenly half-empty and growing back" — showing
 * one in the other's place would conflate a single chapter's ingest with the whole-story rebuild.
 * Renders nothing when no rebuild is running or stopped, so screens can include it
 * unconditionally.
 */
@Composable
fun SmRebuildBanner(
    running: Boolean,
    failed: Boolean,
    ingestedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    if (!running && !failed) return
    val (label, color, bg) = if (running) {
        Triple("위키 재구축 중 $ingestedCount/${totalCount}화", SmColors.brand, SmColors.brandSubtle)
    } else {
        Triple("재구축이 중단됐어요 — 에디터에서 다시 시도할 수 있어요", SmColors.nodeOrphan, SmColors.nodeOrphanBg)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dotAlpha = if (running) {
            val transition = rememberInfiniteTransition(label = "rebuildDot")
            val a by transition.animateFloat(
                initialValue = 1f, targetValue = 0.35f,
                animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                label = "rebuildDotAlpha",
            )
            a
        } else 1f
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = color,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}
