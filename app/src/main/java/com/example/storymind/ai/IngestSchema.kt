package com.example.storymind.ai

import com.example.storymind.data.WikiEntry
import com.example.storymind.ui.components.SmBadgeType

/**
 * Builds the prompt sent to the on-device Gemma model to turn a chapter's manuscript text
 * into structured wiki data (entities + relations + summary).
 *
 * The model's job stops at extraction: entities, relations between them, short descriptions,
 * and a chapter summary. Node coordinates and orphan status are computed deterministically by
 * [IngestService] afterwards, never by the model.
 */
object IngestSchema {

    /** Bumped whenever [buildIngestPrompt]'s instructions change meaningfully, so ingest results
     * stored via [com.example.storymind.data.db.ChapterEntity.ingestPromptVersion] can be told
     * apart from output produced under an older prompt.
     *
     * v1 -> v2: not a wording change — a `trimIndent()` bug fix. Before the fix, interpolating
     * the multi-line `existingBlock`/`manuscript` into the template before calling `trimIndent()`
     * dragged its computed minimum indentation to 0 for the whole string (see [IngestSchemaTest]
     * for the regression coverage, not `docs/constrained-decoding-spike.md` — that spike is about
     * a separate constrained-decoding investigation), so every instruction line still carried its
     * 12-space source indent whenever a chapter had `existingEntities` or more than one paragraph
     * — i.e. essentially every chapter past the first. Chapters stamped with v1 were ingested from
     * that indentation-polluted prompt, not today's clean one.
     *
     * v2 -> v3: added an id-consistency rule bullet ("entities에서 정한 id는 relations에서도 동일한
     * 문자열로만 참조한다"), an experimental attempt to reduce at the source what
     * [IngestService]/[PartialDropRecorder] can only detect after the fact — the 2026-07 1화
     * incident, where Gemma defined "진성" as an entity but then wrote two of its own relations
     * against the truncated id "성". [IdConsistencyPromptSmokeTest] measured it on the actual 1화
     * manuscript (5 generations per variant): v2 (no rule) mismatched 2/5, v3 (with the rule)
     * mismatched **4/5** — no improvement, and directionally worse in that sample. See
     * `docs/entity-relation-id-mismatch-incident.md`'s "후속 조치" section.
     *
     * v3 -> v4: withdrew that rule bullet, per the v3 measurement above. Reusing the v3 number for
     * this would erase the fact that a real prompt (the one actually measured) once existed at
     * that version — v4 is a *different* prompt (the rule removed), not v2 restored-and-renumbered,
     * so it gets its own number even though its text is byte-for-byte what v2 was. The
     * entities-relations id-consistency problem itself isn't considered solved — it's now handled
     * post-hoc instead of at the prompt: [IngestService.generateResult] retries an unresolved
     * relation like a soft parse failure (shares the existing attempt budget, accepts on the last
     * attempt), and [IngestService.shadowMatchDescription] records what a conservative recovery
     * would have matched, in shadow mode only, as data toward a future merge-time fix. */
    const val PROMPT_VERSION: Int = 4

    /** Placeholder tokens (plain text, not `$`-based) inserted into the template before
     * [String.trimIndent] runs and swapped for the real multi-line blocks after. Interpolating
     * [existingBlock]/[manuscript] directly into the triple-quoted literal before trimming — the
     * previous approach — let their own zero-indent lines drag trimIndent's computed minimum
     * indentation down to 0 for the *entire* string, so the template's own indentation never got
     * stripped. Single-line interpolations like `chapterTitle` don't have this problem, since
     * they can't introduce a new zero-indent line. */
    private const val EXISTING_PLACEHOLDER = "%%EXISTING_BLOCK%%"
    private const val MANUSCRIPT_PLACEHOLDER = "%%MANUSCRIPT%%"

    fun buildIngestPrompt(
        chapterTitle: String,
        paragraphs: List<String>,
        existingEntities: List<WikiEntry> = emptyList(),
    ): String {
        val manuscript = paragraphs.joinToString("\n")
        val existingBlock = if (existingEntities.isEmpty()) {
            ""
        } else {
            buildString {
                appendLine()
                appendLine("이전 화에 이미 등장한 엔티티 목록 (이번 화에도 다시 등장하면 아래 id와 name을 그대로 재사용할 것. 새로운 id를 만들지 말 것):")
                existingEntities.forEach { entry ->
                    appendLine("- id:${entry.id} type:${entry.type.toSchemaType()} name:${entry.name}")
                }
            }
        }
        val template = """
            당신은 소설 원고를 분석해서 위키 데이터를 추출하는 어시스턴트입니다.

            할 일:
            - 원고에 등장하는 엔티티(인물/장소/소품/사건)를 찾는다.
            - 엔티티 사이의 관계(엣지)를 찾는다.
            - 각 엔티티에 대한 한 줄 설명을 쓴다.
            - 이 화(chapter) 전체에 대한 3~4문장 요약을 쓴다.

            반드시 지킬 규칙:
            - entities[].type 값은 "character", "place", "item", "event" 4가지 중 하나만 사용한다. 그 외 값은 절대 쓰지 않는다.
            - entities[].desc는 절대 비워두거나 생략하지 않는다. 모든 엔티티는 반드시 "desc" 키와 그 값(한 줄 설명, 최소 5자 이상)을 가져야 한다. desc가 없는 엔티티는 잘못된 출력이다.
            - 노드의 좌표(x, y) 같은 위치 정보는 만들지 않는다. 이 프롬프트가 다루는 범위가 아니다.
            - 어떤 엔티티가 다른 엔티티와 연결되어 있는지 여부(고아/미연결 판정)를 스스로 판단해서 표시하지 않는다. 관계(relations)만 사실대로 나열하면 된다.
            - 이 작업은 단순 추출이다. 분석 과정이나 생각 과정을 출력하지 말고, 추론 없이 곧바로 최종 JSON만 출력한다.
            - 최종 출력은 아래 스키마에 맞는 JSON 하나뿐이어야 한다. JSON 앞뒤로 설명, 인사말, 마크다운 코드펜스 등 어떤 텍스트도 남기지 않는다.
            $EXISTING_PLACEHOLDER
            출력 JSON 스키마 (모든 필드 필수, desc 생략 금지):
            {
              "chapter_summary": "이 화의 3~4문장 요약",
              "entities": [
                {"id":"고유영문/한글 식별자","type":"character|place|item|event","name":"표시 이름","desc":"한 줄 설명 (필수, 비워두지 말 것)"}
              ],
              "relations": [
                {"from":"엔티티 id","to":"엔티티 id"}
              ]
            }

            entities 항목 올바른 예시 (모든 필드가 채워져 있어야 한다):
            {"id":"수인","type":"character","name":"수인","desc":"과거의 기억을 잃은 채 마을에 도착한 떠돌이"}

            분석할 원고
            화 제목: $chapterTitle
            $MANUSCRIPT_PLACEHOLDER

            위 원고를 분석해서 위 스키마에 맞는 JSON만 출력하라.
        """.trimIndent()

        return template
            .replace(EXISTING_PLACEHOLDER, existingBlock)
            .replace(MANUSCRIPT_PLACEHOLDER, manuscript)
    }

    private fun SmBadgeType.toSchemaType(): String = when (this) {
        SmBadgeType.Character -> "character"
        SmBadgeType.Place -> "place"
        SmBadgeType.Item -> "item"
        SmBadgeType.Event -> "event"
        else -> "character"
    }
}
