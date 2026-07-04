package com.example.storymind.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.storymind.data.WikiEntry
import com.example.storymind.ui.components.SmBadge
import com.example.storymind.ui.components.SmCard
import com.example.storymind.ui.components.SmToolbar
import com.example.storymind.ui.theme.SmColors

/** Auto-generated setting-bible list — mirrors the prototype's WikiScreen. */
@Composable
fun WikiScreen(
    entries: List<WikiEntry>,
    modifier: Modifier = Modifier,
    rebuildBanner: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize().background(SmColors.surfaceBase)) {
        // 검색 버튼은 검색 기능이 아직 없어 제거 — 눌러도 아무 일도 안 하던 장식.
        SmToolbar(title = "위키")
        rebuildBanner?.invoke()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries) { entry ->
                SmCard(
                    title = entry.name,
                    badge = { SmBadge(type = entry.type) },
                    description = entry.desc,
                    meta = "마지막 등장: ${entry.chapter}",
                )
            }
        }
    }
}