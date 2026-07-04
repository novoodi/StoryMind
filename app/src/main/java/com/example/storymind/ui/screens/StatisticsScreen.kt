package com.example.storymind.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.data.WikiEntry
import com.example.storymind.data.statistics.ChapterStat
import com.example.storymind.data.statistics.WritingStats
import com.example.storymind.ui.components.SmBadgeType
import com.example.storymind.ui.components.SmCard
import com.example.storymind.ui.components.SmToolbar
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors

/**
 * 집필 통계 탭. 저장된 원고에서 파생한 글자수/화 수/화별 분량을 보여준다. 시각화는 화 순서 축의
 * 막대뿐이다 — 챕터에 timestamp가 없어 날짜 기반 추이는 스키마 변경 없이는 불가능하다(그건
 * 별도 마이그레이션 작업). 위키/그래프 개수는 [WritingStats]와 달리 파생 스냅샷에서 오므로
 * 인자로 따로 받는다.
 */
@Composable
fun StatisticsScreen(
    stats: WritingStats,
    wikiEntries: List<WikiEntry>,
    edgeCount: Int,
    orphanCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(SmColors.surfaceBase)) {
        SmToolbar(title = "통계")
        if (stats.writtenChapters == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "아직 쓴 화가 없어요.\n에디터에서 원고를 써 보세요.",
                    color = SmColors.textTertiary,
                    fontFamily = Pretendard,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        val maxChars = stats.longest?.chars ?: 1
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SmCard(title = "원고") {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        StatRow("총 글자수", "${grouped(stats.totalChars)}자")
                        StatRow("쓴 화", "${stats.writtenChapters}화")
                        StatRow("분석된 화", "${stats.analyzedChapters}화")
                        StatRow("화 평균", "${grouped(stats.avgChars)}자")
                    }
                }
            }
            item {
                SmCard(title = "설정집") {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        StatRow("등장인물", "${wikiEntries.count { it.type == SmBadgeType.Character }}명")
                        StatRow("장소", "${wikiEntries.count { it.type == SmBadgeType.Place }}곳")
                        StatRow("소품", "${wikiEntries.count { it.type == SmBadgeType.Item }}개")
                        StatRow("사건", "${wikiEntries.count { it.type == SmBadgeType.Event }}개")
                        StatRow("관계", "${edgeCount}개")
                        if (orphanCount > 0) StatRow("미연결", "${orphanCount}개")
                    }
                }
            }
            item {
                SmCard(title = "화별 분량") {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        stats.perChapter.forEach { chapter ->
                            ChapterBar(chapter, maxChars)
                        }
                    }
                }
            }
        }
    }
}

/** 라벨-값 한 줄. SettingsScreen의 SettingsRow가 private이라 통계용으로 가볍게 다시 만든다. */
@Composable
private fun StatRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = SmColors.textSecondary, fontFamily = Pretendard, fontSize = 14.sp)
        Text(
            value,
            color = SmColors.textPrimary,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

/** 한 화의 분량 막대. 폭은 가장 긴 화 대비 비율이며, 아주 짧은 화도 보이도록 최소치를 준다. */
@Composable
private fun ChapterBar(stat: ChapterStat, maxChars: Int) {
    val fraction = if (maxChars <= 0) 0f else (stat.chars.toFloat() / maxChars).coerceIn(0.02f, 1f)
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${stat.chapterIndex + 1}화",
            modifier = Modifier.width(38.dp),
            color = SmColors.textSecondary,
            fontFamily = Pretendard,
            fontSize = 12.sp,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(SmColors.surfaceSubtle),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(SmColors.brand),
            )
        }
        Text(
            "${grouped(stat.chars)}자",
            modifier = Modifier.width(66.dp).padding(start = 8.dp),
            color = SmColors.textTertiary,
            fontFamily = Pretendard,
            fontSize = 11.sp,
            textAlign = TextAlign.End,
        )
    }
}

/** 천 단위 구분 쉼표. 한국어 로케일도 쉼표로 묶으므로 기본 로케일 포맷으로 충분하다. */
private fun grouped(n: Int): String = "%,d".format(n)
