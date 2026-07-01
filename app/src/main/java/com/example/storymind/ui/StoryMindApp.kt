package com.example.storymind.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.storymind.data.MockData
import com.example.storymind.ui.components.SmAiStatus
import com.example.storymind.ui.components.SmBottomSheet
import com.example.storymind.ui.components.SmNavBar
import com.example.storymind.ui.components.SmSheetAction
import com.example.storymind.ui.components.SmTab
import com.example.storymind.ui.components.SmWikiDrawer
import com.example.storymind.ui.screens.BrainScreen
import com.example.storymind.ui.screens.EditorScreen
import com.example.storymind.ui.screens.SettingsScreen
import com.example.storymind.ui.screens.WikiScreen

/**
 * App root — mirrors the prototype's App component: tab switching, the
 * editor's AI status/conflict-warning timeline, the wiki drawer, and the
 * warning bottom sheet.
 */
@Composable
fun StoryMindApp(modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(SmTab.Editor) }
    var warningOpen by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var aiStatus by remember { mutableStateOf(SmAiStatus.Analyzing) }

    LaunchedEffect(tab) {
        if (tab == SmTab.Editor) {
            aiStatus = SmAiStatus.Analyzing
            kotlinx.coroutines.delay(3000)
            aiStatus = SmAiStatus.Warning
            kotlinx.coroutines.delay(500)
            shakeTrigger++
            warningOpen = true
        } else {
            aiStatus = SmAiStatus.Analyzing
            warningOpen = false
            drawerOpen = false
        }
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxSize().weight(1f, fill = true)) {
            when (tab) {
                SmTab.Editor -> EditorScreen(
                    aiStatus = aiStatus,
                    shakeTrigger = shakeTrigger,
                    isEmpty = false,
                    onWikiOpen = { drawerOpen = true },
                )
                SmTab.Brain -> BrainScreen()
                SmTab.Wiki -> WikiScreen()
                SmTab.Settings -> SettingsScreen()
            }

            SmWikiDrawer(
                isOpen = drawerOpen,
                onClose = { drawerOpen = false },
                entries = MockData.wiki,
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