package com.example.storymind.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Observational, not pass/fail — same pattern as [IngestSamplingSmokeTest]/
 * [LintGoldenFixtureSmokeTest]. Successor to the now-removed `IdConsistencyPromptSmokeTest`: that
 * test's job (compare the v2 vs v3 prompt) ended once v3's rule bullet was withdrawn
 * ([IngestSchema.PROMPT_VERSION]'s "v3 -> v4" KDoc) — the measurement showed no improvement, so
 * there's no longer a second variant to compare against. The golden fixture (the real 1화
 * manuscript that reproduced the 2026-07 incident) is worth keeping, though: it's a manuscript
 * already known to provoke Gemma's "entities id != relations id" slip, which makes it useful for
 * watching the *current* mitigations instead — [IngestService.generateResult]'s soft-failure retry
 * (Part 1 of the 2026-07 follow-up batch) and [IngestService.shadowMatchDescription]'s shadow-mode
 * matching (Part 2).
 *
 * Runs the full [IngestService.ingest] path (not just raw generation — this exercises the retry
 * loop, not only the model) against the golden manuscript 5 times and reports two numbers:
 * - **Residual mismatch rate**: how often an unresolved relation survives all the way to
 *   [PartialDropRecorder] — i.e. every one of [IngestService]'s attempts had one, so the retry
 *   couldn't route around it. This is the rate the soft-failure retry (Part 1) left *after*
 *   mitigation, not the raw per-attempt rate `IdConsistencyPromptSmokeTest` measured before it.
 * - **Shadow-match hit rate** among those residual mismatches: how often
 *   [IngestService.shadowMatchDescription] would have found exactly one candidate. A high hit rate
 *   here is the kind of evidence that would justify promoting shadow matching from observation-only
 *   into an actual merge-time repair.
 *
 * There's no known-good threshold for either number, so this doesn't assert one — it exists to
 * gather data from a real device via `adb logcat`, same as its predecessor did.
 *
 * Requires the model file; skipped via [Assume] otherwise.
 */
@RunWith(AndroidJUnit4::class)
class IdMismatchRateSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun goldenChapter1_fiveRuns_logsResidualMismatchRateAndShadowMatchHitRate() = runBlocking {
        val engine = OnDeviceEngine(context)
        assumeTrue("Model file not found at ${engine.modelPath} — skipping", engine.isModelAvailable)
        engine.initialize()

        val service = IngestService(IngestTextEngine { prompt, sampler -> engine.generate(prompt, sampler) })

        val previousRecorder = partialDropRecorder
        var dropsThisRun = mutableListOf<String>()
        partialDropRecorder = PartialDropRecorder { detail -> dropsThisRun += detail }

