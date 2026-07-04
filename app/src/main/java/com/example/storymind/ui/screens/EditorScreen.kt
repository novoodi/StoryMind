package com.example.storymind.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import com.example.storymind.ui.components.SmAiStatus
import com.example.storymind.ui.components.SmButton
import com.example.storymind.ui.components.SmButtonSize
import com.example.storymind.ui.components.SmButtonVariant
import com.example.storymind.ui.components.SmIconButton
import com.example.storymind.ui.components.SmStatusBadge
import com.example.storymind.ui.components.SmToolbar
import com.example.storymind.ui.icons.SmIcon
import com.example.storymind.ui.icons.SmIcons
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors

/** A previously saved chapter, shown read-only above the active draft. */
data class EditorChapterSnapshot(val label: String, val title: String?, val body: String)

/**
 * The writing surface — mirrors the prototype's EditorScreen. AI status pill,
 * chapter body, empty state, and a bottom formatting bar (bold/italic/link/char count/save).
 * `shakeTrigger` increments to replay the 200ms conflict-warning shake.
 */
@Composable
fun EditorScreen(
    aiStatus: SmAiStatus,
    shakeTrigger: Int,
    canAdvance: Boolean,
    spellCheck: Boolean,
    previousChapters: List<EditorChapterSnapshot>,
    currentLabel: String,
    currentBody: String,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onNextChapter: () -> Unit,
    onWikiOpen: () -> Unit,
    canLint: Boolean,
    lintRunning: Boolean,
    onLint: () -> Unit,
    onRetryIngest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SmToolbar(
            title = previousChapters.firstOrNull()?.title?.let { "$currentLabel — $it" } ?: currentLabel,
            left = {
                Text(
                    text = "← 소설",
                    color = SmColors.textTertiary,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            },
            badge = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmStatusBadge(status = aiStatus)
                    // Only Warning (a FAILED ingest, see toAiStatus's KDoc) is user-actionable —
                    // Analyzing/Done need no input, and Idle has nothing to retry.
                    if (aiStatus == SmAiStatus.Warning) {
                        SmButton(
                            text = "다시 시도",
                            onClick = onRetryIngest,
                            variant = SmButtonVariant.Danger,
                            size = SmButtonSize.Sm,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            },
            right = {
                SmIconButton(icon = SmIcons.Wiki, onClick = onWikiOpen)
                SmIconButton(icon = SmIcons.More, onClick = {})
            },
        )
        Box(modifier = Modifier.weight(1f)) {
            EditorBody(
                shakeTrigger = shakeTrigger,
                spellCheck = spellCheck,
                previousChapters = previousChapters,
                currentLabel = currentLabel,
                currentBody = currentBody,
                onBodyChange = onBodyChange,
            )
        }
        // 저장 버튼은 AI 상태와 절대 결합하지 않는다(CLAUDE.md 규칙 3의 UI 버전): 인제스트는
        // 화당 수 분, 재구축은 그 몇 배가 걸리는데, 그동안 Analyzing 배지를 저장 잠금으로
        // 쓰면 작가가 새로 쓴 원고를 저장하지 못한 채 프로세스 사망에 노출된다. 원고 저장
        // 자체는 밀리초 단위 Room 쓰기이고, 진행 중인 인제스트와의 충돌은 워커의 REPLACE
        // 정책과 commitIngest의 body 가드가 이미 흡수한다 — 저장을 막을 이유가 없다.
        EditorFormatBar(
            charCount = currentBody.length,
            canSave = currentBody.isNotBlank(),
            justSaved = canAdvance,
            onSave = onSave,
            onNextChapter = onNextChapter,
            canLint = canLint,
            lintRunning = lintRunning,
            onLint = onLint,
        )
    }
}

/**
 * Decorative first-chapter hint. Purely visual — no pointer input — so it can sit on top of
 * the real [BasicTextField] and taps still reach the field underneath for focus.
 */
@Composable
private fun FirstChapterHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
private fun EditorBody(
    shakeTrigger: Int,
    spellCheck: Boolean,
    previousChapters: List<EditorChapterSnapshot>,
    currentLabel: String,
    currentBody: String,
    onBodyChange: (String) -> Unit,
) {
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
        previousChapters.forEachIndexed { index, chapter ->
            if (index > 0) {
                Text(
                    text = chapter.label,
                    color = SmColors.textTertiary,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                )
            }
            if (chapter.title != null) {
                Text(
                    text = chapter.title,
                    color = SmColors.textPrimary,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
            chapter.body.split(Regex("\n+")).filter { it.isNotBlank() }.forEach { paragraph ->
                Text(
                    text = paragraph,
                    color = SmColors.textPrimary,
                    fontFamily = Pretendard,
                    fontSize = 18.sp,
                    lineHeight = 32.sp,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
        }

        if (previousChapters.isNotEmpty()) {
            Text(
                text = currentLabel,
                color = SmColors.textTertiary,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            )
        }

        val isFirstEverChapter = previousChapters.isEmpty()
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = currentBody,
                onValueChange = onBodyChange,
                textStyle = TextStyle(
                    color = SmColors.textPrimary,
                    fontFamily = Pretendard,
                    fontSize = 18.sp,
                    lineHeight = 32.sp,
                ),
                cursorBrush = SolidColor(SmColors.brand),
                // 설정의 "맞춤법 검사" 토글을 IME 자동 교정 힌트로 전달 — 소설 집필 중
                // 원치 않는 자동 수정을 끌 수 있는 실제 관찰 가능한 동작이다.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = spellCheck),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 420.dp),
                decorationBox = { innerTextField ->
                    if (currentBody.isEmpty() && !isFirstEverChapter) {
                        Text(
                            text = "이어서 써보세요…",
                            color = SmColors.textTertiary,
                            fontFamily = Pretendard,
                            fontSize = 18.sp,
                            lineHeight = 32.sp,
                        )
                    }
                    innerTextField()
                },
            )
            if (currentBody.isEmpty() && isFirstEverChapter) {
                FirstChapterHint(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun EditorFormatBar(
    charCount: Int,
    canSave: Boolean,
    justSaved: Boolean,
    onSave: () -> Unit,
    onNextChapter: () -> Unit,
    canLint: Boolean,
    lintRunning: Boolean,
    onLint: () -> Unit,
) {
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
        // 서식(굵게/기울임/링크) 버튼은 본문이 순수 텍스트라 적용할 대상이 없어 제거했다 —
        // 리치 텍스트 모델이 생기기 전까지는 눌러도 아무 일도 안 하는 장식일 뿐이었다.
        Spacer(Modifier.weight(1f))
        Text(
            text = "${charCount}자",
            color = SmColors.textTertiary,
            fontFamily = Pretendard,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        if (justSaved) {
            if (canLint) {
                SmButton(
                    text = "설정 검사",
                    onClick = onLint,
                    variant = SmButtonVariant.Secondary,
                    size = SmButtonSize.Sm,
                    enabled = !lintRunning,
                    loading = lintRunning,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            SmButton(
                text = "다음 화 쓰기",
                onClick = onNextChapter,
                size = SmButtonSize.Sm,
                modifier = Modifier.padding(end = 4.dp),
            )
        } else {
            SmButton(
                text = "저장",
                onClick = onSave,
                enabled = canSave,
                size = SmButtonSize.Sm,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}