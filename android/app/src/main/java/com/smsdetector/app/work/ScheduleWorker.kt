package com.smsdetector.app.work

import android.content.Context
import android.util.Log
import androidx.work.*
import com.smsdetector.app.SmsDetectorApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Sends due scheduled messages. Re-scheduled on boot by BootReceiver. */
class ScheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SmsDetectorApplication
        val dao = app.db.scheduledDao()
        val pending = try { dao.observePending().first() } catch (e: Exception) {
            Log.e("ScheduleWorker", "Cannot read scheduled messages", e)
            return Result.failure()
        }
        val now = System.currentTimeMillis()
        for (item in pending.filter { it.scheduledAt <= now }) {
            try {
                app.repository.sendSms(item.address, item.body)
                if (item.isRecurring) {
                    dao.upsert(item.copy(id = 0, scheduledAt = nextOccurrence(item.scheduledAt, item.recurringLabel)))
                }
                dao.setStatus(item.id, "SENT")
            } catch (e: Exception) {
                Log.e("ScheduleWorker", "Failed to send scheduled SMS", e)
            }
        }
        return Result.success()
    }

    private fun nextOccurrence(at: Long, label: String?): Long {
        val day = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)
        return when (label) {
            "Weekly" -> at + day * 7
            "Monthly" -> at + day * 30
            else -> at + day
        }
    }

    companion object {
        fun enqueueAll(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("sms-scheduler", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
