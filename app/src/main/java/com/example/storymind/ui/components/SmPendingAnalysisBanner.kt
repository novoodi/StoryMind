package com.example.storymind.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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

/**
 * Editor strip nudging the writer to analyze chapters they saved without ingesting — the count
 * comes from [com.example.storymind.ui.StoryViewModel.pendingAnalysisCount]. Distinct from
 * [SmStatusBadge] (what happened to *this* save) and [SmRebuildBanner] (a running whole-story
 * rebuild): this is the idle-but-incomplete case, where nothing is running and the fix is a
 * non-destructive resume (not a wipe-and-rebuild). Unlike the FAILED path's "다시 시도" button —
 * which the caller shows instead of this — reaching here isn't an error: the chapters were simply
 * never queued (auto-analyze off, or the model absent at save time).
 *
 * Renders nothing at [pendingCount] 0 so the editor can include it unconditionally; the caller
 * still gates on "nothing running / model present / not showing a failure badge" before letting a
 * non-zero count through (see [com.example.storymind.ui.StoryMindApp]).
 */
@Composable
fun SmPendingAnalysisBanner(
    pendingCount: Int,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pendingCount <= 0) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SmColors.brandSubtle)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(SmColors.brand)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "분석 안 된 화 ${pendingCount}개",
            color = SmColors.brand,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        SmButton(
            text = "지금 분석",
            onClick = onAnalyze,
            variant = SmButtonVariant.Secondary,
            size = SmButtonSize.Sm,
        )
    }
}
