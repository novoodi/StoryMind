package com.example.storymind.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors
import com.example.storymind.ui.theme.SmRadius
import com.example.storymind.ui.theme.smCardShadow

/** Elevated content card for wiki entries & logs — mirrors components/data/Card.jsx. */
@Composable
fun SmCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    badge: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    description: String? = null,
    meta: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(SmRadius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .smCardShadow(SmRadius.lg)
            .clip(shape)
            .background(SmColors.surfaceCard)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .then(
                if (selected) Modifier.border(2.dp, SmColors.brand, shape) else Modifier
            )
            .padding(16.dp),
    ) {
        if (title != null || badge != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = SmColors.textPrimary,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                badge?.invoke()
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = SmColors.textSecondary,
                fontFamily = Pretendard,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = if (title != null) 3.dp else 0.dp),
            )
        }
        if (description != null) {
            Text(
                text = description,
                color = SmColors.textTertiary,
                fontFamily = Pretendard,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        content?.invoke()
        if (meta != null) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp),
                color = SmColors.borderDefault,
                thickness = 1.dp,
            )
            Text(
                text = meta,
                color = SmColors.textTertiary,
                fontFamily = Pretendard,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}