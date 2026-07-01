package com.example.storymind.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft blue-tinted shadows — mirrors tokens/effects.css. CSS box-shadow pairs
 * a blur shadow with a 1px hairline ring; we approximate with a low
 * elevation shadow plus a matching border stroke.
 */
private val shadowTint = Color(0x1A1A1B1E)
private val hairline = Color(0x0D1A1B1E)

fun Modifier.smCardShadow(radius: Dp = SmRadius.lg): Modifier = this
    .shadow(
        elevation = 2.dp,
        shape = RoundedCornerShape(radius),
        ambientColor = shadowTint,
        spotColor = shadowTint,
        clip = false,
    )
    .border(BorderStroke(1.dp, hairline), RoundedCornerShape(radius))

fun Modifier.smFloatShadow(radius: Dp = SmRadius.xl): Modifier = this.shadow(
    elevation = 12.dp,
    shape = RoundedCornerShape(radius),
    ambientColor = shadowTint,
    spotColor = shadowTint,
    clip = false,
)