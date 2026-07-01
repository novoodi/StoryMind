package com.example.storymind.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors
import com.example.storymind.ui.theme.SmRadius

enum class SmInputSize { Sm, Md, Lg }

/** Controlled text input — mirrors components/forms/Input.jsx. */
@Composable
fun SmInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    icon: (@Composable () -> Unit)? = null,
    size: SmInputSize = SmInputSize.Md,
    enabled: Boolean = true,
) {
    val height = when (size) {
        SmInputSize.Sm -> 36.dp
        SmInputSize.Md -> 44.dp
        SmInputSize.Lg -> 53.dp
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        error != null -> SmColors.nodeOrphan
        focused -> SmColors.brand
        else -> SmColors.borderDefault
    }
    val background = if (error != null) SmColors.surfaceWarning else SmColors.surfaceBase

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                color = SmColors.textSecondary,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(SmRadius.md))
                .background(background)
                .border(1.dp, borderColor, RoundedCornerShape(SmRadius.md))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke()
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = Pretendard,
                    fontSize = 15.sp,
                    color = if (enabled) SmColors.textPrimary else SmColors.textDisabled,
                ),
                cursorBrush = SolidColor(SmColors.brand),
                interactionSource = interaction,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (icon != null) 8.dp else 0.dp),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = SmColors.textPlaceholder,
                            fontFamily = Pretendard,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )
        }
        if (error != null) {
            Text(
                text = error,
                color = SmColors.nodeOrphan,
                fontFamily = Pretendard,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }
    }
}