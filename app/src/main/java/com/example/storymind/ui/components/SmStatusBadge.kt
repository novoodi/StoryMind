package com.example.storymind.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors

enum class SmAiStatus { Analyzing, Warning, Done, Idle }

/**
 * Floating AI-status pill — mirrors components/feedback/StatusBadge.jsx.
 * Renders nothing for [SmAiStatus.Idle]. Never a full-page spinner, per spec.
 */
@Composable
fun SmStatusBadge(status: SmAiStatus, modifier: Modifier = Modifier) {
    if (status == SmAiStatus.Idle) return
    val (label, color, pulsing) = when (status) {
        SmAiStatus.Analyzing -> Triple("살펴보는 중이에요", SmColors.brand, true)
        SmAiStatus.Warning -> Triple("확인이 필요해요", SmColors.nodeOrphan, true)
        SmAiStatus.Done -> Triple("완료됐어요", SmColors.nodePlace, false)
        SmAiStatus.Idle -> Triple("", Color.Transparent, false)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(SmColors.surfaceSubtle)
            .border(1.dp, SmColors.borderDefault, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dotAlpha = if (pulsing) {
            val transition = rememberInfiniteTransition(label = "aiDot")
            val a by transition.animateFloat(
                initialValue = 1f, targetValue = 0.35f,
                animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                label = "aiDotAlpha",
            )
            a
        } else 1f
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(5.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = color,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}