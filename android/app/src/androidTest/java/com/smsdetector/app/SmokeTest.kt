package com.smsdetector.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: verifies the app process starts and core singletons initialize.
 * Runs on an emulator/device or in the CI pipeline's Android test phase.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @Test
    fun applicationInitializesCoreComponents() {
        val app = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.applicationContext as SmsDetectorApplication
        assertNotNull(app.db)
        assertNotNull(app.settings)
        assertNotNull(app.repository)
        // Room is reachable
        val dao = app.db.conversationDao()
        assertNotNull(dao)
    }
}
