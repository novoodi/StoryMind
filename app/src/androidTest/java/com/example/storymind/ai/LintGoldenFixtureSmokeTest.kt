package com.example.storymind.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.storymind.platform.IngestEngineProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * On-device quality *baseline*, not a correctness gate, for the lint prompt
 * ([LintSchema]/[LintParser]/[LintService]). A small on-device model's conflict/development/
 * ambiguous judgment is inherently non-deterministic, so this test only hard-asserts
 * [LintResult.succeeded] (the model produced parseable JSON) across every run — never a specific
 * [LintVerdict], which would make the suite flaky for no real signal. Everything else (verdict
 * distribution, reasons, timing) is logged for a human to read afterward and turn into
 * `docs/lint-golden-baseline.md`.
 *
 * Model file required (mirrors [OnDeviceEngineSmokeTest]'s Assume-skip pattern): 15 thinking-mode
 * generations (5 fixtures x 3 repeats), each paying a cold engine reload via
 * [IngestEngineProvider.withTextEngine] the same way a real on-demand lint invocation would after
 * the ingest engine already released — expect roughly 30-50 minutes including those reloads.
 *
 * Every raw engine response (every [LintService] retry attempt, not just the final parsed one) is
 * captured via an [OnDeviceTextEngine] wrapper and logged as a head/tail-truncated dump —
 * head/tail rather than the full multi-thousand-character response, since logcat lines have a
 * practical length limit, but the tail matters as much as the head here: it's what tells a
 * truncated generation (cut off mid-JSON) apart from a well-formed response that's simply
 * unparseable for some other reason (e.g. the JSON never left the thought channel).
 */
@RunWith(AndroidJUnit4::class)
class LintGoldenFixtureSmokeTest {

    @get:Rule
    val timeout: Timeout = Timeout(90, TimeUnit.MINUTES)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun goldenFixtures_reportVerdictDistributionAndTiming() = runBlocking {
        val probe = OnDeviceEngine(context)
        assumeTrue("Model file not found at ${probe.modelPath} — skipping", probe.isModelAvailable)

        val runsByFixture = LinkedHashMap<Fixture, MutableList<RunResult>>()
        FIXTURES.forEach { runsByFixture[it] = mutableListOf() }

        // Whether each run's raw response(s) contained a `<|channel>thought` block at all — v2's
        // A_conflict responses went straight to JSON with no thought block despite THINK_TOKEN
        // being prepended, raising the question of whether thinking mode is actually activating.
        val thoughtBlockSeenPerRun = mutableListOf<Boolean>()

        // A, B, C, D, E, A, B, C, D, E, A, B, C, D, E — not three-in-a-row per fixture, so engine
        // warmup, thermal throttling, or other ordering effects don't concentrate on one fixture.
        repeat(REPEATS) { round ->
            FIXTURES.forEach { fixture ->
                val runs = runsByFixture.getValue(fixture)
                val runIndex = runs.size + 1
                Log.i(TAG, "Running ${fixture.id} (round ${round + 1}/$REPEATS, run $runIndex/$REPEATS for this fixture)...")

                val startMs = System.currentTimeMillis()
                var backend: EngineBackend? = null
                // Wraps the real engine so every attempt LintService.generateFindings makes
                // (up to MAX_ATTEMPTS=2) is captured verbatim, not just the final parsed result —
                // needed to tell a truncated generation apart from a well-formed-but-unparseable
                // one (e.g. JSON trapped inside the thought channel) for fixture A's parse failures.
                val rawResponses = mutableListOf<String>()
                val result = IngestEngineProvider.withTextEngine(context) { engine, activeBackend ->
                    backend = activeBackend
                    val capturingEngine = OnDeviceTextEngine { prompt ->
                        val raw = engine.generate(prompt)
                        rawResponses += raw
                        raw
                    }
                    LintService(capturingEngine).lint(
                        chapterLabel = fixture.chapterLabel,
                        paragraphs = fixture.paragraphs,
                        candidates = listOf(fixture.candidate),
                    )
                }
                val elapsedMs = System.currentTimeMillis() - startMs

                val run = RunResult(result, backend, elapsedMs)
                runs.add(run)
                val sawThoughtBlock = rawResponses.any { it.contains(THOUGHT_BLOCK_MARKER) }
                thoughtBlockSeenPerRun += sawThoughtBlock
                Log.i(
                    TAG,
                    "${fixture.id} run $runIndex/$REPEATS done: succeeded=${result.succeeded} " +
                        "verdict=${run.verdict()?.name ?: "none"} matchesExpected=${run.matchesExpected(fixture)} " +
                        "backend=${backend?.name ?: "unknown"} elapsedMs=$elapsedMs thoughtBlock=$sawThoughtBlock",
                )
                Log.i(TAG, "${fixture.id} run $runIndex/$REPEATS reason: ${run.reason() ?: "(no finding for this entity)"}")

                // \n replaced so each head/tail dump stays one logcat line (a raw newline would
                // otherwise split it across multiple lines, complicating later grep-based extraction).
                rawResponses.forEachIndexed { attempt, raw ->
                    val flattened = raw.replace("\n", "\\n")
                    Log.i(
                        TAG,
                        "${fixture.id} run $runIndex attempt ${attempt + 1}/${rawResponses.size} " +
                            "rawLength=${raw.length} head500=${flattened.take(500)}",
                    )
                    Log.i(
                        TAG,
                        "${fixture.id} run $runIndex attempt ${attempt + 1}/${rawResponses.size} " +
                            "tail500=${flattened.takeLast(500)}",
                    )
                }
            }
        }

        Log.i(TAG, "===== LINT GOLDEN FIXTURE REPORT (LINT_PROMPT_VERSION=${LintSchema.LINT_PROMPT_VERSION}) =====")
        val thoughtBlockCount = thoughtBlockSeenPerRun.count { it }
        Log.i(
            TAG,
            "Thought block (<|channel>thought) observed in $thoughtBlockCount/${thoughtBlockSeenPerRun.size} runs" +
                if (thoughtBlockCount == 0) {
                    " — 0/${thoughtBlockSeenPerRun.size}: THINK_TOKEN may not be activating thinking mode; needs separate investigation."
                } else {
                    ""
                },
        )
        FIXTURES.forEach { fixture ->
            val runs = runsByFixture.getValue(fixture)
            val matchCount = runs.count { it.matchesExpected(fixture) }
            val avgMs = runs.map { it.elapsedMs }.average()
            Log.i(
                TAG,
                "---- ${fixture.id}: expected=${fixture.expectedLabel()} " +
                    "matched=$matchCount/${runs.size} avgElapsedMs=${avgMs.toLong()} ----",
            )
            runs.forEachIndexed { i, run ->
                Log.i(
                    TAG,
                    "  run ${i + 1}: succeeded=${run.result.succeeded} verdict=${run.verdict()?.name ?: "none"} " +
                        "backend=${run.backend?.name} elapsedMs=${run.elapsedMs} matches=${run.matchesExpected(fixture)}",
                )
                Log.i(TAG, "    reason: ${run.reason() ?: "(no finding for this entity)"}")
            }
        }

        val allSucceeded = runsByFixture.values.flatten().all { it.result.succeeded }
        assertTrue(
            "Every run must at least produce parseable JSON (LintResult.succeeded); verdicts " +
                "themselves are NOT asserted here since a small on-device model's judgment calls " +
                "are non-deterministic. See logcat for the full report ($TAG).",
            allSucceeded,
        )
    }

    private data class RunResult(val result: LintResult, val backend: EngineBackend?, val elapsedMs: Long) {
        fun verdict(): LintVerdict? = result.findings.firstOrNull()?.verdict
        fun reason(): String? = result.findings.firstOrNull()?.reason
        fun matchesExpected(fixture: Fixture): Boolean = verdict() == fixture.expectedVerdict
    }

    /** [expectedVerdict] null means fixture D's "clean" expectation: no finding for the tracked
     * entity at all, not one of the three [LintVerdict] values. */
    private data class Fixture(
        val id: String,
        val expectedVerdict: LintVerdict?,
        val chapterLabel: String,
        val paragraphs: List<String>,
        val candidate: LintCandidate,
    ) {
        fun expectedLabel(): String = expectedVerdict?.name ?: "none (empty findings expected)"
    }

    private companion object {
        const val TAG = "LintGoldenFixtureSmokeTest"
        const val REPEATS = 3

        /** Literal prefix of the thinking-mode block [LintParser] strips — matching this
         * substring is enough to detect presence without needing the full regex. */
        const val THOUGHT_BLOCK_MARKER = "<|channel>thought"

        // Fixture A — obvious conflict: neither the wiki history nor this chapter's own text
        // gives any development/motive, so diving in without hesitation directly contradicts a
        // trauma-driven phobia established two chapters ago.
        val FIXTURE_A = Fixture(
            id = "A_conflict",
            expectedVerdict = LintVerdict.Conflict,
            chapterLabel = "6화",
            candidate = LintCandidate(
                entityId = "jeonghyun",
                entityName = "김정현",
                descHistory = listOf(
                    "1화: 김정현은 물을 극도로 무서워한다. 5년 전 사고 후유증",
                    "3화: 여전히 물 근처에는 가지 않으려 한다",
                ),
            ),
            paragraphs = listOf(
                "김정현은 호숫가에 서서 잠시 물결을 바라보았다.",
                "그는 별다른 망설임 없이 옷을 벗어 던지고 물속으로 뛰어들었다.",
                "차가운 물이 온몸을 감쌌지만 그는 개의치 않고 힘차게 헤엄쳐 나갔다.",
                "멀리서 지켜보던 친구들은 그 모습에 놀랄 뿐이었다.",
            ),
        )

        // Fixture B — development foreshadowed entirely by the wiki history itself: fear ->
        // practice -> waist-deep -> this chapter's first full submersion. No fresh in-chapter
        // motive needed; the timeline alone should carry the judgment.
        val FIXTURE_B = Fixture(
            id = "B_development_via_history",
            expectedVerdict = LintVerdict.Development,
            chapterLabel = "6화",
            candidate = LintCandidate(
                entityId = "jeonghyun",
                entityName = "김정현",
                descHistory = listOf(
                    "1화: 물을 무서워함",
                    "3화: 수영장에서 발만 담그는 연습을 시작했다",
                    "5화: 이제는 허리 깊이까지 들어갈 수 있게 되었다",
                ),
            ),
            paragraphs = listOf(
                "김정현은 수영장 가장자리에 서서 크게 숨을 들이쉬었다.",
                "그는 천천히 물속으로 걸어 들어가 허리까지 몸을 담갔다.",
                "그러고는 눈을 질끈 감고 처음으로 머리끝까지 물속에 잠겼다.",
                "몇 초 뒤 수면 위로 올라온 그의 얼굴에는 옅은 미소가 번졌다.",
            ),
        )

        // Fixture C — development driven purely by this chapter's own stated motive (a sibling
        // in danger), with NO foreshadowing anywhere in the wiki history. This is the case
        // LintSchema's development criterion was widened for (see LintSchemaTest).
        val FIXTURE_C = Fixture(
            id = "C_development_in_chapter",
            expectedVerdict = LintVerdict.Development,
            chapterLabel = "6화",
            candidate = LintCandidate(
                entityId = "jeonghyun",
                entityName = "김정현",
                descHistory = listOf(
                    "1화: 물을 무서워함",
                    "2화: 여전히 물가 근처에는 가지 않는다",
                ),
            ),
            paragraphs = listOf(
                "김정현은 강둑을 따라 걷다가 동생이 물살에 휩쓸리는 것을 보았다.",
                "생각할 겨를도 없이 몸이 먼저 움직였고, 그는 곧장 강물로 뛰어들었다.",
                "심장이 터질 듯 뛰었지만 오직 동생을 구해야 한다는 생각뿐이었다.",
                "그는 필사적으로 팔을 저어 동생에게 다가갔다.",
            ),
        )

        // Fixture D — clean: this chapter has nothing to do with the tracked trait at all, so no
        // finding for this entity is the correct output.
        val FIXTURE_D = Fixture(
            id = "D_clean",
            expectedVerdict = null,
            chapterLabel = "6화",
            candidate = LintCandidate(
                entityId = "jeonghyun",
                entityName = "김정현",
                descHistory = listOf(
                    "1화: 검술 수련 중이다",
                    "2화: 처음으로 실전에 나섰다",
                ),
            ),
            paragraphs = listOf(
                "김정현은 저녁 무렵 식당에 앉아 늦은 저녁을 먹었다.",
                "국은 조금 식어 있었지만 배가 고팠던 그는 개의치 않고 그릇을 비웠다.",
                "옆자리에 앉은 상인이 오늘 장터에서 있었던 일을 늘어놓았다.",
                "김정현은 고개를 끄덕이며 조용히 식사를 마쳤다.",
            ),
        )

        // Fixture E — legitimately ambiguous: the chapter clearly tests the tracked trait (the
        // entity does something directly relevant to it — burning the mentor's letter) but
        // deliberately withholds any motive or emotional reaction, so neither conflict nor
        // development can be read off the text with confidence. Unlike D, this entity should NOT
        // be excluded by the v3 inclusion gate (the scene does touch the tracked trait) — it
        // should pass the gate and land on `ambiguous` at the verdict stage.
        val FIXTURE_E = Fixture(
            id = "E_ambiguous_legit",
            expectedVerdict = LintVerdict.Ambiguous,
            chapterLabel = "6화",
            candidate = LintCandidate(
                entityId = "jeonghyun",
                entityName = "김정현",
                descHistory = listOf(
                    "1화: 김정현은 스승을 깊이 존경한다. 스승의 가르침을 삶의 지침으로 삼는다",
                    "2화: 스승과 자주 서신을 주고받는다",
                ),
            ),
            paragraphs = listOf(
                "김정현은 스승에게서 온 편지를 받았다.",
                "그는 봉투를 뜯어 편지를 끝까지 읽었다.",
                "다 읽은 편지를 촛불에 가져다 대자 종이는 이내 불길에 휩싸였다.",
                "그는 재가 되어 흩어지는 편지를 가만히 지켜보았다.",
            ),
        )

        val FIXTURES = listOf(FIXTURE_A, FIXTURE_B, FIXTURE_C, FIXTURE_D, FIXTURE_E)
    }
}
