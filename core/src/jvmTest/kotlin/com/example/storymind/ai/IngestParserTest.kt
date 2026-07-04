package com.example.storymind.ai

import com.example.storymind.ui.components.SmBadgeType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestParserTest {

    private val lenientJson = Json { isLenient = true }

    @Test
    fun `strips thought block and parses the JSON that follows`() {
        val raw = """
            <|channel>thought
            이 원고에는 지우와 민준이 카페에서 만난다. 엔티티를 추려보자...
            <channel|>
            {
              "chapter_summary": "지우와 민준이 카페에서 처음 만났다.",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"}
              ],
              "relations": []
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals("지우와 민준이 카페에서 처음 만났다.", result.chapterSummary)
        assertEquals(1, result.entities.size)
        assertEquals("jiwoo", result.entities[0].id)
        assertEquals(SmBadgeType.Character, result.entities[0].type)
    }

    @Test
    fun `strips a markdown json code fence around an otherwise valid JSON object`() {
        // On-device evidence (docs/constrained-decoding-spike.md, SM-S928N, 2026-07-02): Gemma
        // wraps its JSON response in a ```json fence even when the prompt explicitly forbids it.
        val raw = """
            ```json
            {
              "chapter_summary": "지우와 민준이 카페에서 처음 만났다.",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"}
              ],
              "relations": []
            }
            ```
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals("지우와 민준이 카페에서 처음 만났다.", result.chapterSummary)
        assertEquals(1, result.entities.size)
        assertEquals("jiwoo", result.entities[0].id)
        assertEquals(SmBadgeType.Character, result.entities[0].type)
    }

    @Test
    fun `cleans trailing commas before parsing`() {
        val raw = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"cafe","type":"place","name":"카페","desc":"장소",},
              ],
              "relations": [
                {"from":"jiwoo","to":"cafe",},
              ],
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(1, result.entities.size)
        assertEquals("cafe", result.entities[0].id)
        assertEquals(SmBadgeType.Place, result.entities[0].type)
        assertEquals(1, result.relations.size)
        assertEquals("jiwoo", result.relations[0].from)
        assertEquals("cafe", result.relations[0].to)
    }

    @Test
    fun `does not corrupt a valid response whose values contain a colon followed by a non-space character`() {
        // Regression (2026-07 코드 감사): the repair chain used to run unconditionally, and
        // MISSING_VALUE_OPEN_QUOTE_REGEX fires on any `:` immediately followed by a non-space
        // character — a shape every clock time / score / ratio inside a desc or summary has
        // ("3:00", "3:2"). Repairing this *valid* response turned it into unparseable JSON, so a
        // chapter whose content kept re-producing the pattern failed every retry deterministically.
        // The strict-parse-first gate must hand this response through byte-identical.
        val raw = """
            {
              "chapter_summary": "율은 매일 3:00에 일어나 하루를 시작했다.",
              "entities": [
                {"id":"율","type":"character","name":"율","desc":"시간 3:00을 반드시 지키는 인물"}
              ],
              "relations": []
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals("율은 매일 3:00에 일어나 하루를 시작했다.", result.chapterSummary)
        assertEquals("시간 3:00을 반드시 지키는 인물", result.entities.single().desc)
    }

    @Test
    fun `does not insert commas into a valid desc containing quoted dialogue`() {
        // Same class as the colon regression above, for MISSING_COMMA_REGEX: a `"` followed by
        // whitespace and another `"` *inside a string value* (escaped dialogue quotes) matches the
        // missing-comma pattern, which used to silently splice a comma into author-visible text.
        val raw = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"율","type":"character","name":"율","desc":"그는 \"안녕\" \"잘가\" 라고 말하는 버릇이 있다"}
              ],
              "relations": []
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals("그는 \"안녕\" \"잘가\" 라고 말하는 버릇이 있다", result.entities.single().desc)
    }

    @Test
    fun `falls back to empty result when no JSON object is present`() {
        val raw = "이건 그냥 모델이 JSON을 만들다 만 텍스트입니다 { \"entities\": [ 이상하게 끊김"

        val result = IngestParser.parse(raw)

        assertEquals("", result.chapterSummary)
        assertTrue(result.entities.isEmpty())
        assertTrue(result.relations.isEmpty())
    }

    @Test
    fun `falls back to empty result when the extracted JSON block is malformed beyond comma repair`() {
        val raw = """
            잡담 텍스트
            { "chapter_summary" "요약", "entities": [] }
            마무리 텍스트
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals("", result.chapterSummary)
        assertTrue(result.entities.isEmpty())
        assertTrue(result.relations.isEmpty())
    }

    @Test
    fun `inserts a comma missing between sibling object members`() {
        val raw = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"yul","type":"character","name":"율","desc":"주인공"},
                {
                  "id": "꿈 기록부"
                  "type": "item",
                  "name": "꿈 기록부",
                  "desc": "중요한 자료"
                }
              ],
              "relations": [
                {"from":"yul","to":"꿈 기록부"}
              ]
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(2, result.entities.size)
        assertEquals("꿈 기록부", result.entities[1].id)
        assertEquals(SmBadgeType.Item, result.entities[1].type)
        assertEquals(1, result.relations.size)
    }

    @Test
    fun `inserts a comma missing between array elements`() {
        val raw = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"a","type":"character","name":"A","desc":"a"}
                {"id":"b","type":"character","name":"B","desc":"b"}
              ],
              "relations": []
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(2, result.entities.size)
    }

    @Test
    fun `collapses a stray quote-comma fragment between two real object members`() {
        val raw = """
            {
              "chapter_summary": "요약",
              "entities": [
                {
                  "id": "엘라시움",      ",
                  "type": "place",      "name": "엘라시움",      ",
                  "desc": "장소 설명"
                }
              ],
              "relations": []
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(1, result.entities.size)
        assertEquals("엘라시움", result.entities[0].id)
        assertEquals("장소 설명", result.entities[0].desc)
    }

    @Test
    fun `skips entities with a type outside the four allowed values`() {
        val raw = """
            {
              "chapter_summary": "요약",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"},
                {"id":"mystery","type":"weather","name":"날씨","desc":"알 수 없는 타입"}
              ],
              "relations": []
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(1, result.entities.size)
        assertEquals("jiwoo", result.entities[0].id)
    }

    @Test
    fun `fixpoint repair recovers a missing comma that a single pass turns into a stray quote-comma`() {
        // On-device evidence (2화 인제스트 실패, 2026-07): Gemma dropped the comma after "type"'s
        // value AND left a stray `",` fragment in the same spot — `"character"    ",`. A single
        // repair pass can't fix this: collapseStrayQuoteCommas doesn't match yet (no comma sits
        // before the stray quote), but insertMissingCommas then manufactures exactly that comma,
        // recreating the stray-quote-comma shape one pass too late. See IngestParser.repairToFixpoint.
        val raw = """
            {
              "chapter_summary": "진성이 다시 등장한다.",
              "entities": [
                {
                  "id": "kimjinseong",
                  "type": "character"    ",
                  "name": "김진성",
                  "desc": "다시 나타난 인물"
                }
              ],
              "relations": []
            }
        """.trimIndent()

        val singlePassResult = IngestParser.singleRepairPass(raw)
        val singlePassParses = try {
            lenientJson.parseToJsonElement(singlePassResult)
            true
        } catch (e: SerializationException) {
            false
        }
        assertFalse(
            "a single repair pass was expected to leave the stray quote-comma artifact behind: $singlePassResult",
            singlePassParses,
        )

        val result = IngestParser.parse(raw)

        assertEquals(1, result.entities.size)
        assertEquals("kimjinseong", result.entities[0].id)
        assertEquals(SmBadgeType.Character, result.entities[0].type)
        assertEquals("김진성", result.entities[0].name)
    }

    @Test
    fun `repairs a key that lost its opening quote right after a comma`() {
        // Actual failure fixture (1화 인제스트, 2026-07): attempt 1's raw response, captured from
        // logcat after repairToFixpoint already stabilized on it and json.parseToJsonElement still
        // rejected it — "type": "character",name": "엄마" is missing the opening quote on `name`.
        // Before MISSING_KEY_OPEN_QUOTE_REGEX existed, this made attempt 1 fail outright and
        // IngestService had to spend a second (higher-temperature) generation recovering the
        // chapter's wiki data.
        val raw = """
            {
              "chapter_summary": "율과 진성은 지민에 대한 복잡한 감정과 과거의 행동에 대해 이야기하며 꿈을 꾸는 과정을 공유한다.",
              "entities": [
                {
                  "id": "율",
                  "type": "character",
                  "name": "율",
                  "desc": "침대에서 일어나 대화를 주도하며 상황을 주도하고 꿈을 꾸게 하는 인물"
                },
                {
                  "id": "진성",
                  "type": "character", "name": "진성",
                  "desc": "지민과의 관계에 대해 고백을 하며 죄책감을 느끼는 인물"
                },
                {
                  "id": "엄마",
                  "type": "character",name": "엄마",
                  "desc": "진성의 방에서 신음 소리를 듣고 들어온 인물"
                }
              ],
              "relations": [
                {"from": "율", "to": "진성"},
                {"from": "진성", "to": "엄마"}
              ]
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(3, result.entities.size)
        val mom = result.entities.single { it.id == "엄마" }
        assertEquals(SmBadgeType.Character, mom.type)
        assertEquals("엄마", mom.name)
        assertEquals("진성의 방에서 신음 소리를 듣고 들어온 인물", mom.desc)
        assertEquals(2, result.relations.size)
    }

    @Test
    fun `repairs a value that lost its opening quote right after a colon`() {
        // Actual failure fixture (2화 인제스트, 2026-07 — the intentionally re-saved duplicate of
        // 1화 used to test replay): attempt 1's raw response, same "stabilized but still rejected"
        // provenance as the key-quote fixture above. "id":지민" and "type":character" are each
        // missing the opening quote right after the colon. The "엄마마" typo in the last relation
        // is deliberately left as-is — it's a duplicated-character id mismatch (엄마마 vs the
        // entity's real id 엄마), not a JSON syntax problem, so it's IngestService's unresolved-
        // relation reporting to catch (see PartialDropRecorder), not something this parser repairs.
        val raw = """
            {
              "chapter_summary": "율은 진성과 지민에 대한 이야기를 나누며 꿈을 꾸고, 이후 진성이 지민의 괴롭힘에 대한 상황을 만들어주는 역할을 하며 꿈을 꾸었다.",
              "entities": [
                {
                  "id": "율",
                  "type": "character",
                  "name": "율",
                  "desc": "과거의 행동을 앞으로의 선택으로 바꿀 수 있다고 조언하는 인물"
                },
                {
                  "id": "진성",
                  "type": "character",
                  "name": "진성",
                  "desc": "지민과의 관계에 대한 고백을 고백하며 죄책감을 느끼는 인물"
                },
                {
                  "id":지민",
                  "type":character",
                  "name": "지민",
                  "desc": "괴롭힘을 당한 것으로 추정되는 인물"
                },
                {
                  "id": "엄마",
                  "type": "character",
                  "name": "엄마",
                  "desc": "아들을 흔들며 진성을 걱정하는 인물"
                }
              ],
              "relations": [
                {"from": "율", "to": "진성"},
                {"from": "진성", "to": "지민"},
                {"from": "진성", "to": "엄마마"}
              ]
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(4, result.entities.size)
        val jimin = result.entities.single { it.id == "지민" }
        assertEquals(SmBadgeType.Character, jimin.type)
        assertEquals("지민", jimin.name)
        assertEquals(3, result.relations.size)
        // The typo'd id passes straight through the parser unresolved, as documented above.
        assertEquals("엄마마", result.relations[2].to)
    }

    @Test
    fun `repairs a lone orphan comma left over after a value with nothing else on the line`() {
        // Actual failure fixture (v2 run 1/5 of IdConsistencyPromptSmokeTest's predecessor,
        // 2026-07): a value immediately followed by an isolated comma on its own line before the
        // next real key — `"id": "율",\n    ,\n    "type": "character",`. Before
        // ORPHAN_COMMA_REGEX existed, this made the attempt fail outright (a different shape than
        // the stray-quote-comma case: no quote sits between the two commas here, just whitespace).
        val raw = """
            {
              "chapter_summary": "율과 진성은 과거의 행동에 대한 갈등을 겪으며, 꿈을 통해 지민의 괴롭힘 상황을 상상으로 만들어내고 이를 통해 감정화한다.",
              "entities": [
                {
                  "id": "율",
                  ,
                  "type": "character",

                  "name": "율",
                  "desc": "과거의 행동을 긍정적으로 변화시키고자 노력하는 인물"
                },
                {
                  "id": "진성",
                  ,
                  "type": "character",
                  "name": "진성",
                  "desc": "과거의 행동에 대해 죄책감을 느끼며 지민에게 연락을 시도하는 인물"
                },
                {
                  "id": "엄마",
                  ,
                  "type": "character",
                  "name": "엄마",
                  "desc": "진성의 어머니로, 지민의 상황에 대해 걱정하며 진성을 위로하는 인물"
                }
              ],
              "relations": [
                {
                  "from": "율",
                  ,
                  "to": "진성"
                }
              ]
            }
        """.trimIndent()

        val result = IngestParser.parse(raw)

        assertEquals(3, result.entities.size)
        val yul = result.entities.single { it.id == "율" }
        assertEquals(SmBadgeType.Character, yul.type)
        assertEquals("율", yul.name)
        assertEquals(1, result.relations.size)
        assertEquals("율", result.relations[0].from)
        assertEquals("진성", result.relations[0].to)
    }
}