        try {
            var residualMismatches = 0
            var shadowMatchHits = 0
            repeat(RUNS) { run ->
                dropsThisRun = mutableListOf()
                val result = service.ingest(chapterLabel = GOLDEN_TITLE, title = GOLDEN_TITLE, paragraphs = GOLDEN_PARAGRAPHS)
                val residualDrop = dropsThisRun.isNotEmpty()
                if (residualDrop) {
                    residualMismatches++
                    if (dropsThisRun.any { it.contains("shadow_match:") }) shadowMatchHits++
                }
                Log.i(
                    TAG,
                    "run ${run + 1}/$RUNS ingested=${result != null} residualDrop=$residualDrop " +
                        "detail=${dropsThisRun.joinToString(" | ")}",
                )
            }

            Log.i(TAG, "===== ID MISMATCH RATE REPORT (PROMPT_VERSION=${IngestSchema.PROMPT_VERSION}) =====")
            Log.i(TAG, "residual mismatch (survived every attempt's soft-failure retry): $residualMismatches/$RUNS")
            Log.i(TAG, "of those, shadow_match found exactly one candidate: $shadowMatchHits/$residualMismatches")
            Unit // Log.i returns Int, which would otherwise make this try-expression (and thus the @Test method) non-void.
        } finally {
            partialDropRecorder = previousRecorder
            engine.release()
        }
    }

    private companion object {
        const val TAG = "IdMismatchRateSmokeTest"
        const val RUNS = 5

        const val GOLDEN_TITLE = "1화"

        /** The actual 1화 manuscript that reproduced the 2026-07 incident — captured verbatim
         * from the device DB right after the incident, not a synthetic stand-in, since this test's
         * purpose is watching current mitigations against exactly the conditions that triggered it. */
        val GOLDEN_PARAGRAPHS = listOf(
            "\"진짜지 그럼 가짜겠냐\" 율은 침대에서 일어나 기지개를 켜며 이불을 정리했다.",
            "\"그래서 어제 좀 진전이 있었나?\" 이불을 다 정리한 율은 따뜻한 클라우터를 마시며 말했다.",
            "\"네… 별거는 아니지만 카페랑 어제 꿈에서 나왔던 횡단보도도 가보고...\"",
            "\"잘했네 그럼 꿈을 만들어 볼까 그전에 뭐 하나만 물어보자\"",
            "\"너랑 그 지민이라는 애랑 뭔 일이 있었던 거냐? 나도 그걸 좀 알아야 꿈을 만들기 편해서 말이지 뭔가 이 꿈 기록부에는 나와 있지 않는 그런 게 있을 것 같단 말이지\" 율은 빈 컵을 책상에 올려놓으며 말했다.",
            "\"그... 그게\" 진성은 떨리는 목소리로 율에게 지민을 처음 만난 날부터 어쩌다가 지민이 전학을 가게 됐는지 그동안에 있었던 모든 일을 설명해주었다. 그렇게 진성의 기나긴 고백이 끝나고 잠시 동안 정적이 찾아왔다.",
            "\"하…\" 율은 짧은 한숨을 내쉬었다.",
            "\"그러니깐 네가 한말을 정리하면 너한테 가장 먼저 다가와준 친구를 버린 파렴치한 놈이라는 거네\" 율의 날카로운 말은 진성에게 비수가 되어 날아왔지만 진성은 변명할 수 없었다. 율의 말이 사실이기 때문이다.",
            "\"알아요 저도 제가 쓰레기 같은 거…\" 진성은 더 이상 말을 이어가지 못했다.",
            "\"알면 됐다. 이제부터 잘하면 되지 뭐 과거의 그 쓰레기 같은 너의 행동도 앞으로의 선택으로 바꿀 수 있어\" 자리에서 일어난 율은 주변에 구름들을 만들어 내기 시작했다. 잠시 뒤 방 안은 구름으로 가득 찼고 이후 율과 진성이 있던 방은 고등학교 1학년 교실로 변했다.",
            "\"자자 그럼 훈련을 시작해 볼까 이야기를 들어보니깐 지민이라는 애가 괴롭힘을 당한 것 같던데 내가 그 상황을 만들어 줄게 그 이후엔 뭘 해야 하는지 알겠지?\" 율은 순식간에 변환 상황에 놀란 표정을 한 진성을 일으켜 세우며 말했다.",
            "\"네? 뭘요?\" 진성은 당황한 목소리로 율을 쳐다보면서 말했다.",
            "\"꿈에서라도 비겁하게 숨지 말라고 사실은 너도 알았다면서 걔가 그런 애가 아니라는 거 그럼 시작한다\" 율이 박수를 치자 꿈이 시작됐다.",
            "[아이들이 뒤에서 떠드는 소리가 들린다. 이에 진성은 그 소리가 지민의 뒷담화라는 것을 알게 된다. \"저번에 물건 없어진 거 이지민 걔라면서\" \"내가 그럴 줄 알았어 저번에 보니깐…\" 지민을 향한 수많은 거짓된 정보들이 쏟아져 나오고 있었다. 계속해서 듣고 있던 진성은 그때와는 다르게 이번엔 아니라고 말하기로 다짐했다. \"아니야 니들이 봤어? 제발 다 조용히 좀 해\" 진성은 자리에서 일어나 교실에 있는 아이들을 향해 소리쳤다. 그 순간 지민의 험담을 하고 있던 모든 아이들이 진성을 쳐다보기 시작했다. \"뭐야… 잠만 너 이지민이랑 다니던 애 아니야?\" \"끼리끼리 논다더니 감싸주는거야?\" 교실에 있던 아이들이 수군거리기 시작했다. 교실에서 울려 퍼지는 수근거림은 진성에게 마치 너도 똑같이 당할래? 라고 들렸다. 진성은 무서웠지만 한편으로는 지민이 그동안 혼자서 이런 고통을 견뎌냈다고 생각하니 미안했다. \"도망치고 싶어…\" 진성은 마음속으로 외쳤다. 그때 진성의 가슴 쪽에서 또 다시 그림자가 나오기 시작했다.] 꿈 밖에서 이를 지켜보던 율은 그림자가 나오는 것을 보고 꿈을 종료시켰다.",
            "\"또 그림자가…\" 떨리는 목소리로 진성은 바닥에 주저앉았다.",
            "\"잘했어 저번보다는 괜찮네\" 율은 바닥에 주저앉아 있는 진성을 일으키고 클라우터를 주면서 말했다. \"오늘은 여기까지 예전처럼 도망친 게 아니라 일어나서 소리쳤다는 것만으로도 상당히 만족스러운 결과야\" 율은 생각보다 빠른 진성의 행동에 만족스러운 표정을 지었다. \"그림자는 너가 현실에서 해결하지 못하면 계속해서 튀어나올 거야 너 근데 지민이 어디 사는지는 아냐?\" 율은 클라우터를 마시는 진성을 지그시 쳐다봤다.",
            "\"아니요 사실 전학 간 이후로 제가 지민이가 보내는 문자고 카톡이고 하나도 안 봐서\" 진성은 자신을 쳐다보고 있는 율의 시선을 피하며 말했다.",
            "\"뭐하고 사는지나 한번 주변에 물어봐라…\" 율은 쉽지 않다는 듯 진성을 쳐다봤다. 이후 진성은 저번처럼 속이 울렁거리고 주위가 흔들리더니 또 바닥에 쓰러졌다. \"이제부턴 너가 잘해야 해 내가 해줄 수 있는 건 여기까지야\" 이후 진성은 율의 시야에서 사라졌다.",
            "\"아들 아들 뭔 꿈을 꿨길래 애가 이렇게 신음 소리를 내\" 진성의 방에서 나는 신음 소리를 듣고 들어온 엄마가 아들을 흔들었다.",
            "\"별거 아니야 엄마 걱정마\" 눈을 뜬 진성은 우선 엄마를 진정시키고 나서 무엇을 해야 할지 곰곰이 생각했다. 생각을 마친 진성은 거실로 나가서 밥을 차리고 있는 엄마에게 다가갔다.",
            "\"엄마 그… 지민이 있잖아 어디로 이사 간 지 알아?\" 진성은 크게 심호흡을 한 뒤 엄마에게 물어봤다. 진성의 말을 들은 엄마는 식탁으로 와서 진성에게 앉으라는 듯이 손짓을 했다. 진성이 의자에 앉자 엄마는 운을 떼기 시작했다.",
            "\"지민이가 이사 간 이후에 잘 지내냐고 몇 번 연락이 왔었어 요즘은 안 오긴 하는데.\" 말을 들은 진성은 죄책감에 미안해서 아무런 말도 못했다. 자신은 지민이 힘들 때 아무런 도움을 주지 못했는데 지민은 자신을 걱정하고 있었다는 사실에 죄책감이 가슴을 옥죄여 왔다.",
            "\"아들 한번 연락이라도 해봐 무슨 일이 있었는지는 잘 모르겠지만 너가 힘들 때 도와줬던 친구잖아\" 엄마의 그 한마디가 진성을 더 비참하게 만들었다. 진성은 방으로 들어가 한참 동안 생각했다. 이후 생각을 마친 진성은 자신의 사과가 진정한 사과가 아니라 그저 죄책감을 덜기 위한 이기적인 행동이 아닐까 하며 지민에게 쓸 문자를 썼다 지웠다 반복했다. 그때 진성은 엘라시움에서 율이 했던 말이 떠올랐다. \"과거의 그 쓰레기 같은 너의 행동도 앞으로의 선택으로 바꿀 수 있어\" 율이 했던 말을 떠올리며 마음을 다잡은 진성은 지민에게 연락을 보냈다. <잘 지내? 그동안 연락 못 봐서 미안해 내가 연락을 먼저 했어야 했는데 엄마한테 들었어 계속 문자 보냈다고 하던데… 혹시 만날 수 있을까? 아직 못한 말이 있어서...> 전송 버튼을 누른 진성은 지민이 언제 연락을 볼까 초조한 마음으로 계속해서 봤지만 숫자 1은 쉽사리 없어지지 않았다. \"그래 나라도 연락을 안 보겠다\" 그렇게 하루 종일 방에서 핸드폰만 보고 있던 진성은 결국 잠에 들었다.",
        )
    }
}
