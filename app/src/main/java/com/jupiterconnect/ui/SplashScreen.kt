package com.jupiterconnect.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiterconnect.session.CloudGarageStore
import com.jupiterconnect.session.RideStore
import com.jupiterconnect.sync.SupabaseClient
import com.jupiterconnect.transport.ClusterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TitleWhite = Color(0xFFFFFFFF)
private val SubtitleGrey = Color(0xFF8A8A8A)

/**
 * Five-second branded splash. The animation and cloud/BLE initialization run
 * in parallel; navigation happens only when both are ready (or at 5.5 s).
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val time = remember { Animatable(0f) }
    val initReady = remember { mutableStateOf(false) }
    val finished = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            ) == 0f
        }.getOrDefault(false)
    }

    fun finish() {
        if (finished.value) return
        finished.value = true
        onDone()
    }

    LaunchedEffect(Unit) {
        // BLE auto-connect and data loading happen while the animation plays.
        withContext(Dispatchers.IO) {
            ClusterRepository.initPrefs(context)
            RideStore.init(context)
            SupabaseClient.init(context)
            CloudGarageStore.init(context)
            runCatching { ClusterRepository.startService(context) }
        }
        initReady.value = true
        if (reduceMotion) {
            // "Remove animations": show the final frame for one second.
            time.snapTo(5000f)
            delay(1000L)
        } else {
            val started = System.currentTimeMillis()
            time.animateTo(5000f, tween(5000, easing = LinearEasing))
            while (!initReady.value && System.currentTimeMillis() - started < 5500L) {
                delay(50L)
            }
        }
        finish()
    }

    SplashFrame(
        timeMs = { time.value },
        modifier = Modifier.clickable {
            scope.launch {
                runCatching { time.snapTo(5000f) }
                finish()
            }
        },
    )
}

/** The whole visual frame, driven by one elapsed-time supplier. */
@Composable
fun SplashFrame(
    timeMs: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                val t = timeMs()
                alpha = 1f - ((t - 4700f) / 300f).coerceIn(0f, 1f)
            },
    ) {
        ScooterSplash(timeMs, Modifier.fillMaxSize())

        Column(
            Modifier.align(Alignment.Center).offset(y = 170.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val title = "JUPITER CONNECT"
            Row(Modifier.graphicsLayer {
                val p = ((timeMs() - 3000f) / 800f).coerceIn(0f, 1f)
                scaleX = 1f + (1f - p) * 0.25f
            }) {
                title.forEachIndexed { i, ch ->
                    Text(
                        ch.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = TitleWhite,
                        modifier = Modifier.graphicsLayer {
                            val a = ((timeMs() - 3000f - i * 30f) / 300f).coerceIn(0f, 1f)
                            alpha = a
                            translationY = (1f - a) * 8f
                        },
                    )
                }
            }
            Text(
                "JUPITER 125 · SMARTXONNECT",
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = SubtitleGrey,
                modifier = Modifier.graphicsLayer {
                    alpha = ((timeMs() - 3800f) / 600f).coerceIn(0f, 1f)
                },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0x000000)
@Composable
private fun SplashFirstFramePreview() = SplashFrame({ 200f })

@Preview(showBackground = true, backgroundColor = 0x000000)
@Composable
private fun SplashMiddleFramePreview() = SplashFrame({ 1500f })

@Preview(showBackground = true, backgroundColor = 0x000000)
@Composable
private fun SplashFinalFramePreview() = SplashFrame({ 4600f })
