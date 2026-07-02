package com.example.storymind.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.storymind.data.GraphEdge
import com.example.storymind.data.GraphNode
import com.example.storymind.data.StoryRepository
import com.example.storymind.data.WikiEntry
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.platform.IngestEngineProvider
import com.example.storymind.ui.components.SmAiStatus
import com.example.storymind.work.IngestWorker
import com.example.storymind.work.LintWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryUiState(
    val previousChapters: List<ChapterEntity> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentBody: String = "",
    val wikiEntries: List<WikiEntry> = emptyList(),
    val graphNodes: List<GraphNode> = emptyList(),
    val graphEdges: List<GraphEdge> = emptyList(),
    val orphanIds: Set<String> = emptySet(),
    val aiStatus: SmAiStatus = SmAiStatus.Idle,
    val isModelAvailable: Boolean = false,
    val isLoaded: Boolean = false,
    /** True once the current draft has been persisted via [StoryViewModel.saveAndIngest], win or
     * lose on the ingest itself — this is what unlocks "다음 화 쓰기", not ingest success, so
     * writers aren't stuck mid-story when the on-device model is unavailable. */
    val canAdvance: Boolean = false,
    val lastSaveIngested: Boolean = false,
) {
    val currentLabel: String get() = "${currentChapterIndex + 1}화"
}

/**
 * Owns Room persistence and the editor's UI state for the writing flow: type a chapter -> save ->
 * ingest -> wiki/graph accumulate -> next chapter, surviving process death.
 *
 * The ingest itself no longer runs here: saving commits the manuscript (`ingested = false`) and
 * enqueues [IngestWorker], which survives this ViewModel — and the whole process — being torn
 * down. This ViewModel only *observes* that work's [WorkInfo] to drive the status badge, and
 * reloads the accumulated progress when a run succeeds. The engine is owned by
 * [IngestEngineProvider] at app scope, which is why there is no `onCleared` releasing it anymore.
 */
class StoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StoryRepository(StoryDatabase.get(application))
    private val workManager = WorkManager.getInstance(application)

    /** Chapter whose unique ingest work this ViewModel watches: the last chapter saved this
     * session, or (after a restart) the last chapter found in the DB — that's the only one whose
     * worker can still be pending/running. */
    private val watchedChapterIndex = MutableStateFlow<Int?>(null)

    /** See [toAiStatus] — gates SUCCEEDED → Done so a finished work record persisted from a
     * previous session doesn't resurrect the Done badge on cold start. */
    private var sessionSawActiveWork = false

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState

    /** Chapter whose lint work this ViewModel watches. Unlike [watchedChapterIndex], this stays
     * null until [lintCurrentChapter] is actually called — lint is on-demand, so there is no
     * "resume watching after restart" case to handle and no stale-session gate to write (see
     * [toLintUiState]'s KDoc). */
    private val watchedLintChapterIndex = MutableStateFlow<Int?>(null)

    private val _lintState = MutableStateFlow<LintUiState>(LintUiState.Idle)
    val lintState: StateFlow<LintUiState> = _lintState

    init {
        viewModelScope.launch {
            val savedChapters = repository.loadChapters()
            val progress = repository.loadProgress()
            val modelAvailable = IngestEngineProvider.isModelAvailable(getApplication())

            // A last chapter with ingested=false is reopened as the working draft. That was true
            // before the worker existed too (a failed ingest leaves the same state); with async
            // ingest the window is just longer, and if the worker finishes later the flag flips
            // to true so the next launch advances past it.
            val last = savedChapters.lastOrNull()
            val (previous, draftIndex, draftBody) = when {
                last == null -> Triple(emptyList<ChapterEntity>(), 0, "")
                !last.ingested -> Triple(savedChapters.dropLast(1), last.chapterIndex, last.body)
                else -> Triple(savedChapters, last.chapterIndex + 1, "")
            }

            _uiState.update {
                it.copy(
                    previousChapters = previous,
                    currentChapterIndex = draftIndex,
                    currentBody = draftBody,
                    wikiEntries = progress.wikiEntries,
                    graphNodes = progress.nodes,
                    graphEdges = progress.edges,
                    orphanIds = progress.orphanIds,
                    isModelAvailable = modelAvailable,
                    isLoaded = true,
                )
            }
            watchedChapterIndex.value = last?.chapterIndex
        }
        observeIngestWork()
        observeLintWork()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeIngestWork() {
        viewModelScope.launch {
            watchedChapterIndex
                .filterNotNull()
                .flatMapLatest { index ->
                    workManager.getWorkInfosForUniqueWorkFlow(IngestWorker.uniqueNameFor(index))
                }
                .collect { infos -> onIngestWorkChanged(infos.firstOrNull()?.state) }
        }
    }

    private suspend fun onIngestWorkChanged(state: WorkInfo.State?) {
        if (state != null && !state.isFinished) sessionSawActiveWork = true
        val status = state.toAiStatus(sessionSawActiveWork)
        if (status == SmAiStatus.Done) {
            // The worker committed a new progress snapshot; re-read it so wiki/graph reflect the
            // chapter that just finished ingesting.
            val progress = repository.loadProgress()
            _uiState.update {
                it.copy(
                    wikiEntries = progress.wikiEntries,
                    graphNodes = progress.nodes,
                    graphEdges = progress.edges,
                    orphanIds = progress.orphanIds,
                    aiStatus = status,
                    lastSaveIngested = true,
                )
            }
        } else {
            _uiState.update { it.copy(aiStatus = status) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLintWork() {
        viewModelScope.launch {
            watchedLintChapterIndex
                .filterNotNull()
                .flatMapLatest { index ->
                    workManager.getWorkInfosForUniqueWorkFlow(LintWorker.uniqueNameFor(index))
                }
                .collect { infos -> onLintWorkChanged(infos.firstOrNull()) }
        }
    }

    private fun onLintWorkChanged(info: WorkInfo?) {
        val wikiById = _uiState.value.wikiEntries.associateBy { it.id }
        val findingsJson = info?.outputData?.getString(LintWorker.KEY_FINDINGS_JSON)
        _lintState.value = info?.state.toLintUiState(findingsJson) { entityId ->
            wikiById[entityId]?.name ?: entityId
        }
    }

    fun onBodyChange(body: String) {
        _uiState.update { it.copy(currentBody = body, canAdvance = false) }
    }

    /**
     * Persists the manuscript first — that alone unlocks the next chapter (rule 3) — then hands
     * the ingest to [IngestWorker]. From here on the badge is driven by [onIngestWorkChanged];
     * the eager Analyzing update below only bridges the gap until WorkManager's first emission.
     */
    fun saveAndIngest() {
        val state = _uiState.value
        val body = state.currentBody
        if (body.isBlank()) return

        val chapterIndex = state.currentChapterIndex
        val label = state.currentLabel

        viewModelScope.launch {
            repository.saveChapter(chapterIndex, label, title = null, body = body, ingested = false)
            _uiState.update { it.copy(canAdvance = true, lastSaveIngested = false) }

            // Any prior lint result belonged to the manuscript that just got overwritten — a
            // stale finding for the old body would misread as a verdict on the new one.
            _lintState.value = LintUiState.Idle
            watchedLintChapterIndex.value = null

            if (!IngestEngineProvider.isModelAvailable(getApplication())) {
                _uiState.update { it.copy(aiStatus = SmAiStatus.Idle) }
                return@launch
            }

            sessionSawActiveWork = true
            _uiState.update { it.copy(aiStatus = SmAiStatus.Analyzing) }
            IngestWorker.enqueue(getApplication(), chapterIndex)
            watchedChapterIndex.value = chapterIndex
        }
    }

    /**
     * On-demand setting-consistency check for the current chapter (a "설정 검사" button, not
     * part of the save/advance flow — CLAUDE.md rule 3 doesn't apply here since nothing about
     * advancing depends on this). Only meaningful once the chapter is ingested: lint reads
     * candidates out of the accumulated wiki, which has nothing for a chapter that hasn't
     * finished ingesting yet. [EditorScreen] gates the button on the same
     * [StoryUiState.lastSaveIngested] flag this checks.
     */
    fun lintCurrentChapter() {
        val state = _uiState.value
        if (!state.lastSaveIngested) return

        val chapterIndex = state.currentChapterIndex
        _lintState.value = LintUiState.Running
        LintWorker.enqueue(getApplication(), chapterIndex)
        watchedLintChapterIndex.value = chapterIndex
    }

    fun startNextChapter() {
        val state = _uiState.value
        if (!state.canAdvance) return

        // The lint result (if any) belongs to the chapter being left behind.
        _lintState.value = LintUiState.Idle
        watchedLintChapterIndex.value = null

        val justSaved = ChapterEntity(
            chapterIndex = state.currentChapterIndex,
            label = state.currentLabel,
            title = null,
            body = state.currentBody,
            ingested = state.lastSaveIngested,
        )
        _uiState.update {
            it.copy(
                previousChapters = it.previousChapters + justSaved,
                currentChapterIndex = it.currentChapterIndex + 1,
                currentBody = "",
                aiStatus = SmAiStatus.Idle,
                canAdvance = false,
                lastSaveIngested = false,
            )
        }
    }
}
