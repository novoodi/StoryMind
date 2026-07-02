package com.example.storymind.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.storymind.ai.OnDeviceTextEngine
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.platform.IngestEngineProvider
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
        IngestEngineProvider.textEngineOverride = null
        StoryDatabase.setInstanceForTesting(null)
        db.close()
    }

    @Test
    fun resavingTheChapter_resetsAStaleLintResult() = runBlocking {
        val viewModel = StoryViewModel(application)
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.isLoaded } }

        viewModel.onBodyChange("지우와 민준이 카페에서 만났다.")
        viewModel.saveAndIngest()
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.lastSaveIngested } }

        viewModel.lintCurrentChapter()
        withTimeout(TIMEOUT_MS) { viewModel.lintState.first { it is LintUiState.Done } }
        assertTrue(viewModel.lintState.value is LintUiState.Done)

        // Re-editing and re-saving the same (not-yet-advanced) chapter must invalidate the now-
        // stale lint result — otherwise it would misread as a verdict on the new manuscript.
        viewModel.onBodyChange("지우와 민준이 다시 만났다.")
        viewModel.saveAndIngest()

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
