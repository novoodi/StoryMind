package com.example.storymind.ai

/**
 * Builds the prompt sent to the on-device Gemma model to check whether this chapter's manuscript
 * stays consistent with an entity's established wiki history (a "setting conflict" lint pass,
 * separate from and downstream of the ingest pipeline in [IngestSchema]).
 *
 * Unlike ingest, this prompt is built to turn thinking mode on ([THINK_TOKEN] up front): telling
 * conflict from natural development from earlier established facts is a judgment call, not
 * extraction, and was expected to benefit from the model reasoning over the wiki history before
 * answering. In practice, `docs/lint-golden-baseline.md`'s v3 measurement never observed a
 * `<|channel>thought ... <channel|>` block — 0/15 runs across five fixtures — so whether
 * [THINK_TOKEN] actually activates thinking mode for this model/LiteRT-LM combination is an open
 * question, not a confirmed premise; judgment quality was 15/15 correct regardless. [LintParser]
 * still strips that block unconditionally rather than treating it as an occasional repair, since a
 * false negative here (leaving a thought block in what's parsed as the JSON answer) is worse than
 * the strip being a no-op on every call so far.
 *
 * [THINK_TOKEN] must land as the literal first character of the built prompt for thinking mode to
 * reliably activate — see [buildLintPrompt]'s placeholder-based construction, which exists
 * specifically so [candidatesBlock]/[manuscript] can't push it out of position.
 */
object LintSchema {

    /** Prepended to every lint prompt to switch the model into thinking mode. */
    private const val THINK_TOKEN = "<|think|>"

    /** Bumped whenever [buildLintPrompt]'s instructions change meaningfully, so a future
     * provenance stamp on lint results (mirroring [IngestSchema.PROMPT_VERSION] on
     * [com.example.storymind.data.db.ChapterEntity.ingestPromptVersion]) can tell output apart
     * from an older prompt version. Not stamped anywhere yet — v1 lint has no persistence.
     *
     * v1 -> v2: fixes a reproducible misjudgment found in `docs/lint-golden-baseline.md`'s golden
     * fixture C ("이력에 조짐 없음 + 원고 내 계기 서술" — no wiki foreshadowing, but the chapter
     * itself states an urgent motive). All 3/3 runs called it `conflict` instead of the intended
     * `development`, quoting the in-chapter motive in the reason text while still misclassifying
     * it — the model was pattern-matching the conflict example's surface shape ("물을 무서워하는
     * 인물이... 망설임 없이 물에 뛰어든다"), which was nearly identical to the in-chapter
     * development example, rather than the *presence or absence of a stated motive* the two
     * verdicts actually hinge on. v2 rewrites both examples as a minimal contrasting pair (conflict
     * explicitly states neither history nor chapter gives a motive; development's in-chapter
     * example is separated out) plus one explicit contrast sentence tying them together.
     *
     * v2 -> v3: fixes a regression the v2 fix introduced (fixture D in
     * `docs/lint-golden-baseline.md`'s "v2 재측정" section). D's chapter is entirely unrelated to
     * the tracked entity's history (sword training vs. an ordinary meal scene) and should produce
     * no finding at all — but v2 made the model emit an `ambiguous` finding for it every time,
     * reasoning that there wasn't "enough information" to judge consistency. The root cause: v1/v2
     * defined ambiguous as covering *both* "genuinely can't tell conflict from development" *and*
     * "insufficient information," and the model folded "this scene doesn't touch the tracked trait
     * at all" into the latter. v3 restructures judgment into two explicit stages — (1) an inclusion
     * gate that asks whether the chapter even tests the entity's established setting at all, with
     * irrelevance leading to *exclusion from findings*, not a verdict; (2) verdict assignment,
     * which only runs for entities that passed the gate — and narrows ambiguous's definition to
     * drop the "insufficient information" clause entirely, since that was the exact wording the
     * model was generalizing from to cover irrelevant scenes too. */
    const val LINT_PROMPT_VERSION: Int = 3

    /** Placeholder tokens (plain text, not `$`-based) inserted into the template before
     * [String.trimIndent] runs and swapped for the real multi-line blocks after — see
     * [com.example.storymind.ai.IngestSchema]'s identical fix for why: interpolating a multi-line
     * block directly into the triple-quoted literal before trimming lets its own zero-indent
     * lines drag trimIndent's computed minimum indentation down to 0 for the whole string, so
     * none of the template's own indentation gets stripped — and here that would also push
     * [THINK_TOKEN] off the first line. */
    private const val CANDIDATES_PLACEHOLDER = "%%CANDIDATES_BLOCK%%"
    private const val MANUSCRIPT_PLACEHOLDER = "%%MANUSCRIPT%%"

