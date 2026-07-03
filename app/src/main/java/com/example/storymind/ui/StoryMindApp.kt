package com.example.storymind.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.storymind.ui.components.SmBottomSheet
import com.example.storymind.ui.components.SmLintResultSheet
import com.example.storymind.ui.components.SmNavBar
import com.example.storymind.ui.components.SmRebuildBanner
import com.example.storymind.ui.components.SmSheetAction
import com.example.storymind.ui.components.SmTab
import com.example.storymind.ui.components.SmWikiDrawer
import com.example.storymind.ui.screens.BrainScreen
import com.example.storymind.ui.screens.EditorChapterSnapshot
import com.example.storymind.ui.screens.EditorScreen
import com.example.storymind.ui.screens.SettingsScreen
import com.example.storymind.ui.screens.WikiScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App root — mirrors the prototype's App component: tab switching, the
 * editor's AI status, the wiki drawer, and the warning bottom sheet.
 *
 * Writing/ingest state (chapters, wiki, graph, AI status) is owned by [StoryViewModel],
 * which persists to Room so it survives process death. Only pure navigation state
 * (active tab, drawer/sheet open, shake replay) stays local to this composable.
 */
@Composable
fun StoryMindApp(modifier: Modifier = Modifier) {
    val viewModel: StoryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val lintState by viewModel.lintState.collectAsState()
    val rebuildState by viewModel.rebuildState.collectAsState()
    val backupState by viewModel.backupState.collectAsState()

    // SAF 계약들. CreateDocument의 파일명 기본값에 날짜를 넣는 것은 launch 시점에 계산한다
    // (컴포지션 시점에 고정하면 자정을 넘긴 세션에서 어제 날짜가 제안된다).
    val txtExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let(viewModel::exportManuscriptTxt) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::stageRestore) }
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    var tab by remember { mutableStateOf(SmTab.Editor) }
    var warningOpen by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var lintSheetOpen by remember { mutableStateOf(false) }
    var rebuildConfirmOpen by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab) {
        if (tab != SmTab.Editor) {
            warningOpen = false
            drawerOpen = false
            lintSheetOpen = false
        }
        if (tab != SmTab.Settings) {
            rebuildConfirmOpen = false
        }
    }

    val rebuildBanner: @Composable () -> Unit = {
        SmRebuildBanner(
            running = rebuildState.running,
            failed = rebuildState.failed,
            ingestedCount = rebuildState.ingestedCount,
            totalCount = rebuildState.totalCount,
        )
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxSize().weight(1f, fill = true)) {
            when (tab) {
                SmTab.Editor -> EditorScreen(
                    aiStatus = uiState.aiStatus,
                    shakeTrigger = shakeTrigger,
                    canAdvance = uiState.canAdvance,
                    previousChapters = uiState.previousChapters.map {
                        EditorChapterSnapshot(label = it.label, title = it.title, body = it.body)
                    },
                    currentLabel = uiState.currentLabel,
                    currentBody = uiState.currentBody,
                    onBodyChange = viewModel::onBodyChange,
                    onSave = viewModel::saveAndIngest,
                    onNextChapter = viewModel::startNextChapter,
                    onWikiOpen = { drawerOpen = true },
                    canLint = uiState.lastSaveIngested,
                    lintRunning = lintState is LintUiState.Running,
                    onLint = {
                        viewModel.lintCurrentChapter()
                        lintSheetOpen = true
                    },
                    onRetryIngest = viewModel::retryIngest,
                )
                SmTab.Brain -> BrainScreen(
                    nodes = uiState.graphNodes,
                    edges = uiState.graphEdges,
                    orphanIds = uiState.orphanIds,
                    wikiEntries = uiState.wikiEntries,
                    rebuildBanner = rebuildBanner,
                )
                SmTab.Wiki -> WikiScreen(
                    entries = uiState.wikiEntries,
                    rebuildBanner = rebuildBanner,
                )
                SmTab.Settings -> SettingsScreen(
                    rebuildRunning = rebuildState.running,
                    rebuildProgressLabel = "재구축 중 ${rebuildState.ingestedCount}/${rebuildState.totalCount}화",
                    canRebuild = uiState.isModelAvailable,
                    onRebuildRequest = { rebuildConfirmOpen = true },
                    backupBusy = backupState == BackupUiState.Working,
                    onExportTxt = { txtExportLauncher.launch("storymind-원고-${today()}.txt") },
                    onExportBackup = { backupExportLauncher.launch("storymind-backup-${today()}.db") },
                    // OpenDocument의 마임 필터가 "*/*"인 이유: .db에는 표준 마임타입이 없어
                    // 좁은 필터로는 문서 프로바이더 대부분이 방금 내보낸 백업조차 회색 처리한다.
                    // 잘못된 파일 선택은 어차피 검증(안전장치 a)이 막는다.
                    onImportBackup = { restoreLauncher.launch(arrayOf("*/*")) },
                )
            }

            SmWikiDrawer(
                isOpen = drawerOpen,
                onClose = { drawerOpen = false },
                entries = uiState.wikiEntries,
            )

            SmBottomSheet(
                isOpen = warningOpen,
                onClose = { warningOpen = false },
                title = "이야기가 맞지 않아요",
                description = "박민준이 2장에서 이미 자리를 떠났는데, 여기서 다시 등장하고 있어요. 설정을 확인해볼까요?",
                primaryAction = SmSheetAction("설정 확인하기") {
                    warningOpen = false
                    tab = SmTab.Wiki
                },
                secondaryAction = SmSheetAction("그냥 둘래요") { warningOpen = false },
            )

            SmLintResultSheet(
                isOpen = lintSheetOpen,
                onClose = { lintSheetOpen = false },
                state = lintState,
            )

            SmBottomSheet(
                isOpen = rebuildConfirmOpen,
                onClose = { rebuildConfirmOpen = false },
                title = "AI 데이터를 재구축할까요?",
                description = "위키와 관계 그래프를 지우고 원고 전체에서 다시 만듭니다. " +
                    "화당 몇 분씩 걸리며 원고는 변경되지 않습니다.",
                conflictLabel = "위키·그래프가 초기화된 뒤 다시 채워져요",
                primaryAction = SmSheetAction("재구축 시작") {
                    rebuildConfirmOpen = false
                    viewModel.startRebuild()
                },
                secondaryAction = SmSheetAction("취소") { rebuildConfirmOpen = false },
            )

            // 백업/내보내기 결과·확인 시트. RestoreReady만 실제 결정을 묻는 2버튼 확인이고
            // (안전장치 b — 여기서 확정해야만 confirmRestore가 진행된다), 나머지는 결과 안내다.
            when (val bs = backupState) {
                BackupUiState.Idle, BackupUiState.Working -> Unit
                BackupUiState.RestoreReady -> SmBottomSheet(
                    isOpen = true,
                    onClose = viewModel::cancelRestore,
                    title = "백업을 가져올까요?",
                    description = "현재 데이터를 백업 파일 내용으로 교체합니다. 지금 데이터는 " +
                        "자동으로 임시 보관됩니다. 교체가 끝나면 앱이 다시 시작돼요.",
                    conflictLabel = "현재 원고·위키가 백업 내용으로 바뀌어요",
                    primaryAction = SmSheetAction("교체하고 다시 시작") { viewModel.confirmRestore() },
                    secondaryAction = SmSheetAction("취소") { viewModel.cancelRestore() },
                )
                is BackupUiState.RestoreInvalid -> BackupInfoSheet(
                    title = "백업 파일을 사용할 수 없어요",
                    description = "${bs.reason} 기존 데이터는 아무것도 바뀌지 않았어요.",
                    onDismiss = viewModel::dismissBackupState,
                )
                BackupUiState.RestoreFailed -> BackupInfoSheet(
                    title = "복원을 시작하지 못했어요",
                    description = "안전 백업 단계에서 실패해 아무것도 바꾸지 않았어요. 다시 시도해 주세요.",
                    onDismiss = viewModel::dismissBackupState,
                )
                BackupUiState.RestoreCompleted -> BackupInfoSheet(
                    title = "복원이 끝났어요",
                    description = "백업 데이터로 교체됐어요. 위키·그래프가 이상해 보이면 설정의 " +
                        "'AI 데이터 재구축'으로 원고에서 다시 만들 수 있어요.",
                    onDismiss = viewModel::dismissBackupState,
                )
                BackupUiState.TxtExported -> BackupInfoSheet(
                    title = "원고를 내보냈어요",
                    description = "선택한 위치에 전체 원고가 텍스트 파일로 저장됐어요.",
                    onDismiss = viewModel::dismissBackupState,
                )
                BackupUiState.BackupExported -> BackupInfoSheet(
                    title = "백업 파일을 내보냈어요",
                    description = "원고·위키·그래프가 모두 담긴 파일이에요. 안전한 곳에 보관하세요.",
                    onDismiss = viewModel::dismissBackupState,
                )
                BackupUiState.ExportFailed -> BackupInfoSheet(
                    title = "내보내기에 실패했어요",
                    description = "파일을 쓰는 중 문제가 생겼어요. 다른 위치로 다시 시도해 주세요.",
                    onDismiss = viewModel::dismissBackupState,
                )
            }
        }
        SmNavBar(active = tab, onChange = { tab = it }, modifier = Modifier.navigationBarsPadding())
    }
}

/** 단일 "확인" 버튼 안내 시트 — 백업/내보내기 결과는 결정을 요구하지 않으므로 취소 버튼과
 * 경고 칩을 뺀다(SmBottomSheet KDoc 참고). */
@Composable
private fun BackupInfoSheet(title: String, description: String, onDismiss: () -> Unit) {
    SmBottomSheet(
        isOpen = true,
        onClose = onDismiss,
        title = title,
        description = description,
        conflictLabel = null,
        primaryAction = SmSheetAction("확인", onDismiss),
    )
}