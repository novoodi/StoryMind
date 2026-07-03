package com.example.storymind.ui

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
        }
        SmNavBar(active = tab, onChange = { tab = it }, modifier = Modifier.navigationBarsPadding())
    }
}