    fun buildLintPrompt(
        chapterLabel: String,
        paragraphs: List<String>,
        candidates: List<LintCandidate>,
    ): String {
        val manuscript = paragraphs.joinToString("\n")
        val candidatesBlock = buildString {
            candidates.forEach { candidate ->
                appendLine("- id:${candidate.entityId} name:${candidate.entityName}")
                candidate.descHistory.forEach { entry -> appendLine("  $entry") }
            }
        }

        val template = """
            $THINK_TOKEN
            당신은 소설 원고가 위키 설정 이력과 일관되는지 검증하는 어시스턴트입니다. 일관되면
            일관되다고, 어긋나면 어긋난다고 판정하는 것이 임무이며, 문제를 찾아내는 것 자체가
            목표가 아닙니다.

            할 일:
            - 아래 "검사 대상 엔티티" 각각에 대해 두 단계로 판단한다.
            - 1단계(포함 게이트): 이번 화 원고가 그 엔티티의 기존 설정을 시험하거나 건드리는
              지점이 있는지 먼저 확인한다. 없으면 그 엔티티는 findings에서 완전히 제외한다.
              판단할 정보가 부족한 것과 무관한 것은 다르다 — 무관하면 ambiguous가 아니라
              제외다. 예: 이력이 "검술 수련 중이다"인 인물이 이번 화에서 검술과 무관한 식사
              장면에만 등장하면 findings에 포함하지 않는다.
            - 2단계(verdict): 1단계를 통과한 엔티티에 대해서만 아래 conflict/development/
              ambiguous 중 하나로 판정한다. 1단계를 통과하지 못한 엔티티는 2단계로 넘어가지
              않는다 — findings에 아예 등장하지 않아야 한다.

            절대 하지 말 것:
            - 원고의 문장력, 전개 속도, 문체 등을 창작적으로 평가하지 않는다. 오직 설정
              일관성만 검사한다.
            - 좌표, 새로운 엔티티, 새로운 관계 등을 추출하지 않는다. 이 작업의 범위가 아니다.
            - 검사 대상 목록에 없는 엔티티에 대해서는 판정을 만들지 않는다.

            verdict 판정 기준 (셋 중 하나):
            - "conflict": 같은 시점 또는 그 이후 시점에서, 위키 이력에 기록된 사실과 이번 화의
              서술이 별다른 경과 설명 없이 논리적으로 양립할 수 없는 경우. 예: 이력에 "물을
              극도로 무서워한다"는 인물이, 극복 서술이 이력에 없고 이번 화 원고에도 아무런
              계기나 심경 변화 서술이 없는데 태연히 호수에 다이빙한다.
              conflict는 이력에도 원고에도 아무런 경과·계기 서술이 없는 경우에만 해당한다.
            - "development": 시간이 지나며 자연스럽게 일어날 수 있는 변화(성장, 변심, 극복,
              상처, 관계 변화 등). 위키 이력을 화 순서대로 읽었을 때 그 변화로 이어지는 흐름이나
              조짐이 있으면 development로 판정한다. 예: 위키 이력이 "1화: 물을 무서워한다" →
              "3화: 수영 연습을 시작했다"로 이어지고, 이번 화에서 물에 들어간다면 development.
              위키 이력에 조짐이 없더라도, 이번 화 원고 자체가 변화의 계기나 동기를 서술하고
              있다면(절박한 상황, 심경 변화의 묘사 등) development로 판정한다. 예: 이력에는
              "물을 무서워한다"뿐이지만, 이번 화에서 동생이 물에 빠지는 것을 보고 절박함에
              몸이 먼저 움직여 뛰어든다 — 원고가 계기와 심경을 서술하므로 development.
              같은 행동(물에 뛰어듦)이라도 계기 서술이 원고나 이력 어디에도 없으면 conflict,
              어느 한쪽에라도 있으면 development다.
            - "ambiguous": 원고가 그 엔티티의 설정을 시험하는 지점은 분명히 있으나, 그것이
              conflict인지 development인지 근거만으로 확신할 수 없는 경우. 설정을 시험하는
              지점 자체가 없다면 이는 ambiguous가 아니라 1단계에서 이미 제외됐어야 한다.

            검사 대상 엔티티와 위키 이력 (화 순서대로 누적됨):
            $CANDIDATES_PLACEHOLDER
            분석할 원고
            화 제목: $chapterLabel
            $MANUSCRIPT_PLACEHOLDER

            출력 규칙:
            - 분석 과정이나 생각은 생각 과정에만 담고, 최종 답으로는 아래 스키마에 맞는 JSON
              하나만 출력한다. JSON 앞뒤로 설명, 인사말, 마크다운 코드펜스 등 어떤 텍스트도
              남기지 않는다.
            - 결과에 포함시킬 항목이 없으면 findings는 빈 배열이어야 한다.

            출력 JSON 스키마 (모든 필드 필수):
            {
              "findings": [
                {
                  "entity_id": "검사 대상 목록의 id를 그대로 사용",
                  "verdict": "conflict|development|ambiguous",
                  "chapter_evidence": "이번 화 원고에서 판정의 근거가 된 서술 요약",
                  "wiki_evidence": "위키 이력에서 판정의 근거가 된 부분 요약",
                  "reason": "판정 이유 (한국어로 간결하게)"
                }
              ]
            }
        """.trimIndent()

        return template
            .replace(CANDIDATES_PLACEHOLDER, candidatesBlock)
            .replace(MANUSCRIPT_PLACEHOLDER, manuscript)
    }
}
