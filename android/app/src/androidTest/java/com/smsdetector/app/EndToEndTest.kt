package com.smsdetector.app

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end journey driven through the accessibility layer (UIAutomator),
 * exercising the real app UI from the launcher intent across all main tabs.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

    @get:Rule
    val permissions = GrantPermissionRule.grant(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private lateinit var device: UiDevice
    private lateinit var packageName: String

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        packageName = context.packageName
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        assertNotNull("Launcher intent must exist", intent)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.waitForIdle()
    }

    @Test
    fun navigateAcrossTabs() {
        // Chats tab
        waitForText("Start chat")
        assertVisible("Start chat")

        // Scheduled tab
        waitForText("Scheduled")
        clickText("Scheduled")
        waitForText("Scheduled & Smart Tools")
        assertVisible("Scheduled & Smart Tools")

        // Security tab
        waitForText("Security")
        clickText("Security")
        waitForText("Device Protected")
        assertVisible("Device Protected")

        // Back to Chats
        waitForText("Chats")
        clickText("Chats")
        waitForText("Start chat")
    }

    private fun waitForText(text: String, timeout: Long = 20_000) {
        device.wait(Until.hasObject(By.textContains(text)), timeout)
    }

    private fun assertVisible(text: String) {
        assertNotNull("Text not visible: $text", device.findObject(By.textContains(text)))
    }

    private fun clickText(text: String) {
        val obj = device.findObject(By.textContains(text))
        assertNotNull("Cannot find clickable text: $text", obj)
        obj.click()
        device.waitForIdle()
    }
}
