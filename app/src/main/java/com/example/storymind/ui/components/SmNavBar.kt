package com.example.storymind.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storymind.ui.icons.SmIcon
import com.example.storymind.ui.icons.SmIcons
import com.example.storymind.ui.theme.Pretendard
import com.example.storymind.ui.theme.SmColors
import com.example.storymind.ui.theme.SmSpacing

enum class SmTab(val icon: Int, val label: String) {
    Editor(SmIcons.Edit, "에디터"),
    Brain(SmIcons.Brain, "브레인"),
    Wiki(SmIcons.Book, "위키"),
    Statistics(SmIcons.Chart, "통계"),
    Settings(SmIcons.Gear, "설정"),
}

/** Fixed bottom tab bar — 50px per spec, 22×22 icons. Mirrors components/navigation/NavBar.jsx. */
@Composable
fun SmNavBar(active: SmTab, onChange: (SmTab) -> Unit, modifier: Modifier = Modifier) {
    val borderColor = SmColors.borderDefault
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SmSpacing.nav)
            .background(SmColors.surfaceBase)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        SmTab.entries.forEach { tab ->
            val on = tab == active
            val interaction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onChange(tab) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SmIcon(
                    id = tab.icon,
                    tint = if (on) SmColors.brand else SmColors.textTertiary,
                    size = SmSpacing.navIcon,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    color = if (on) SmColors.brand else SmColors.textTertiary,
                    fontFamily = Pretendard,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp,
                )
            }
        }
    }
}