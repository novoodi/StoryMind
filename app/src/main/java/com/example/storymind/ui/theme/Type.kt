package com.example.storymind.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.storymind.R

/** Pretendard — Korean-optimized geometric sans-serif. Mirrors tokens/fonts.css. */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/** Letter spacing tokens — mirrors tokens/typography.css tracking scale. */
object SmTracking {
    val tight = (-0.03).em
    val normal = (-0.01).em
    val wide = 0.02.em
}

/** Line height tokens — mirrors tokens/typography.css leading scale. */
object SmLeading {
    const val tight = 1.2f
    const val snug = 1.35f
    const val normal = 1.5f
    const val relaxed = 1.65f
    const val loose = 1.8f
}

/** Text style scale — mirrors tokens/typography.css size scale, all on Pretendard. */
object SmType {
    val displayHero = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.Bold,
        fontSize = 38.sp, lineHeight = (38 * SmLeading.tight).sp, letterSpacing = SmTracking.tight,
    )
    val chapterTitle = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = (32 * SmLeading.tight).sp, letterSpacing = SmTracking.tight,
    )
    val pageTitle = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = (28 * SmLeading.tight).sp, letterSpacing = SmTracking.tight,
    )
    val sectionHeader = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = (24 * SmLeading.snug).sp, letterSpacing = SmTracking.tight,
    )
    val toolbarTitle = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = SmTracking.normal,
    )
    val bodyEditor = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = (18 * SmLeading.loose).sp, letterSpacing = SmTracking.normal,
    )
    val bodyMd = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = (16 * SmLeading.relaxed).sp, letterSpacing = SmTracking.normal,
    )
    val label = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = (14 * SmLeading.normal).sp, letterSpacing = SmTracking.normal,
    )
    val labelMedium = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = (14 * SmLeading.normal).sp, letterSpacing = SmTracking.normal,
    )
    val caption = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = (12 * SmLeading.normal).sp, letterSpacing = SmTracking.normal,
    )
    val tiny = TextStyle(
        fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = SmTracking.wide,
    )
}

// Material3 typography bridge — keeps default Text() calls on-brand even
// where a screen doesn't reach for SmType explicitly.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
)