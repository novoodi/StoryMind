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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives [StoryViewModel] end-to-end (real WorkManager + an in-memory Room DB, same setup
 * [com.example.storymind.work.IngestWorkerTest] uses for the worker in isolation) to cover a gap
 * a plain [LintStatusMapping] test can't reach: [StoryViewModel.lintState] is owned by the
 * ViewModel, not by the pure WorkInfo-to-state mapping, so the "resaving invalidates a stale lint
 * result" behavior can only be verified against the real [StoryViewModel.saveAndIngest] /
 * [StoryViewModel.lintCurrentChapter] sequence.
 */
@RunWith(AndroidJUnit4::class)
class StoryViewModelLintResetTest {

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

        // Routed by prompt shape: only LintSchema's template mentions "검사 대상 엔티티".
        IngestEngineProvider.textEngineOverride = OnDeviceTextEngine { prompt ->
            if (prompt.contains("검사 대상 엔티티")) LINT_RESPONSE else INGEST_RESPONSE
        }
    }

    @After
    fun tearDown() {
        // 반드시 db.close()보다 먼저: ViewModel의 관찰 코루틴(observeChapterFlags, WorkManager
        // WorkInfo 구독)은 clear 없이는 계속 살아 있는데, WorkManager는 프로세스 공용
        // 싱글턴이라 *다음* 테스트가 같은 unique work 이름을 쓰면 이 ViewModel이 깨어나
        // 이미 닫힌 DB를 읽는다 — 그 SQLException이 viewModelScope에서 미처리 예외가 되어
        // 계측 프로세스 전체를 죽였다(2026-07 기기 실행: IngestWorkerTest부터 전부 중단).
        viewModel?.viewModelScope?.cancel()
        viewModel = null
        IngestEngineProvider.textEngineOverride = null
        StoryDatabase.setInstanceForTesting(null)
        db.close()
    }

    @Test
    fun resavingTheChapter_resetsAStaleLintResult() = runBlocking {
        val viewModel = StoryViewModel(application).also { this@StoryViewModelLintResetTest.viewModel = it }
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.isLoaded } }

        viewModel.onBodyChange("지우와 민준이 카페에서 만났다.")
        viewModel.saveAndIngest()
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.lastSaveIngested } }

        viewModel.lintCurrentChapter()
        withTimeout(TIMEOUT_MS) { viewModel.lintState.first { it is LintUiState.Done } }
        assertTrue(viewModel.lintState.value is LintUiState.Done)

        // Re-editing and re-saving the same (not-yet-advanced) chapter must invalidate the now-
        // stale lint result — otherwise it would misread as a verdict on the new manuscript.
        // Awaited (not asserted synchronously — saveAndIngest resets inside a launched coroutine).
        viewModel.onBodyChange("지우와 민준이 다시 만났다.")
        viewModel.saveAndIngest()
        withTimeout(TIMEOUT_MS) { viewModel.lintState.first { it == LintUiState.Idle } }

        // The reset must also *survive* the re-save's ingest running to completion: those
        // WorkManager DB changes (enqueue → RUNNING → SUCCEEDED) are exactly the stimulus that
        // used to make a still-attached old subscription re-emit the finished lint's SUCCEEDED
        // record and flip the state back to a stale Done (the regression this test caught
        // on-device, 2026-07 — fixed by observeLintWork's null branch detaching the subscription).
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.lastSaveIngested } }
        assertEquals(LintUiState.Idle, viewModel.lintState.value)
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

        val LINT_RESPONSE = """{ "findings": [] }"""
    }
}
