package com.example.storymind.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.ui.components.SmToolbar
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors

/** App preferences — mirrors the prototype's SettingsScreen. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var spellCheck by remember { mutableStateOf(true) }
    var autoAnalyze by remember { mutableStateOf(true) }
    var conflictAlert by remember { mutableStateOf(true) }
    var autoWiki by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(SmColors.surfaceBase)) {
        SmToolbar(title = "설정")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            SectionLabel("에디터")
            SettingsRow(label = "글꼴 크기", value = "18px")
            SettingsRow(label = "줄 간격", value = "1.8")
            SettingsRow(label = "맞춤법 검사", checked = spellCheck, onCheckedChange = { spellCheck = it })

            SectionLabel("AI 기능")
            SettingsRow(label = "자동 분석", checked = autoAnalyze, onCheckedChange = { autoAnalyze = it })
            SettingsRow(label = "설정 충돌 알림", checked = conflictAlert, onCheckedChange = { conflictAlert = it })
            SettingsRow(label = "위키 자동 생성", checked = autoWiki, onCheckedChange = { autoWiki = it })

            SectionLabel("계정")
            SettingsRow(label = "구독 플랜", value = "스탠다드")
            SettingsRow(label = "데이터 백업", value = "방금 전")
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        color = SmColors.textTertiary,
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = SmColors.textPrimary,
                fontFamily = Pretendard,
                fontSize = 16.sp,
            )
            if (checked != null && onCheckedChange != null) {
                SmToggle(on = checked, onClick = { onCheckedChange(!checked) })
            } else if (value != null) {
                Text(
                    text = value,
                    color = SmColors.textTertiary,
                    fontFamily = Pretendard,
                    fontSize = 14.sp,
                )
            }
        }
        HorizontalDivider(color = SmColors.borderDefault, thickness = 1.dp)
    }
}

@Composable
private fun SmToggle(on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (on) SmColors.brand else SmColors.borderStrong)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.White)
        )
    }
}