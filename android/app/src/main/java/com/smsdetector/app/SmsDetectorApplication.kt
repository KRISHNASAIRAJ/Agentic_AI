package com.smsdetector.app

import android.app.Application
import com.smsdetector.app.data.db.SmsDatabase
import com.smsdetector.app.data.prefs.SettingsDataStore
import com.smsdetector.app.data.repository.SmsRepository
import com.smsdetector.app.work.ScheduleWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDetectorApplication : Application() {
    lateinit var db: SmsDatabase
        private set
    lateinit var settings: SettingsDataStore
        private set
    lateinit var repository: SmsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        db = SmsDatabase.get(this)
        settings = SettingsDataStore(this)
        repository = SmsRepository(this, db, settings)
        ScheduleWorker.enqueueAll(this)
        if (repository.isDefaultSmsApp()) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { repository.syncInbox() }
            }
        }
    }
}
