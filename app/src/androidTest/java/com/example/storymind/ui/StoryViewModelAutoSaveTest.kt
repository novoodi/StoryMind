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
 * 디바운스 자동 저장이 명시적 "저장" 없이 편집 중인 원고를 draft 행에 넣어 유실을 막는지, 그리고
 * 그 저장이 **눈에 보이지 않는지**(인제스트/canAdvance/aiStatus 무접촉) 검증한다.
 *
 * 핵심 격리: 엔진 오버라이드로 [IngestEngineProvider.isModelAvailable]을 true로 만들고 자동 분석을
 * 기본값(켜짐) 그대로 둔다 — 그러니 위키가 하나라도 생겼다면 그 원인은 오직 "자동 저장이 인제스트를
 * 건드렸다"뿐이다. 즉 위키 0개는 자동 저장이 분석과 완전히 분리돼 있다는 증거다.
 */
@RunWith(AndroidJUnit4::class)
class StoryViewModelAutoSaveTest {

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
        // 관찰자 정리는 db.close()보다 먼저 *완료*돼야 한다(StoryViewModelAutoAnalyzeTest 참고):
        // 아직 살아있는 observeChapters Flow가 닫힌 커넥션 풀을 건드리면 다음 테스트가 깨진다.
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
    fun autoSave_persistsDraftWithoutSaving_andNeverIngests() = runBlocking {
        val viewModel = StoryViewModel(application).also { this@StoryViewModelAutoSaveTest.viewModel = it }
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.isLoaded } }

        // 명시적 저장 없이 타이핑만 — 디바운스 자동 저장에만 의존한다.
        viewModel.onBodyChange(DRAFT_BODY)

        // 원고가 스스로 chapters 테이블에 도달한다(저장 버튼을 누른 적 없다).
        withTimeout(TIMEOUT_MS) {
            while (db.storyDao().loadChapter(0)?.body != DRAFT_BODY) delay(50)
        }
        val saved = db.storyDao().loadChapter(0)
        assertEquals(DRAFT_BODY, saved?.body)
        assertFalse("자동 저장은 미분석 draft로 남겨야 한다", saved?.ingested ?: true)

        // 눈에 보이지 않아야 한다: 다음 화 잠금 해제도, 분석 시작도 하지 않는다.
        assertFalse("자동 저장은 다음 화를 잠금 해제하면 안 된다", viewModel.uiState.value.canAdvance)
        assertEquals(SmAiStatus.Idle, viewModel.uiState.value.aiStatus)

        // 모델이 있고 자동 분석이 켜져 있어도 인제스트는 절대 일어나지 않는다.
        delay(500) // 잘못 enqueue된 인제스트가 있었다면 돌 시간을 준다
        assertEquals(0, db.storyDao().loadWikiEntries().size)

        // 지금 쓰는 중인 화(현재 화)는 "분석 안 된 화" 배너 카운트에서 제외된다.
        assertEquals(0, viewModel.pendingAnalysisCount.value)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val DRAFT_BODY = "아직 저장 버튼을 누르지 않은 초안 본문."

        val INGEST_RESPONSE = """
            {
              "chapter_summary": "초안 요약.",
              "entities": [
                {"id":"jiwoo","type":"character","name":"김지우","desc":"주인공"}
              ],
              "relations": []
            }
        """.trimIndent()
    }
}
