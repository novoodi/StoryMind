package com.example.storymind.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.storymind.ai.OnDeviceTextEngine
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.platform.IngestEngineProvider
import com.example.storymind.ui.components.SmAiStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "자동 분석" 설정 토글이 실제로 저장 시 인제스트를 거르는지 검증한다. 엔진 오버라이드를
 * 걸어 [IngestEngineProvider.isModelAvailable]이 true가 되게 하므로, 화가 인제스트되지
 * *않았다면* 그 원인은 모델 부재가 아니라 오직 자동 분석 게이트뿐이다 — 즉 이 테스트는
 * 토글의 동작만 격리해 본다. 원고 저장과 canAdvance는 규칙 3에 따라 게이트와 무관하게
 * 유지돼야 한다는 것도 함께 확인한다.
 */
@RunWith(AndroidJUnit4::class)
class StoryViewModelAutoAnalyzeTest {

    private lateinit var application: Application
    private lateinit var db: StoryDatabase
    private var viewModel: StoryViewModel? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        application = context.applicationContext as Application
        db = Room.inMemoryDatabaseBuilder(context, StoryDatabase::class.java).build()
        StoryDatabase.setInstanceForTesting(db)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { INGEST_RESPONSE }
    }

    @After
    fun tearDown() = runBlocking {
        // 설정은 SharedPreferences로 영속되므로 다음 테스트에 새지 않도록 기본값(켜짐)으로
        // 되돌린다. ViewModel 관찰자 정리는 반드시 db.close()보다 먼저 *완료*돼야 한다:
        // observeChapters는 Room InvalidationTracker에 콜백을 등록하는데, cancel()은 취소를
        // 요청만 하고 즉시 반환하므로 곧바로 close()하면 아직 살아있는 Flow가 닫힌 커넥션
        // 풀을 건드려 백그라운드 스레드에서 크래시하고 *다음* 테스트를 실패시킨다. scope의
        // Job을 join해 취소가 실제로 끝날 때까지 기다린 뒤 닫는다.
        viewModel?.let { vm ->
            vm.setAutoAnalyze(true)
            val job = vm.viewModelScope.coroutineContext[Job]
            vm.viewModelScope.cancel()
            job?.join()
        }
        viewModel = null
        IngestEngineProvider.textEngineOverride = null
        StoryDatabase.setInstanceForTesting(null)
        db.close()
    }

    @Test
    fun autoAnalyzeOff_savesManuscriptAndUnlocksNext_butSkipsIngest() = runBlocking {
        val viewModel = StoryViewModel(application).also { this@StoryViewModelAutoAnalyzeTest.viewModel = it }
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.isLoaded } }
        viewModel.setAutoAnalyze(false)

        viewModel.onBodyChange("지우와 민준이 카페에서 만났다.")
        viewModel.saveAndIngest()

        // 저장은 성공하고 다음 화로 진행 가능해야 한다(규칙 3) — 게이트는 인제스트만 막는다.
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.canAdvance } }
        assertEquals(SmAiStatus.Idle, viewModel.uiState.value.aiStatus)
        assertFalse(viewModel.uiState.value.lastSaveIngested)

        // 화는 DB에 저장됐지만 ingested=false로 남고 파생 위키는 만들어지지 않는다.
        val saved = db.storyDao().loadChapter(0)
        assertEquals("지우와 민준이 카페에서 만났다.", saved?.body)
        assertFalse(saved?.ingested ?: true)
        assertEquals(0, db.storyDao().loadWikiEntries().size)
    }

    @Test
    fun pendingCount_tracksUnanalyzedChapters_andAnalyzePendingClearsThem() = runBlocking {
        val viewModel = StoryViewModel(application).also { this@StoryViewModelAutoAnalyzeTest.viewModel = it }
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.isLoaded } }
        viewModel.setAutoAnalyze(false)

        // 자동 분석 OFF로 두 화를 저장 — 둘 다 미분석 상태로 DB에 쌓인다.
        viewModel.onBodyChange("첫 번째 화 본문.")
        viewModel.saveAndIngest()
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.canAdvance } }
        viewModel.startNextChapter()
        viewModel.onBodyChange("두 번째 화 본문.")
        viewModel.saveAndIngest()

        // 배너 카운트는 "지금 편집 중인 화"를 제외한다: 2화가 현재 화이므로, 진행을 마친 1화만
        // 미분석으로 센다(둘 다 ingested=false여도). 이 제외가 자동 저장을 배너에 안 보이게 한다.
        withTimeout(TIMEOUT_MS) { viewModel.pendingAnalysisCount.first { it == 1 } }

        // "지금 분석" → 비파괴 resume이 두 화를 순서대로 인제스트한다. 완료 신호로 배너 카운트를
        // 쓰지 않는 이유: 카운트는 현재 화(2화)를 제외하므로, 1화만 인제스트돼도 0으로 떨어져
        // 2화 인제스트 완료 전에 조기 통과한다. 실제 두 화가 모두 ingested=true가 될 때까지 기다린다.
        viewModel.analyzePending()
        withTimeout(TIMEOUT_MS) {
            while (db.storyDao().loadChapters().count { it.ingested } != 2) delay(50)
        }
        assertEquals(2, db.storyDao().loadChapters().count { it.ingested })
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        val INGEST_RESPONSE = """
            {
              "chapter_summary": "지우와 민준이 카페에서 만났다.",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"}
              ],
              "relations": []
            }
        """.trimIndent()
    }
}
