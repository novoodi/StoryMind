package com.example.storymind.ai

/**
 * Builds the prompt sent to the on-device Gemma model to turn a chapter's manuscript text
 * into structured wiki data (entities + relations + summary).
 *
 * The model's job stops at extraction: entities, relations between them, short descriptions,
 * and a chapter summary. Node coordinates and orphan status are computed deterministically by
 * [IngestService] afterwards, never by the model.
 */
object IngestSchema {

    fun buildIngestPrompt(chapterTitle: String, paragraphs: List<String>): String {
        val manuscript = paragraphs.joinToString("\n")
        return """
            <|think|>
            당신은 소설 원고를 분석해서 위키 데이터를 추출하는 어시스턴트입니다.

            할 일:
            - 원고에 등장하는 엔티티(인물/장소/소품/사건)를 찾는다.
            - 엔티티 사이의 관계(엣지)를 찾는다.
            - 각 엔티티에 대한 한 줄 설명을 쓴다.
            - 이 화(chapter) 전체에 대한 3~4문장 요약을 쓴다.

            반드시 지킬 규칙:
            - entities[].type 값은 "character", "place", "item", "event" 4가지 중 하나만 사용한다. 그 외 값은 절대 쓰지 않는다.
            - 노드의 좌표(x, y) 같은 위치 정보는 만들지 않는다. 이 프롬프트가 다루는 범위가 아니다.
            - 어떤 엔티티가 다른 엔티티와 연결되어 있는지 여부(고아/미연결 판정)를 스스로 판단해서 표시하지 않는다. 관계(relations)만 사실대로 나열하면 된다.
            - 최종 출력은 아래 스키마에 맞는 JSON 하나뿐이어야 한다. JSON 앞뒤로 설명, 인사말, 마크다운 코드펜스 등 어떤 텍스트도 남기지 않는다.

            출력 JSON 스키마:
            {
              "chapter_summary": "이 화의 3~4문장 요약",
              "entities": [
                {"id":"고유영문/한글 식별자","type":"character|place|item|event","name":"표시 이름","desc":"한 줄 설명"}
              ],
              "relations": [
                {"from":"엔티티 id","to":"엔티티 id"}
              ]
            }

            분석할 원고
            화 제목: $chapterTitle
            $manuscript

            위 원고를 분석해서 위 스키마에 맞는 JSON만 출력하라.
        """.trimIndent()
    }
}
