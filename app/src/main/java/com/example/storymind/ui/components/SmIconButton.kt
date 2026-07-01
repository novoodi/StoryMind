package com.example.storymind.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.storymind.ui.icons.SmIcon
import com.example.storymind.ui.theme.SmColors

/** Bare icon button — mirrors the prototype's IBtn (36×36, no background, textSecondary tint). */
@Composable
fun SmIconButton(
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = SmColors.textSecondary,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SmIcon(id = icon, tint = tint, size = iconSize)
    }
}