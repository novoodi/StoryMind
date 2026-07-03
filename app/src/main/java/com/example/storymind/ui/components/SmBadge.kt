package com.example.storymind.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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

// SmBadgeType은 도메인 공용 enum이라 :core commonMain으로 이동했다 (같은 패키지 유지).
enum class SmBadgeSize { Sm, Lg }

private data class BadgeSpec(val bg: Color, val color: Color, val label: String)

private fun badgeSpec(type: SmBadgeType) = when (type) {
    SmBadgeType.Character -> BadgeSpec(SmColors.nodeCharacterBg, SmColors.nodeCharacter, "인물")
    SmBadgeType.Place -> BadgeSpec(SmColors.nodePlaceBg, SmColors.nodePlace, "장소")
    SmBadgeType.Item -> BadgeSpec(SmColors.nodeItemBg, SmColors.nodeItem, "소품")
    SmBadgeType.Event -> BadgeSpec(SmColors.nodeEventBg, SmColors.nodeEvent, "사건")
    SmBadgeType.Orphan -> BadgeSpec(SmColors.nodeOrphanBg, SmColors.nodeOrphan, "미연결")
    SmBadgeType.Draft -> BadgeSpec(SmColors.surfaceSubtle, SmColors.textSecondary, "초고")
    SmBadgeType.Complete -> BadgeSpec(Color(0xFFF0FFF4), Color(0xFF16A34A), "완성")
    SmBadgeType.New -> BadgeSpec(SmColors.brandSubtle, Color(0xFF1940D4), "신규")
}

/** Pill-shaped semantic badge — mirrors components/data/Badge.jsx. */
@Composable
fun SmBadge(
    type: SmBadgeType,
    modifier: Modifier = Modifier,
    label: String? = null,
    dot: Boolean = false,
    size: SmBadgeSize = SmBadgeSize.Sm,
) {
    val spec = badgeSpec(type)
    val isLg = size == SmBadgeSize.Lg
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(spec.bg)
            .padding(horizontal = if (isLg) 10.dp else 8.dp, vertical = if (isLg) 4.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            val dotSize = if (isLg) 6.dp else 5.dp
            if (type == SmBadgeType.Orphan) {
                val transition = rememberInfiniteTransition(label = "badgeDot")
                val alpha by transition.animateFloat(
                    initialValue = 1f, targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "badgeDotAlpha",
                )
                Box(dotSize, spec.color, alpha)
            } else {
                Box(dotSize, spec.color, 1f)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        }
        Text(
            text = label ?: spec.label,
            color = spec.color,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (isLg) 12.sp else 11.sp,
        )
    }
}

@Composable
private fun Box(size: androidx.compose.ui.unit.Dp, color: Color, alpha: Float) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}
