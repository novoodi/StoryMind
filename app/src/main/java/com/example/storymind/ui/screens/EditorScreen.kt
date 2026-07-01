package com.example.storymind.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.data.MockData
import com.example.storymind.ui.components.SmAiStatus
import com.example.storymind.ui.components.SmIconButton
import com.example.storymind.ui.components.SmStatusBadge
import com.example.storymind.ui.components.SmToolbar
import com.example.storymind.ui.icons.SmIcon
import com.example.storymind.ui.icons.SmIcons
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors

/**
 * The writing surface — mirrors the prototype's EditorScreen. AI status pill,
 * chapter body, empty state, and a bottom formatting bar (bold/italic/link).
 * `shakeTrigger` increments to replay the 200ms conflict-warning shake.
 */
@Composable
fun EditorScreen(
    aiStatus: SmAiStatus,
    shakeTrigger: Int,
    isEmpty: Boolean,
    onWikiOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SmToolbar(
            title = "1장 — 빗소리",
            left = {
                Text(
                    text = "← 소설",
                    color = SmColors.textTertiary,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            },
            badge = { SmStatusBadge(status = aiStatus) },
            right = {
                SmIconButton(icon = SmIcons.Wiki, onClick = onWikiOpen)
                SmIconButton(icon = SmIcons.More, onClick = {})
            },
        )
        Box(modifier = Modifier.weight(1f)) {
            if (isEmpty) {
                EditorEmptyState()
            } else {
                EditorBody(shakeTrigger = shakeTrigger)
            }
        }
        EditorFormatBar()
    }
}

@Composable
private fun EditorEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SmColors.surfaceBase)
            .padding(horizontal = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SmColors.brandSubtle),
            contentAlignment = Alignment.Center,
        ) {
            SmIcon(id = SmIcons.Edit, tint = SmColors.brand, size = 26.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "아직 작성된 원고가 없어요.",
            color = SmColors.textPrimary,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "첫 문장을 시작해 볼까요?",
            color = SmColors.textSecondary,
            fontFamily = Pretendard,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(1.dp)
                .background(SmColors.borderDefault)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "글을 쓰는 동안 AI가 조용히\n설정집과 위키를 채워드려요.",
            color = SmColors.textTertiary,
            fontFamily = Pretendard,
            fontSize = 12.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EditorBody(shakeTrigger: Int) {
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            val keyframeSpec = keyframes<Float> {
                durationMillis = 200
                0f at 0
                -5f at 40
                5f at 80
                -3f at 120
                3f at 160
                0f at 200
            }
            shakeX.animateTo(0f, animationSpec = keyframeSpec)
        }
    }

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = shakeX.value }
            .background(SmColors.surfaceBase)
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Text(
            text = MockData.chapterTitle,
            color = SmColors.textPrimary,
            fontFamily = Pretendard,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            modifier = Modifier.padding(bottom = 18.dp),
        )
        MockData.chapterParagraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                color = SmColors.textPrimary,
                fontFamily = Pretendard,
                fontSize = 18.sp,
                lineHeight = 32.sp,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
        val transition = rememberInfiniteTransition(label = "cursor")
        val cursorAlpha by transition.animateFloat(
            initialValue = 1f, targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
            label = "cursorAlpha",
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(18.dp)
                .alpha(cursorAlpha)
                .background(SmColors.brand)
        )
    }
}

@Composable
private fun EditorFormatBar() {
    val borderColor = SmColors.borderDefault
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(SmColors.surfaceBase)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmIconButton(icon = SmIcons.Bold, onClick = {}, iconSize = 18.dp)
        SmIconButton(icon = SmIcons.Italic, onClick = {}, iconSize = 18.dp)
        SmIconButton(icon = SmIcons.Link, onClick = {}, iconSize = 18.dp)
        Spacer(Modifier.weight(1f))
        Text(
            text = "128자",
            color = SmColors.textTertiary,
            fontFamily = Pretendard,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}