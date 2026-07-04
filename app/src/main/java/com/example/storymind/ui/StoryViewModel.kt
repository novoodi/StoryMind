package com.example.storymind.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.storymind.data.GraphEdge
import com.example.storymind.data.GraphNode
import com.example.storymind.data.SettingsRepository
import com.example.storymind.data.StoryRepository
import com.example.storymind.data.WikiEntry
import com.example.storymind.data.backup.BackupValidation
import com.example.storymind.data.backup.StoryBackupManager
import com.example.storymind.data.db.ChapterEntity
import com.example.storymind.data.db.StoryDatabase
import com.example.storymind.platform.IngestEngineProvider
import com.example.storymind.ui.components.SmAiStatus
import com.example.storymind.work.IngestWorker
import com.example.storymind.work.LintWorker
import com.example.storymind.work.ReplayWorker
import com.example.storymind.ai.ingestLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryUiState(
    val previousChapters: List<ChapterEntity> = emptyList(),
    val currentChapterIndex: Int = 0,
    /** Optional per-chapter heading. Blank means "no title" — chapters continuing an episode often
     * have none. Persisted as `null` (not "") so [com.example.storymind.data.db.ChapterEntity.title]
     * and the manuscript export stay consistent about absence. */
    val currentTitle: String = "",
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
 * Derived-data rebuild progress for the Brain/Wiki banner. Counts come from the chapters table's
 * `ingested` flags, not from WorkManager: the flags survive process death and are exactly what the
 * replay loop itself reads, so "n/m" can never disagree with what will actually be rebuilt.
 * [failed] stays false once every eligible chapter is ingested even if the last replay WorkInfo
 * record says FAILED — a later successful pass (or a direct save of the failed chapter) heals the
 * data, and a warning about a problem that no longer exists would just erode trust in the banner.
 */
data class RebuildUiState(
    val running: Boolean = false,
    val failed: Boolean = false,
    val ingestedCount: Int = 0,
    val totalCount: Int = 0,
) {
    val visible: Boolean get() = running || failed
}

/**
 * 백업/내보내기 플로우의 상태 기계. 성공·실패까지 전부 상태로 표현하는 이유: SAF 쓰기와
 * 복원 검증은 수백 ms~수 초가 걸리는 백그라운드 작업이라, 결과를 콜백으로 흘리면 그 사이
 * 화면 회전/프로세스 복원에서 유실된다 — 시트를 띄울 근거는 관찰 가능한 상태여야 한다.
 * [RestoreReady]가 별도 상태인 것이 복원 안전장치 b의 구현이다: 검증 통과가 곧 실행이
 * 아니라, 사용자의 명시적 확인([StoryViewModel.confirmRestore])을 기다리는 중간 정지점이다.
 */
/** 복원 후보의 출처 — 검증/확인/승격 경로는 셋 다 동일하고, 확인 시트의 문구만 달라진다. */
enum class RestoreSource { External, PreRestore, AutoBackup }

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data object TxtExported : BackupUiState
    data object MdExported : BackupUiState
    data object BackupExported : BackupUiState
    data object ExportFailed : BackupUiState
    /** 검증 통과, 사용자 확인 대기 — 스테이징 파일이 유지되고 있다. [source]는 후보가
     * SAF 파일인지·복원 직전 백업인지·주기 자동 백업인지로, 확인 시트 문구에만 영향을 준다. */
    data class RestoreReady(val source: RestoreSource) : BackupUiState
    /** 검증 거부 — 아무것도 바뀌지 않았고 [reason]이 그 이유다(안전장치 a). */
    data class RestoreInvalid(val reason: String) : BackupUiState
    data object RestoreFailed : BackupUiState
    /** 직전 프로세스에서 확정된 복원이 이번 실행 시작 시 적용됐다 — 완료 안내용. */
    data object RestoreCompleted : BackupUiState
}

/**
 * Owns Room persistence and the editor's UI state for the writing flow: type a chapter -> save ->
 * ingest -> wiki/graph accumulate -> next chapter, surviving process death.
 *
 * The ingest itself no longer runs here: saving commits the manuscript (`ingested = false`) and
 * enqueues [IngestWorker], which survives this ViewModel — and the whole process — being torn
 * down. This ViewModel only *observes* that work's [WorkInfo] to drive the status badge, and
 * re-reads the accumulated progress whenever a chapter's flags change. The engine is owned by
 * [IngestEngineProvider] at app scope, which is why there is no `onCleared` releasing it anymore.
 */
class StoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StoryRepository(StoryDatabase.get(application))
    private val settings = SettingsRepository(application)
    private val workManager = WorkManager.getInstance(application)

    /** 저장 시 자동 인제스트 여부 — 설정 화면 토글이 관찰/변경하고, [saveAndIngest]가 저장
     * 시점에 [SettingsRepository.autoAnalyze] 값을 동기로 읽어 인제스트를 걸지 결정한다. */
    val autoAnalyze: StateFlow<Boolean> = settings.autoAnalyze

    /** 에디터 자동 교정(맞춤법) 여부 — 설정 토글과 에디터 텍스트필드가 함께 관찰한다. */
    val spellCheck: StateFlow<Boolean> = settings.spellCheck

    fun setAutoAnalyze(enabled: Boolean) = settings.setAutoAnalyze(enabled)

    fun setSpellCheck(enabled: Boolean) = settings.setSpellCheck(enabled)

    /**
     * Which work's state drives the editor's AI badge. One badge, two possible sources: the
     * per-chapter [IngestWorker] of the last save, or the [ReplayWorker] chain — whichever the
     * user most recently set in motion. A single watched source (instead of merging both flows)
     * keeps the badge unambiguous: after a retry/rebuild the per-chapter FAILED record that
     * prompted it still exists in WorkManager, and merging would show that stale Warning next to
     * the replay's Analyzing forever.
     */
    private sealed interface AiWatch {
        data class Chapter(val index: Int) : AiWatch
        data object Replay : AiWatch
    }

    private val aiWatch = MutableStateFlow<AiWatch?>(null)

    /** See [toAiStatus] — gates SUCCEEDED → Done so a finished work record persisted from a
     * previous session doesn't resurrect the Done badge on cold start. */
    private var sessionSawActiveWork = false

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState

    /** (index, body, title) last written to the chapters table by an explicit save or an autosave.
     * The guard that keeps the debounced autosave from re-writing an unchanged draft (and from
     * flipping ingested=true back to false when nothing actually changed). Seeded in init from the
     * reopened draft; declared ahead of the init block that assigns it. */
    private var lastPersistedDraft: DraftSnapshot? = null

    private data class DraftSnapshot(val index: Int, val body: String, val title: String)

    /** Chapter whose lint work this ViewModel watches. Unlike [aiWatch], this stays null until
     * [lintCurrentChapter] is actually called — lint is on-demand, so there is no "resume watching
     * after restart" case to handle and no stale-session gate to write (see [toLintUiState]'s
     * KDoc). Resetting back to null is meaningful too: it detaches the WorkInfo subscription in
     * [observeLintWork], which is what keeps a finished lint's record from re-asserting itself
     * after a re-save invalidated it (see that method's null branch). */
    private val watchedLintChapterIndex = MutableStateFlow<Int?>(null)

    private val _lintState = MutableStateFlow<LintUiState>(LintUiState.Idle)
    val lintState: StateFlow<LintUiState> = _lintState

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState

    /** pre-restore 자동 백업이 기기에 존재하는지 — 설정의 "복원 전 데이터로 되돌리기" 노출
     * 조건. 파일은 [StoryBackupManager.promoteStagedRestore]에서만 만들어지고 그 직후 프로세스가
     * 재시작되므로, 프로세스당 한 번 init에서 읽으면 충분하다. */
    private val _preRestoreAvailable = MutableStateFlow(false)
    val preRestoreAvailable: StateFlow<Boolean> = _preRestoreAvailable

    /** 주기 자동 백업이 하나라도 있는지 — 설정의 "최근 자동 백업에서 복원" 노출 조건. 백업은
     * [com.example.storymind.work.AutoBackupWorker]가 백그라운드에서 만들므로, preRestore와
     * 마찬가지로 프로세스당 한 번 init에서 읽는다(다음 실행에 반영). */
    private val _autoBackupAvailable = MutableStateFlow(false)
    val autoBackupAvailable: StateFlow<Boolean> = _autoBackupAvailable

    /**
     * Finished-but-unanalyzed chapters — the count the editor's "분석 안 된 화 n개" banner reports.
     * Grows whenever a chapter is saved without being analyzed: auto-analyze off, the model absent
     * at save time, or a chapter saved during a running rebuild. The chapter currently open in the
     * editor is excluded: autosave persists it as a draft (ingested=false) while the writer is still
     * typing, and a chapter you are actively writing is "in progress", not "finished but unanalyzed"
     * — it starts counting the moment you advance past it (its index is no longer the current one),
     * which is when the writer considers it done.
     * Read straight from the chapters table (not WorkManager) so it survives process death and can
     * never disagree with what a resume would actually pick up — same source [ReplayWorker] itself
     * reads. The banner's *visibility* gate (nothing running, model present, not already showing a
     * failure badge) lives in the composable, which already holds aiStatus/rebuild/model state;
     * this flow is only the count.
     */
    val pendingAnalysisCount: StateFlow<Int> = combine(
        repository.observeChapters(),
        _uiState.map { it.currentChapterIndex }.distinctUntilChanged(),
    ) { chapters, currentIndex ->
        chapters.count { it.body.isNotBlank() && !it.ingested && it.chapterIndex != currentIndex }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Brain/Wiki rebuild banner; also read by [saveAndIngest] to keep the badge on the replay
     * chain while a rebuild is running (the per-save worker defers to it anyway). */
    val rebuildState: StateFlow<RebuildUiState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(ReplayWorker.UNIQUE_NAME),
        repository.observeChapters(),
    ) { infos, chapters ->
        val replayState = aggregateReplayState(infos)
        val eligible = chapters.filter { it.body.isNotBlank() }
        val pendingRemain = eligible.any { !it.ingested }
        RebuildUiState(
            running = replayState != null && !replayState.isFinished,
            failed = replayState == WorkInfo.State.FAILED && pendingRemain,
            ingestedCount = eligible.count { it.ingested },
            totalCount = eligible.size,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RebuildUiState())

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
            // A reopened draft carries its saved title back into the editor; a fresh next chapter
            // starts blank.
            val draftTitle = if (last != null && !last.ingested) last.title.orEmpty() else ""

            _uiState.update {
                it.copy(
                    previousChapters = previous,
                    currentChapterIndex = draftIndex,
                    currentTitle = draftTitle,
                    currentBody = draftBody,
                    wikiEntries = progress.wikiEntries,
                    graphNodes = progress.nodes,
                    graphEdges = progress.edges,
                    orphanIds = progress.orphanIds,
                    isModelAvailable = modelAvailable,
                    isLoaded = true,
                    // A reopened draft was already persisted by a past saveAndIngest, and saving
                    // is what unlocks the next chapter (rule 3) — losing the unlock to a process
                    // restart would force a no-op re-save. Editing resets this via onBodyChange,
                    // same as within a session.
                    canAdvance = draftBody.isNotBlank(),
                )
            }
            // Seed the autosave guard with the reopened draft so the first idle tick after launch is
            // a no-op — init already reflects exactly what is in the chapters table.
            lastPersistedDraft = draftBody.takeIf { it.isNotBlank() }
                ?.let { DraftSnapshot(draftIndex, it, draftTitle) }

            // Cold-start watch target: a replay chain that is still active — or FAILED with
            // chapters actually missing — outranks the last chapter's own work record, because
            // it's the thing whose outcome the user is still waiting on (or must retry).
            val replayState = aggregateReplayState(
                workManager.getWorkInfosForUniqueWorkFlow(ReplayWorker.UNIQUE_NAME).first()
            )
            aiWatch.value = when {
                replayState != null && (!replayState.isFinished || replayState == WorkInfo.State.FAILED) ->
                    AiWatch.Replay
                else -> last?.let { AiWatch.Chapter(it.chapterIndex) }
            }
        }
        observeIngestWork()
        observeLintWork()
        observeChapterFlags()
        observeAutoSave()
        viewModelScope.launch {
            // 복원은 프로세스 재시작 너머에서 완료되므로(StoryBackupManager KDoc 원칙 2),
            // 완료 안내는 새 프로세스의 첫 ViewModel이 마커를 소비해서 띄운다.
            if (StoryBackupManager.consumeRestoreCompletedMarker(getApplication())) {
                _backupState.value = BackupUiState.RestoreCompleted
            }
            _preRestoreAvailable.value = StoryBackupManager.hasPreRestoreBackup(getApplication())
            _autoBackupAvailable.value = StoryBackupManager.hasAutoBackup(getApplication())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeIngestWork() {
        viewModelScope.launch {
            aiWatch
                .filterNotNull()
                .flatMapLatest { watch ->
                    when (watch) {
                        is AiWatch.Chapter ->
                            workManager.getWorkInfosForUniqueWorkFlow(IngestWorker.uniqueNameFor(watch.index))
                        AiWatch.Replay ->
                            workManager.getWorkInfosForUniqueWorkFlow(ReplayWorker.UNIQUE_NAME)
                    }
                }
                .collect { infos ->
                    val state = when (aiWatch.value) {
                        // A replay chain is several WorkInfos (one per self-appended hop); a
                        // per-chapter save is always a single record.
                        AiWatch.Replay -> aggregateReplayState(infos)
                        else -> infos.firstOrNull()?.state
                    }
                    onIngestWorkChanged(state)
                }
        }
    }

    private suspend fun onIngestWorkChanged(state: WorkInfo.State?) {
        if (state != null && !state.isFinished) sessionSawActiveWork = true
        var status = state.toAiStatus(sessionSawActiveWork)
        // A FAILED record can outlive the failure it reported: a later replay pass (rebuild or
        // retry) ingests the chapter out-of-band of the record's own unique work. The DB flag is
        // the truth about whether data is actually missing, so a Warning is only shown while some
        // non-blank chapter genuinely has no ingested data behind it.
        if (status == SmAiStatus.Warning) {
            val anyPending = repository.loadChapters().any { !it.ingested && it.body.isNotBlank() }
            if (!anyPending) status = SmAiStatus.Idle
        }
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
                )
            }
        } else {
            _uiState.update { it.copy(aiStatus = status) }
        }
    }

    /**
     * Re-reads derived state whenever any chapter row changes. This is what makes a rebuild's
     * progress visible as it happens — each replay commit flips one chapter's flag, which lands
     * here and refreshes wiki/graph (design decision 2: the wiki visibly grows back instead of
     * being staged behind a spinner). It also keeps [StoryUiState.lastSaveIngested] truthful from
     * the DB instead of from badge transitions: a rebuild resets and later restores the current
     * chapter's flag, and the lint button must track that, not the last save's outcome.
     */
    private fun observeChapterFlags() {
        viewModelScope.launch {
            repository.observeChapters().collect { chapters ->
                if (!_uiState.value.isLoaded) return@collect
                val progress = repository.loadProgress()
                _uiState.update { state ->
                    val current = chapters.firstOrNull { it.chapterIndex == state.currentChapterIndex }
                    state.copy(
                        wikiEntries = progress.wikiEntries,
                        graphNodes = progress.nodes,
                        graphEdges = progress.edges,
                        orphanIds = progress.orphanIds,
                        lastSaveIngested = current?.ingested == true,
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLintWork() {
        viewModelScope.launch {
            watchedLintChapterIndex
                .flatMapLatest { index ->
                    when (index) {
                        // null은 "관찰 대상 없음"으로 실제 emission을 만들어 이전 구독을 끊는다.
                        // filterNotNull로 null을 삼키면 flatMapLatest가 전환되지 않아 직전
                        // 챕터의 WorkInfo 구독이 살아남는데, WorkManager DB의 무관한 변경(예:
                        // 재저장이 IngestWorker를 enqueue하는 것)만으로도 이미 끝난 린트의
                        // SUCCEEDED WorkInfo가 재방출되어, saveAndIngest가 방금 리셋한 Idle을
                        // 스테일 Done으로 되덮는 버그가 있었다(2026-07 기기 테스트에서 확인).
                        null -> flowOf(emptyList())
                        else -> workManager.getWorkInfosForUniqueWorkFlow(LintWorker.uniqueNameFor(index))
                    }
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

    /** Editing the title, like editing the body, invalidates the saved state: the DB row still
     * holds the old title until the next save, so advancing without re-saving would drop the edit
     * (rule 3 — a save is what persists and unlocks). */
    fun onTitleChange(title: String) {
        _uiState.update { it.copy(currentTitle = title, canAdvance = false) }
    }

    /**
     * 브레인 화면에서 드래그가 끝난 노드의 위치 확정. DB에 영속화하고(탭 전환·재시작을 넘어
     * 살아남도록) 로컬 상태도 같은 값으로 갱신한다 — 챕터 플래그가 안 바뀌는 한
     * [observeChapterFlags]는 다시 읽지 않으므로, 로컬 갱신이 없으면 화면을 떠났다 돌아올 때
     * 옛 좌표로 되돌아가 보인다.
     */
    fun moveNode(id: String, x: Float, y: Float) {
        _uiState.update { state ->
            state.copy(graphNodes = state.graphNodes.map { if (it.id == id) it.copy(x = x, y = y) else it })
        }
        viewModelScope.launch { repository.updateNodePosition(id, x, y) }
    }

    /** The single path that writes the working chapter's manuscript to the chapters table, shared by
     * [saveAndIngest] and [observeAutoSave]. Always `ingested = false` — persisting the manuscript
     * and analyzing it are separate steps (rule 3); analysis is triggered only by an explicit save.
     * Records [lastPersistedDraft] so autosave can skip an unchanged draft. */
    private suspend fun persistDraft(index: Int, label: String, title: String?, body: String) {
        repository.saveChapter(index, label, title = title, body = body, ingested = false)
        lastPersistedDraft = DraftSnapshot(index, body, title.orEmpty())
    }

    /**
     * Debounced autosave: while the writer types, persist the working chapter's body/title to its
     * draft row (ingested=false) so an app kill or crash never loses unsaved typing — the gap that
     * existed because [onBodyChange] only updates in-memory [_uiState], not the DB. It rides the
     * exact same draft mechanism a manual save uses, so a restored draft reopens through init's
     * `!last.ingested` branch with no new table or restore code.
     *
     * Deliberately invisible: it never triggers ingest (that is minutes of on-device generation —
     * only an explicit save should pay it), never touches canAdvance/aiStatus/lint. The manual
     * "저장" button remains the thing that unlocks the next chapter and starts analysis. The guard
     * skips blank bodies and any snapshot equal to the last persisted one (including the seed from
     * init), so a load or an explicit save doesn't provoke a redundant write.
     */
    @OptIn(FlowPreview::class)
    private fun observeAutoSave() {
        viewModelScope.launch {
            _uiState
                .filter { it.isLoaded }
                .map { DraftSnapshot(it.currentChapterIndex, it.currentBody, it.currentTitle) }
                .distinctUntilChanged()
                .debounce(AUTO_SAVE_DEBOUNCE_MS)
                .collect { snap ->
                    if (snap.body.isBlank() || snap == lastPersistedDraft) return@collect
                    persistDraft(snap.index, "${snap.index + 1}화", snap.title.takeIf { it.isNotBlank() }, snap.body)
                }
        }
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
        val title = state.currentTitle.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            persistDraft(chapterIndex, label, title, body)
            _uiState.update { it.copy(canAdvance = true, lastSaveIngested = false) }

            // Any prior lint result belonged to the manuscript that just got overwritten — a
            // stale finding for the old body would misread as a verdict on the new one.
            _lintState.value = LintUiState.Idle
            watchedLintChapterIndex.value = null

            // 자동 분석 OFF: 원고 저장·canAdvance는 그대로 두고(규칙 3) 인제스트만 건너뛴다.
            // 화는 ingested=false로 남아 나중에 재구축이나 재활성화로 채울 수 있다. 모델
            // 미탑재와 같은 무배지(Idle) 상태로 수렴 — 둘 다 "이 화는 아직 분석 안 됨"이다.
            if (!settings.autoAnalyze.value) {
                _uiState.update { it.copy(aiStatus = SmAiStatus.Idle) }
                return@launch
            }

            if (!IngestEngineProvider.isModelAvailable(getApplication())) {
                _uiState.update { it.copy(aiStatus = SmAiStatus.Idle) }
                return@launch
            }

            sessionSawActiveWork = true
            _uiState.update { it.copy(aiStatus = SmAiStatus.Analyzing) }
            IngestWorker.enqueue(getApplication(), chapterIndex)
            // While a rebuild is running the per-save worker above defers to the replay chain
            // (IngestWorker's ordering guard), and the chain is what will actually ingest this
            // chapter — so the badge keeps watching the chain rather than reporting the deferral.
            if (!rebuildState.value.running) {
                aiWatch.value = AiWatch.Chapter(chapterIndex)
            }
        }
    }

    /**
     * Recovery from a FAILED ingest, surfaced as [SmAiStatus.Warning] (see [toAiStatus]'s KDoc for
     * why FAILED doesn't fade quietly like a benign CANCELLED does). Enqueues [ReplayWorker]'s
     * resume — not the single chapter's [IngestWorker] — because the failed chapter may not be the
     * only pending one: any chapter saved after the failure was deferred by the ordering guard,
     * and a mid-rebuild failure leaves everything after the failed chapter pending too. The resume
     * ingests all of them in order; when only the one chapter is pending it degenerates to exactly
     * the old single-chapter retry. Manual-only by design, matching the workers' no-auto-retry
     * policy: a generation attempt costs on the order of a minute, and the same manuscript is
     * likely to hit the same slip again without a human noticing first.
     */
    fun retryIngest() {
        if (_uiState.value.aiStatus != SmAiStatus.Warning) return
        resumeAnalysis()
    }

    /**
     * Analyze every chapter that was saved without being ingested — the editor's "지금 분석"
     * action next to the "분석 안 된 화 n개" banner. Same non-destructive resume as [retryIngest]
     * (it ingests all pending chapters in order via [ReplayWorker]), just reached from an
     * idle-but-incomplete state instead of a FAILED one: chapters pile up unanalyzed when
     * auto-analyze is off or the model was missing at save time, and a full rebuild would be the
     * only other route — needlessly re-ingesting everything already done. Guarded on model
     * availability (the banner is already hidden without it; this is the non-UI backstop).
     */
    fun analyzePending() {
        if (!_uiState.value.isModelAvailable) return
        resumeAnalysis()
    }

    /** Shared by [retryIngest]/[analyzePending]: enqueue the pending-chapter resume and point the
     * badge at the replay chain. The two differ only in the precondition that leads here. */
    private fun resumeAnalysis() {
        sessionSawActiveWork = true
        _uiState.update { it.copy(aiStatus = SmAiStatus.Analyzing) }
        ReplayWorker.enqueueResume(getApplication())
        aiWatch.value = AiWatch.Replay
    }

    /**
     * Wipes all derived data and rebuilds it from the manuscripts, chapter by chapter — see
     * [ReplayWorker] for the operation's definition and ordering guarantees. Guarded on model
     * availability: the reset half would run fine without the model, but then nothing could
     * execute the rebuild half, leaving the user with an empty wiki and no way back until the
     * model file reappears. [SettingsScreen] disables the entry point on the same flag; this
     * check is the non-UI backstop.
     */
    fun startRebuild() {
        if (!_uiState.value.isModelAvailable) return

        // Any lint result references wiki history that is about to be wiped.
        _lintState.value = LintUiState.Idle
        watchedLintChapterIndex.value = null

        sessionSawActiveWork = true
        _uiState.update { it.copy(aiStatus = SmAiStatus.Analyzing) }
        ReplayWorker.enqueueRebuild(getApplication())
        aiWatch.value = AiWatch.Replay
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

    /** SAF [uri]로 전체 원고를 텍스트 내보내기. 원고만 읽는 연산이라 가드가 없다 — 실패해도
     * 잃는 것은 내보내기 한 번뿐이다. */
    fun exportManuscriptTxt(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            val ok = StoryBackupManager.exportManuscriptTxt(getApplication(), uri)
            _backupState.value = if (ok) BackupUiState.TxtExported else BackupUiState.ExportFailed
        }
    }

    /** SAF [uri]로 전체 원고를 Markdown 내보내기. TXT와 같은 이유로 가드 없음. */
    fun exportManuscriptMarkdown(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            val ok = StoryBackupManager.exportManuscriptMarkdown(getApplication(), uri)
            _backupState.value = if (ok) BackupUiState.MdExported else BackupUiState.ExportFailed
        }
    }

    /** SAF [uri]로 DB 전체를 단일 백업 파일로 내보내기 — 방식은 [StoryBackupManager] KDoc. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            val ok = StoryBackupManager.exportBackup(getApplication(), uri)
            _backupState.value = if (ok) BackupUiState.BackupExported else BackupUiState.ExportFailed
        }
    }

    /** 복원 1단계 — 후보 검증까지만. 통과하면 [BackupUiState.RestoreReady]에서 멈춰 사용자
     * 확인을 기다리고, 거부되면 사유와 함께 아무것도 바꾸지 않는다(안전장치 a·b). */
    fun stageRestore(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            _backupState.value = when (val v = StoryBackupManager.stageRestore(getApplication(), uri)) {
                is BackupValidation.Valid -> BackupUiState.RestoreReady(RestoreSource.External)
                is BackupValidation.Invalid -> BackupUiState.RestoreInvalid(v.rejection.userMessage)
            }
        }
    }

    /** 복원 직전 자동 백업(pre-restore)으로 되돌리기 — [stageRestore]와 같은 검증·확인·확정
     * 경로를 타되 후보만 내부 파일이다. 진입점 노출 여부는 [preRestoreAvailable]이 결정한다. */
    fun stageRollback() {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            _backupState.value = when (val v = StoryBackupManager.stagePreRestoreRollback(getApplication())) {
                is BackupValidation.Valid -> BackupUiState.RestoreReady(RestoreSource.PreRestore)
                is BackupValidation.Invalid -> BackupUiState.RestoreInvalid(v.rejection.userMessage)
            }
        }
    }

    /** 최근 주기 자동 백업으로 되돌리기 — 위 둘과 같은 경로, 후보만 최신 자동 백업 파일.
     * 진입점 노출 여부는 [autoBackupAvailable]이 결정한다. */
    fun stageAutoBackupRestore() {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            _backupState.value = when (val v = StoryBackupManager.stageAutoBackupRestore(getApplication())) {
                is BackupValidation.Valid -> BackupUiState.RestoreReady(RestoreSource.AutoBackup)
                is BackupValidation.Invalid -> BackupUiState.RestoreInvalid(v.rejection.userMessage)
            }
        }
    }

    /** 복원 확정 — 안전망 백업과 pending 승격이 성공하면 프로세스를 재시작해 다음 실행이
     * 파일을 교체하게 한다. 성공 경로에서는 이 프로세스가 끝나므로 이후 상태 갱신이 없다. */
    fun confirmRestore() {
        if (_backupState.value !is BackupUiState.RestoreReady) return
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            if (StoryBackupManager.promoteStagedRestore(getApplication())) {
                StoryBackupManager.restartProcess(getApplication())
            } else {
                _backupState.value = BackupUiState.RestoreFailed
            }
        }
    }

    fun cancelRestore() {
        viewModelScope.launch {
            StoryBackupManager.discardStagedRestore(getApplication())
            _backupState.value = BackupUiState.Idle
        }
    }

    fun dismissBackupState() {
        _backupState.value = BackupUiState.Idle
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
            title = state.currentTitle.takeIf { it.isNotBlank() },
            body = state.currentBody,
            ingested = state.lastSaveIngested,
        )
        _uiState.update {
            it.copy(
                previousChapters = it.previousChapters + justSaved,
                currentChapterIndex = it.currentChapterIndex + 1,
                currentTitle = "",
                currentBody = "",
                aiStatus = SmAiStatus.Idle,
                canAdvance = false,
                lastSaveIngested = false,
            )
        }
    }

    private companion object {
        /** How long typing must pause before an autosave fires. Long enough not to write on every
         * keystroke, short enough that a crash loses at most a sentence or two. */
        const val AUTO_SAVE_DEBOUNCE_MS = 1_500L

        /**
         * Collapses a replay chain's WorkInfos (one per self-appended hop, so usually several)
         * into the single state [toAiStatus] understands. Any hop still pending means the chain
         * is running; otherwise one FAILED hop means the whole rebuild stopped there — the
         * remaining hops were never created, so "all finished + one FAILED" is the chain's true
         * terminal state, not a partial success.
         */
        fun aggregateReplayState(infos: List<WorkInfo>): WorkInfo.State? = when {
            infos.isEmpty() -> null
            infos.any { !it.state.isFinished } -> WorkInfo.State.RUNNING
            infos.any { it.state == WorkInfo.State.FAILED } -> WorkInfo.State.FAILED
            infos.any { it.state == WorkInfo.State.CANCELLED } -> WorkInfo.State.CANCELLED
            else -> WorkInfo.State.SUCCEEDED
        }
    }
}
