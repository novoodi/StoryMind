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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.storymind.ai.LintVerdict
import com.example.storymind.ui.LintFindingUi
import com.example.storymind.ui.LintUiState
import com.example.storymind.ui.icons.SmIcons
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors
import com.example.storymind.ui.theme.SmDuration
import com.example.storymind.ui.theme.SmEasing
import com.example.storymind.ui.theme.SmRadius

private data class VerdictSpec(val label: String, val color: Color, val bg: Color, val rank: Int)

private fun verdictSpec(verdict: LintVerdict): VerdictSpec = when (verdict) {
    // rank orders the list so the most actionable findings surface first.
    LintVerdict.Conflict -> VerdictSpec("충돌", SmColors.nodeOrphan, SmColors.nodeOrphanBg, rank = 0)
    LintVerdict.Ambiguous -> VerdictSpec("모호", SmColors.textSecondary, SmColors.surfaceSubtle, rank = 1)
    LintVerdict.Development -> VerdictSpec("전개", SmColors.nodePlace, SmColors.nodePlaceBg, rank = 2)
}

/**
 * On-demand "설정 검사" result sheet — same slide-up/scrim language as [SmBottomSheet], but built
 * for a variable-length findings list (that sheet's fixed title+description+two-actions shape
 * doesn't fit a list) inside a [LazyColumn] so a chapter with many findings still scrolls rather
 * than overflowing the sheet. Findings are shown flat, ordered by [VerdictSpec.rank] (conflict
 * first) rather than grouped under section headers — v1 keeps this minimal per CLAUDE.md's ai/
 * lint scope (findings-only, no DB write, no navigation to the flagged entity yet).
 *
 * Takes the whole [LintUiState] rather than a plain findings list so Running/Failed render inside
 * the same sheet shell instead of the caller having to branch on state before deciding whether to
 * open it at all.
 */
@Composable
fun SmLintResultSheet(
    isOpen: Boolean,
    onClose: () -> Unit,
    state: LintUiState,
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .clip(RoundedCornerShape(topStart = SmRadius.xl, topEnd = SmRadius.xl))
                    .background(SmColors.surfaceBase),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SmColors.borderDefault)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "설정 검사 결과",
                        color = SmColors.textPrimary,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                    SmIconButton(icon = SmIcons.Close, onClick = onClose)
                }
                when (state) {
                    is LintUiState.Running -> LintRunningState()
                    is LintUiState.Failed -> LintFailedState()
                    is LintUiState.Idle -> Unit // sheet isn't opened for Idle; nothing to render
                    is LintUiState.Done -> if (state.findings.isEmpty()) {
                        LintCleanState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.findings.sortedBy { verdictSpec(it.verdict).rank }) { finding ->
                                LintFindingCard(finding)
                            }
                            if (state.truncated) {
                                item {
                                    Text(
                                        text = "결과가 많아 일부만 표시했어요.",
                                        color = SmColors.textTertiary,
                                        fontFamily = Pretendard,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LintRunningState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = SmColors.brand, strokeWidth = 2.5.dp)
        Text(
            text = "위키 이력과 이번 화를 대조하고 있어요…",
            color = SmColors.textSecondary,
            fontFamily = Pretendard,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun LintFailedState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "설정 검사에 실패했어요.",
            color = SmColors.textPrimary,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Text(
            text = "잠시 후 다시 시도해주세요.",
            color = SmColors.textTertiary,
            fontFamily = Pretendard,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LintCleanState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "설정 충돌이 발견되지 않았어요.",
            color = SmColors.textPrimary,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Text(
            text = "위키 이력과 이번 화가 잘 이어지고 있어요.",
            color = SmColors.textTertiary,
            fontFamily = Pretendard,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LintFindingCard(finding: LintFindingUi, modifier: Modifier = Modifier) {
    val spec = verdictSpec(finding.verdict)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SmRadius.lg))
            .background(SmColors.surfaceCard)
            .padding(14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = finding.entityName,
                    color = SmColors.textPrimary,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(spec.bg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = spec.label,
                        color = spec.color,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                }
            }
            Text(
                text = finding.reason,
                color = SmColors.textSecondary,
                fontFamily = Pretendard,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
