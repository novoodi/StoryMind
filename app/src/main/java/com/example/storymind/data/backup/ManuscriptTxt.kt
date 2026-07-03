package com.example.storymind.data.backup

import com.example.storymind.data.db.ChapterEntity

/**
 * 전체 원고를 하나의 일반 텍스트로 직렬화한다. 순수 함수로 둔 이유: 원고는 이 앱의 가장
 * 소중한 자산이라(규칙 1) 내보내기 형식이 조용히 뒤틀리면 안 되고, 파일 I/O와 분리돼 있어야
 * JVM 유닛 테스트로 형식을 못 박을 수 있다.
 *
 * 형식: 화마다 "제N화" 헤더(제목이 있으면 같은 줄에 이어 붙임), 빈 줄, 본문. 화 사이는
 * 빈 줄 두 개로 구분해 이어 읽을 때 경계가 보이게 한다. 빈 본문 화는 건너뛴다 — 저장만
 * 되고 아직 쓰이지 않은 화는 원고가 아니다(리플레이가 빈 화를 건너뛰는 것과 같은 기준).
 * 헤더의 N은 label 문자열이 아니라 chapterIndex에서 만든다: label은 UI 표기(현재 "N화")라
 * 바뀔 수 있지만, 내보내기 형식은 한 번 배포되면 사용자의 파일에 남는 계약이다.
 */
fun formatManuscriptTxt(chapters: List<ChapterEntity>): String {
    val sections = chapters
        .filter { it.body.isNotBlank() }
        .map { chapter ->
            val heading = buildString {
                append("제${chapter.chapterIndex + 1}화")
                chapter.title?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
            }
            "$heading\n\n${chapter.body.trimEnd()}"
        }
    return sections.joinToString(separator = "\n\n\n", postfix = "\n")
}
