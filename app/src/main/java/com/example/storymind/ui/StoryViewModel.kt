package com.example.storymind.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.storymind.ai.ChapterProgress
import com.example.storymind.ai.IngestService
import com.example.storymind.ai.OnDeviceEngine
import com.example.storymind.ai.merge
import com.example.storymind.data.GraphEdge
import com.example.storymind.data.GraphNode
import com.example.storymind.data.StoryRepository
import com.example.storymind.data.WikiEntry
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.ui.components.SmAiStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "StoryViewModel"

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
 * Owns the on-device engine, the ingest pipeline, and Room persistence for the writing flow:
 * type a chapter -> save -> ingest -> wiki/graph accumulate -> next chapter, surviving process death.
 */
class StoryViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = OnDeviceEngine(application)
    private val ingestService = IngestService(engine::generate)
    private val repository = StoryRepository(StoryDatabase.get(application).storyDao())

    private var progress = ChapterProgress()

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState

    init {
        viewModelScope.launch {
            val savedChapters = repository.loadChapters()
            progress = repository.loadProgress()
            val modelAvailable = engine.isModelAvailable

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
        }
    }

    fun onBodyChange(body: String) {
        _uiState.update { it.copy(currentBody = body, canAdvance = false) }
    }

    fun saveAndIngest() {
        val state = _uiState.value
        val body = state.currentBody
        if (body.isBlank()) return

        val chapterIndex = state.currentChapterIndex
        val label = state.currentLabel

        viewModelScope.launch {
            repository.saveChapter(chapterIndex, label, title = null, body = body, ingested = false)
            _uiState.update { it.copy(canAdvance = true, lastSaveIngested = false) }

            if (!engine.isModelAvailable) {
                _uiState.update { it.copy(aiStatus = SmAiStatus.Idle) }
                return@launch
            }

            _uiState.update { it.copy(aiStatus = SmAiStatus.Analyzing) }
            try {
                engine.initialize()
                val paragraphs = body.split(Regex("\n+")).filter { it.isNotBlank() }
                val result = ingestService.ingest(
                    chapterLabel = label,
                    title = label,
                    paragraphs = paragraphs,
                    existingWiki = progress.wikiEntries,
                )
                progress = progress.merge(result)
                repository.saveProgress(progress)
                repository.saveChapter(chapterIndex, label, title = null, body = body, ingested = true)

                _uiState.update {
                    it.copy(
                        wikiEntries = progress.wikiEntries,
                        graphNodes = progress.nodes,
                        graphEdges = progress.edges,
                        orphanIds = progress.orphanIds,
                        aiStatus = SmAiStatus.Done,
                        lastSaveIngested = true,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ingest failed, manuscript is saved but wiki/graph unchanged", e)
                _uiState.update { it.copy(aiStatus = SmAiStatus.Idle) }
            }
        }
    }

    fun startNextChapter() {
        val state = _uiState.value
        if (!state.canAdvance) return

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

    override fun onCleared() {
        // viewModelScope is already cancelled by the time onCleared runs, so release()
        // (a suspend fun closing the native engine) needs its own blocking call here.
        kotlinx.coroutines.runBlocking { engine.release() }
    }
}