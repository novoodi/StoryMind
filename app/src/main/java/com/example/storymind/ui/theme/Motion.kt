package com.example.storymind.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Motion tokens — mirrors project/tokens/effects.css easing curves & durations. */
object SmEasing {
    val standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val accelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val bounce: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val out: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
}

object SmDuration {
    const val instant = 80
    const val fast = 150
    const val normal = 200 // shake animation (spec)
    const val slow = 280 // drawer slide, bottom sheet (spec)
    const val deliberate = 350 // graph transitions
}