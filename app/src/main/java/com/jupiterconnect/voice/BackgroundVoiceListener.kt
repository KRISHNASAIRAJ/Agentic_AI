package com.jupiterconnect.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.jupiterconnect.debug.FrameLog
import com.jupiterconnect.transport.ClusterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Voice input straight from the foreground service, with no screen.
 *
 * When the bike's voice button is pressed while Jupiter Connect is in the
 * background, the mic must turn on immediately and stay on until the rider
 * says something. Opening a UI screen from the background is unreliable on
 * OEM ROMs and left a queued "speak now" prompt when the app was later opened;
 * listening here avoids both problems. The existing VoiceCommandActivity is
 * still used when the app is already in the foreground.
 */
class BackgroundVoiceListener(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val processor = VoiceCommandProcessor(context)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var active = false

    /** Called from the BLE callback; speech APIs must run on the main thread. */
    fun start() {
        main.post { startOnMain() }
    }

    private fun startOnMain() {
        if (active) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            FrameLog.note("VOICE bg skipped: RECORD_AUDIO not granted")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            FrameLog.note("VOICE bg skipped: speech recognition unavailable")
            return
        }
        active = true
        FrameLog.note("VOICE bg listening from service (no screen)")
        if (tts == null) {
            tts = TextToSpeech(context) { result ->
                if (result == TextToSpeech.SUCCESS) tts?.setLanguage(Locale.ENGLISH)
            }
        }
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) =
                FrameLog.note("VOICE bg mic ready")
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = FrameLog.note("VOICE bg processing")
            override fun onError(error: Int) {
                FrameLog.note("VOICE bg error $error")
                finish()
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (spoken.isBlank()) {
                    FrameLog.note("VOICE bg heard nothing")
                    finish()
                    return
                }
                FrameLog.note("VOICE bg heard: $spoken")
                scope.launch {
                    val reply = processor.process(spoken)
                    FrameLog.note("VOICE bg reply: $reply")
                    tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "jupiter_voice_reply")
                    // Show the short answer on the bike as well as saying it.
                    ClusterRepository.showText(reply)
                    finish()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { rec ->
            rec.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching { rec.startListening(intent) }
                .onFailure {
                    FrameLog.note("VOICE bg start failed: ${it.message}")
                    finish()
                }
        }
    }

    /** Release the mic as soon as the command has been handled. */
    private fun finish() {
        main.post {
            runCatching { recognizer?.stopListening() }
            recognizer?.destroy()
            recognizer = null
            active = false
        }
    }

    fun destroy() {
        finish()
        tts?.shutdown()
        tts = null
        scope.cancel()
    }
}
