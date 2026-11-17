package dev.tvsopen.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Fade + slide entrance for a section. Short and non-blocking. */
@Composable
fun Modifier.enterAnimated(delayMs: Int = 0): Modifier {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 380, delayMillis = delayMs,
            easing = FastOutSlowInEasing),
        label = "enter-alpha",
    )
    val slide by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 380, delayMillis = delayMs,
            easing = FastOutSlowInEasing),
        label = "enter-slide",
    )
    return this.graphicsLayer(alpha = alpha, translationY = slide)
}

/** A gentle pulse for a "live" dot (alpha 1 -> 0.35 -> 1). */
@Composable
fun rememberPulseAlpha(): () -> Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )
    return { alpha }
}

/** Convenience: smooth crossfade + vertical swap for changing text values. */
@Composable
fun AnimatedValue(text: String, label: String) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(animationSpec = tween(180)) + slideInVertically { it / 3 }) togetherWith
                (fadeOut(animationSpec = tween(120)) + slideOutVertically { -it / 3 })
        },
        label = label,
    ) { value ->
        androidx.compose.material3.Text(value)
    }
}
