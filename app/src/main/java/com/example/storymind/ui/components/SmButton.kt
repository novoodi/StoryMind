package com.example.storymind.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors
import com.example.storymind.ui.theme.SmRadius

enum class SmButtonVariant { Primary, Secondary, Ghost, Danger, Soft }
enum class SmButtonSize { Sm, Md, Lg }

private data class SizeSpec(val height: androidx.compose.ui.unit.Dp, val hPad: androidx.compose.ui.unit.Dp, val fontSize: androidx.compose.ui.unit.TextUnit, val radius: androidx.compose.ui.unit.Dp)

private fun sizeSpec(size: SmButtonSize) = when (size) {
    SmButtonSize.Sm -> SizeSpec(36.dp, 14.dp, 13.sp, SmRadius.md)
    SmButtonSize.Md -> SizeSpec(44.dp, 18.dp, 15.sp, SmRadius.lg)
    SmButtonSize.Lg -> SizeSpec(53.dp, 24.dp, 16.sp, SmRadius.lg)
}

private data class VariantSpec(val background: Color, val content: Color, val border: BorderStroke?)

private fun variantSpec(variant: SmButtonVariant) = when (variant) {
    SmButtonVariant.Primary -> VariantSpec(SmColors.brand, Color.White, null)
    SmButtonVariant.Secondary -> VariantSpec(Color.Transparent, SmColors.brand, BorderStroke(1.5.dp, SmColors.brand))
    SmButtonVariant.Ghost -> VariantSpec(Color.Transparent, SmColors.textPrimary, null)
    SmButtonVariant.Danger -> VariantSpec(SmColors.surfaceWarning, SmColors.nodeOrphan, null)
    SmButtonVariant.Soft -> VariantSpec(SmColors.brandSubtle, SmColors.brand, null)
}

/** Primary action button — mirrors components/actions/Button.jsx. Press scales to 0.97 at 120ms. */
@Composable
fun SmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SmButtonVariant = SmButtonVariant.Primary,
    size: SmButtonSize = SmButtonSize.Md,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val spec = sizeSpec(size)
    val vSpec = variantSpec(variant)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(120),
        label = "buttonPress",
    )
    val disabledOrLoading = !enabled || loading

    val shape = RoundedCornerShape(spec.radius)
    Row(
        modifier = (if (fullWidth) modifier.fillMaxWidth() else modifier.wrapContentWidth())
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(spec.height)
            .clip(shape)
            .background(vSpec.background)
            .then(if (vSpec.border != null) Modifier.border(vSpec.border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !disabledOrLoading,
                onClick = onClick,
            )
            .padding(horizontal = spec.hPad),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha = if (!enabled) 0.38f else 1f
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = vSpec.content,
                strokeWidth = 2.5.dp,
            )
        } else {
            icon?.invoke()
        }
        Text(
            text = text,
            color = vSpec.content.copy(alpha = alpha),
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = spec.fontSize,
            modifier = Modifier.padding(start = if (icon != null || loading) 6.dp else 0.dp),
        )
    }
}