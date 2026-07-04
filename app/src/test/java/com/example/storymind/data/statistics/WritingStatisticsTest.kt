package com.example.storymind.data.statistics

import com.example.storymind.data.db.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** computeWritingStats는 화면이 그대로 표시하는 숫자를 만든다 — 소리 없이 어긋나면 안 되므로
 * (형식/계산 순수 함수 컨벤션, formatManuscriptTxt와 동일) 값들을 그대로 못 박는다. */
class WritingStatisticsTest {

    private fun chapter(index: Int, body: String, ingested: Boolean = true) = ChapterEntity(
        chapterIndex = index,
        label = "${index + 1}화",
        title = null,
        body = body,
        ingested = ingested,
    )

    @Test
    fun aggregatesCharsChaptersAverageAndLongest() {
        val stats = computeWritingStats(
            listOf(
                chapter(0, "12345", ingested = true),          // 5자, 분석됨
                chapter(1, "1234567890", ingested = false),     // 10자, 미분석
            )
        )
        assertEquals(15, stats.totalChars)
        assertEquals(2, stats.writtenChapters)
        assertEquals(1, stats.analyzedChapters)
        assertEquals(7, stats.avgChars)                         // 15 / 2 = 7 (정수 나눗셈)
        assertEquals(ChapterStat(1, 10), stats.longest)
        assertEquals(listOf(ChapterStat(0, 5), ChapterStat(1, 10)), stats.perChapter)
    }

    @Test
    fun skipsBlankChapters() {
        val stats = computeWritingStats(
            listOf(
                chapter(0, "본문 있음"),
                chapter(1, "   \n"),                             // 공백뿐 — 원고 아님
            )
        )
        assertEquals(1, stats.writtenChapters)
        assertEquals(listOf(ChapterStat(0, "본문 있음".length)), stats.perChapter)
    }

    @Test
    fun ordersPerChapterByIndexRegardlessOfInput() {
        val stats = computeWritingStats(
            listOf(chapter(2, "셋째"), chapter(0, "첫째"))
        )
        assertEquals(listOf(0, 2), stats.perChapter.map { it.chapterIndex })
    }

    @Test
    fun emptyInputYieldsZeroesAndNoLongest() {
        val stats = computeWritingStats(emptyList())
        assertEquals(0, stats.totalChars)
        assertEquals(0, stats.writtenChapters)
        assertEquals(0, stats.avgChars)
        assertNull(stats.longest)
        assertEquals(emptyList<ChapterStat>(), stats.perChapter)
    }
}
