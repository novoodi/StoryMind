package com.example.storymind.data.backup

import com.example.storymind.data.db.ChapterEntity

/**
 * 전체 원고를 하나의 Markdown 문서로 직렬화한다. [formatManuscriptTxt]와 같은 이유로 순수
 * 함수다: 원고는 이 앱의 가장 소중한 자산이라(규칙 1) 내보내기 형식이 조용히 뒤틀리면 안 되고,
 * 파일 I/O와 분리돼 있어야 JVM 유닛 테스트로 형식을 못 박을 수 있다.
 *
 * 형식: 화마다 `# 제N화` ATX 헤딩(제목이 있으면 같은 줄에 이어 붙임), 빈 줄, 본문. 화 사이는
 * 빈 줄 하나로만 구분한다 — TXT와 달리 헤딩 자체가 경계를 만들고, Markdown에서 헤딩은 바로 앞에
 * 빈 줄 하나면 충분히 헤딩으로 파싱된다(빈 줄 둘은 불필요). 빈 본문 화는 건너뛴다 — 저장만 되고
 * 아직 쓰이지 않은 화는 원고가 아니다(TXT·리플레이와 같은 기준). 헤딩의 N은 label 문자열이
 * 아니라 chapterIndex에서 만든다: label은 UI 표기(현재 "N화")라 바뀔 수 있지만, 내보내기 형식은
 * 한 번 배포되면 사용자의 파일에 남는 계약이다.
 *
 * 본문은 작가가 쓴 그대로(trimEnd만) 내보낸다 — 문단을 재배열하거나 Markdown 특수문자를
 * 이스케이프하지 않는다. "Markdown으로 내보내기"는 본문이 Markdown으로 렌더된다는 뜻이고,
 * 원고를 변형하지 않는 것이 형식을 왜곡하지 않는다는 규칙 1의 정신에 맞는다.
 */
fun formatManuscriptMarkdown(chapters: List<ChapterEntity>): String {
    val sections = chapters
        .filter { it.body.isNotBlank() }
        .map { chapter ->
            val heading = buildString {
                append("# 제").append(chapter.chapterIndex + 1).append("화")
                chapter.title?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
            }
            "$heading\n\n${chapter.body.trimEnd()}"
        }
    return sections.joinToString(separator = "\n\n", postfix = "\n")
}
