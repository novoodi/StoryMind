package com.example.storymind.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Observational, not pass/fail — measures whether [IngestService]'s low-temperature retry
 * escalation ([IngestService.samplerForAttempt], docs/constrained-decoding-spike.md's "대안 2")
 * actually reduces on-device JSON parse failures, and what each sampler setting costs in wall
 * time. There's no known-good success-rate threshold yet, so this test doesn't assert one — it
 * exists to gather that number (from a real device, via `adb logcat`) rather than enforce it. The
 * retry loop itself isn't used here: it stops at the first successful parse, which would starve
 * later escalation steps of runs to measure, so each setting gets its own fixed batch instead.
 * Requires the model file; skipped via [Assume] otherwise, same as [ConstrainedDecodingSmokeTest].
 */
@RunWith(AndroidJUnit4::class)
class IngestSamplingSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fixedManuscript_fiveRunsPerSamplerSetting_logsParseSuccessRateAndTiming() = runBlocking {
        val engine = OnDeviceEngine(context)
        assumeTrue("Model file not found at ${engine.modelPath} — skipping", engine.isModelAvailable)
        engine.initialize()

        val prompt = IngestSchema.buildIngestPrompt(SAMPLE_TITLE, SAMPLE_PARAGRAPHS)
        try {
            for (sampler in SAMPLER_SETTINGS_TO_MEASURE) {
                var successes = 0
                var totalMs = 0L
                repeat(RUNS_PER_SETTING) { run ->
                    val startMs = System.currentTimeMillis()
                    val raw = engine.generate(prompt, sampler)
                    val elapsedMs = System.currentTimeMillis() - startMs
                    totalMs += elapsedMs
                    val parsed = IngestParser.parseOrNull(raw) != null
                    if (parsed) successes++
                    Log.i(TAG, "sampler=$sampler run ${run + 1}/$RUNS_PER_SETTING elapsedMs=$elapsedMs parsed=$parsed")
                }
                Log.i(
                    TAG,
                    "sampler=$sampler summary: $successes/$RUNS_PER_SETTING parsed, " +
                        "avgMs=${totalMs / RUNS_PER_SETTING}",
                )
            }
        } finally {
            engine.release()
        }
    }

    private companion object {
        const val TAG = "IngestSamplingSmokeTest"
        const val RUNS_PER_SETTING = 5

        const val SAMPLE_TITLE = "빗속의 첫 만남"
        val SAMPLE_PARAGRAPHS = listOf(
            "지우는 폭우가 쏟아지는 골목에서 낡은 우산을 쓴 민준과 처음 마주쳤다.",
            "두 사람은 비를 피해 골목 끝의 카페 달빛으로 들어갔고, 창가 자리에 마주 앉았다.",
            "민준은 주머니에서 오래된 회중시계를 꺼내 지우에게 보여주며, 이것이 두 사람을 만나게 했다고 말했다.",
        )

        /** Mirrors [IngestService.samplerForAttempt]'s three escalation steps — same topK/topP,
         * same temperatures, measured independently rather than through the actual retry loop. */
        val SAMPLER_SETTINGS_TO_MEASURE = listOf(
            SamplerSettings(topK = 40, topP = 0.9, temperature = 0.1, seed = 42),
            SamplerSettings(topK = 40, topP = 0.9, temperature = 0.4, seed = 43),
            SamplerSettings(topK = 40, topP = 0.9, temperature = 0.7, seed = 44),
        )
    }
}
