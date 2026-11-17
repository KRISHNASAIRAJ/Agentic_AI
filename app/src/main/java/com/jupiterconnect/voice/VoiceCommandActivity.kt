package com.jupiterconnect.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jupiterconnect.transport.ClusterRepository
import com.jupiterconnect.ui.theme.JupiterConnectTheme
import kotlinx.coroutines.launch
import java.util.Locale

/** Small listening screen launched by the cluster's voice/ISS trigger. */
class VoiceCommandActivity : ComponentActivity() {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val status = mutableStateOf("Listening…")
    private val processor by lazy { VoiceCommandProcessor(this) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else {
                status.value = "Microphone permission is needed for voice commands"
                finishSoon()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JupiterConnectTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Jupiter voice", style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(16.dp))
                        Text(status.value, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { finish() }) { Text("Cancel") }
                    }
                }
            }
        }

        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) tts?.setLanguage(Locale.ENGLISH)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.value = "Voice recognition is not available on this phone"
            finishSoon()
            return
        }
        val listener = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { recognizer = it }
        listener.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.value = "Listening…" }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { status.value = "Processing…" }
            override fun onError(error: Int) {
                status.value = "Could not hear you"
                finishSoon()
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (spoken.isBlank()) {
                    status.value = "Could not hear you"
                    finishSoon()
                    return
                }
                status.value = spoken
                lifecycleScope.launch {
                    val reply = processor.process(spoken)
                    status.value = reply
                    tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "jupiter_voice_reply")
                    ClusterRepository.showText(reply)
                    finishSoon()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listener.startListening(intent)
    }

    private fun finishSoon() {
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2500)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
