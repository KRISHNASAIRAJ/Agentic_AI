package com.jupiterconnect.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Fade + rise entrance for a section, with an optional stagger delay. */
@Composable
fun Modifier.enterAnimated(delayMs: Int = 0): Modifier {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(380, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label = "enter-alpha",
    )
    val slide by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(380, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label = "enter-slide",
    )
    return this.graphicsLayer(alpha = alpha, translationY = slide)
}

/** A gentle 1 -> 0.35 -> 1 pulse for a live indicator. */
@Composable
fun rememberPulseAlpha(): () -> Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    return { alpha }
}
