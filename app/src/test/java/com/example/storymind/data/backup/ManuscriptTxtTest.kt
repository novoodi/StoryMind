package com.example.storymind.data.backup

import com.example.storymind.data.db.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** 내보내기 형식은 한 번 배포되면 사용자 파일에 남는 계약이라(formatManuscriptTxt KDoc),
 * 전체 출력을 문자열 그대로 못 박는다 — 어떤 변경이든 이 테스트가 의도적 결정을 강제한다. */
class ManuscriptTxtTest {

    private fun chapter(index: Int, body: String, title: String? = null) = ChapterEntity(
        chapterIndex = index,
        label = "${index + 1}화",
        title = title,
        body = body,
        ingested = true,
    )

    @Test
    fun formatsHeadersFromChapterIndexWithBlankLineSeparation() {
        val text = formatManuscriptTxt(
            listOf(
                chapter(0, "옛 마을에 도착했다."),
                chapter(1, "폐가의 문이 열려 있었다."),
            )
        )
        assertEquals(
            "제1화\n\n옛 마을에 도착했다.\n\n\n제2화\n\n폐가의 문이 열려 있었다.\n",
            text,
        )
    }

    @Test
    fun appendsTitleToHeadingWhenPresent() {
        val text = formatManuscriptTxt(listOf(chapter(0, "본문.", title = "과거의 흔적")))
        assertEquals("제1화 과거의 흔적\n\n본문.\n", text)
    }

    @Test
    fun skipsBlankChaptersAndTrimsTrailingWhitespace() {
        val text = formatManuscriptTxt(
            listOf(
                chapter(0, "첫 화 본문.\n\n"),
                chapter(1, "   \n"),
                chapter(2, "셋째 화 본문."),
            )
        )
        assertEquals("제1화\n\n첫 화 본문.\n\n\n제3화\n\n셋째 화 본문.\n", text)
    }
}
