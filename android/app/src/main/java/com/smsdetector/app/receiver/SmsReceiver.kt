package com.smsdetector.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.smsdetector.app.SmsDetectorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives SMS_RECEIVED, classifies on-device, and updates the local store. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val app = context.applicationContext as SmsDetectorApplication
        val repo = app.repository

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (sms in messages) {
                    val sender = sms.originatingAddress ?: continue
                    val body = sms.messageBody ?: continue
                    repo.handleIncoming(sender, body, sms.timestampMillis)
                    Log.d("SmsReceiver", "Classified SMS from $sender")
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Failed to process SMS", e)
            } finally {
                pending.finish()
            }
        }
    }
}
