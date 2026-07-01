package com.example.storymind.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors
import com.example.storymind.ui.theme.SmDuration
import com.example.storymind.ui.theme.SmEasing
import com.example.storymind.ui.theme.SmRadius

data class SmSheetAction(val label: String, val onClick: () -> Unit)

/**
 * Slide-up overlay sheet for AI conflict warnings — mirrors
 * components/feedback/BottomSheet.jsx. Animates in at 280ms ease-decelerate.
 * Caller places this inside a Box(Modifier.fillMaxSize()) alongside screen content.
 */
@Composable
fun SmBottomSheet(
    isOpen: Boolean,
    onClose: () -> Unit,
    title: String,
    description: String,
    primaryAction: SmSheetAction,
    secondaryAction: SmSheetAction,
    modifier: Modifier = Modifier,
    conflictLabel: String = "설정 충돌을 발견했어요",
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(SmDuration.slow)),
        exit = fadeOut(tween(SmDuration.slow)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x661A1B1E))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onClose,
                )
        )
    }
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInVertically(
            animationSpec = tween(SmDuration.normal, easing = SmEasing.decelerate),
            initialOffsetY = { it },
        ),
        exit = slideOutVertically(
            animationSpec = tween(SmDuration.normal, easing = SmEasing.accelerate),
            targetOffsetY = { it },
        ),
        modifier = modifier
            .fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = SmRadius.xl, topEnd = SmRadius.xl))
                    .background(SmColors.surfaceBase)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 36.dp),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SmColors.borderDefault)
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SmRadius.md))
                            .background(SmColors.surfaceWarning)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp, end = 9.dp)
                                    .size(6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(SmColors.nodeOrphan)
                            )
                            Text(
                                text = conflictLabel,
                                color = SmColors.nodeOrphan,
                                fontFamily = Pretendard,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    Text(
                        text = title,
                        color = SmColors.textPrimary,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text(
                        text = description,
                        color = SmColors.textSecondary,
                        fontFamily = Pretendard,
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
                    SmButton(
                        text = primaryAction.label,
                        onClick = primaryAction.onClick,
                        variant = SmButtonVariant.Primary,
                        size = SmButtonSize.Lg,
                        fullWidth = true,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                    SmButton(
                        text = secondaryAction.label,
                        onClick = secondaryAction.onClick,
                        variant = SmButtonVariant.Ghost,
                        size = SmButtonSize.Md,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}