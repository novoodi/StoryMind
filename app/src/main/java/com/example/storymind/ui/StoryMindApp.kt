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
import com.example.storymind.ui.components.SmNavBar
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

    var tab by remember { mutableStateOf(SmTab.Editor) }
    var warningOpen by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab) {
        if (tab != SmTab.Editor) {
            warningOpen = false
            drawerOpen = false
        }
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
                )
                SmTab.Brain -> BrainScreen(
                    nodes = uiState.graphNodes,
                    edges = uiState.graphEdges,
                    orphanIds = uiState.orphanIds,
                    wikiEntries = uiState.wikiEntries,
                )
                SmTab.Wiki -> WikiScreen(entries = uiState.wikiEntries)
                SmTab.Settings -> SettingsScreen()
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
        }
        SmNavBar(active = tab, onChange = { tab = it }, modifier = Modifier.navigationBarsPadding())
    }
}