package com.example.storymind.ui.theme

import androidx.compose.ui.graphics.Color

/* ── Brand Blue (Toss Blue base) ── */
val Blue50 = Color(0xFFEBF0FF)
val Blue100 = Color(0xFFD6E2FF)
val Blue200 = Color(0xFFADCDFF)
val Blue300 = Color(0xFF85ACFF)
val Blue400 = Color(0xFF4D79F8)
val Blue500 = Color(0xFF1F4EF5) // Brand main
val Blue600 = Color(0xFF1940D4)
val Blue700 = Color(0xFF1533A8)
val Blue800 = Color(0xFF0F2580)

/* ── Neutral (blue-tinted gray — never pure black) ── */
val Neutral50 = Color(0xFFF8F9FC)
val Neutral100 = Color(0xFFF2F4F9)
val Neutral200 = Color(0xFFE5E7EF)
val Neutral300 = Color(0xFFC8CBDA)
val Neutral400 = Color(0xFF9EA3B3)
val Neutral500 = Color(0xFF6B7089)
val Neutral600 = Color(0xFF5C5F6B)
val Neutral700 = Color(0xFF3D404D)
val Neutral800 = Color(0xFF2D2F36)
val Neutral900 = Color(0xFF22242C)
val Neutral950 = Color(0xFF1A1B1E) // Primary text — spec mandated, NEVER pure black

/* ── Warning / Error (pastel, never aggressive red) ── */
val Red50 = Color(0xFFFFF0F0)
val Red100 = Color(0xFFFFD6D6)
val Red200 = Color(0xFFFFA8A8)
val Red400 = Color(0xFFF87171)
val Red500 = Color(0xFFEF4444)

/* ── Success / Place nodes ── */
val Green50 = Color(0xFFF0FFF4)
val Green100 = Color(0xFFDCFCE7)
val Green500 = Color(0xFF22C55E)
val Green600 = Color(0xFF16A34A)

/* ── Item nodes ── */
val Amber50 = Color(0xFFFFFBEB)
val Amber100 = Color(0xFFFEF3C7)
val Amber500 = Color(0xFFF59E0B)
val Amber600 = Color(0xFFD97706)

/* ── Event nodes ── */
val Purple50 = Color(0xFFF5F3FF)
val Purple100 = Color(0xFFEDE9FE)
val Purple500 = Color(0xFF8B5CF6)
val Purple600 = Color(0xFF7C3AED)

/**
 * StoryMind semantic palette — mirrors project/tokens/colors.css.
 * Only "day" (낮) atmosphere is implemented; the prototype's dusk/night
 * atmosphere swaps are left as a future extension point.
 */
object SmColors {
    val brand = Blue500
    val brandHover = Blue600
    val brandActive = Blue700
    val brandSubtle = Blue50

    val textPrimary = Neutral950
    val textSecondary = Neutral600
    val textTertiary = Neutral400
    val textDisabled = Neutral300
    val textPlaceholder = Neutral400
    val textOnBrand = Color.White

    val surfaceBase = Color.White
    val surfaceSubtle = Neutral50
    val surfaceCard = Color.White
    val surfaceWarning = Red50

    val borderDefault = Neutral200
    val borderStrong = Neutral300
    val borderBrand = Blue500
    val borderWarning = Red200

    val nodeCharacter = Blue500
    val nodeCharacterBg = Blue50
    val nodePlace = Green500
    val nodePlaceBg = Green50
    val nodeItem = Amber500
    val nodeItemBg = Amber50
    val nodeEvent = Purple500
    val nodeEventBg = Purple50
    val nodeOrphan = Red500
    val nodeOrphanBg = Red50

    val edgeLine = Neutral300
}