package com.smsdetector.app

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.smsdetector.app.data.db.Category
import com.smsdetector.app.data.db.Conversation
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * End-to-end journey: with the app launched, navigate from Chats to the
 * Scheduled and Security tabs. Uses only static UI elements so the test
 * never depends on asynchronous database seeding or SMS permissions.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS
        ))
        .around(compose)

    @Test
    fun navigateAcrossTabs() {
        // Chats tab (static element — always present)
        waitForText("Start chat")
        waitForText("Chats")

        // Scheduled tab
        waitForText("Scheduled")
        compose.onNodeWithText("Scheduled").performClick()
        waitForText("Scheduled & Smart Tools")

        // Security tab
        waitForText("Security")
        compose.onNodeWithText("Security").performClick()
        waitForText("Device Protected")

        // Back to Chats
        waitForText("Chats")
        compose.onNodeWithText("Chats").performClick()
        waitForText("Start chat")
    }

    /** Retries until the text appears; tolerates the brief window before Compose attaches. */
    private fun waitForText(text: String, timeoutMillis: Long = 30_000) {
        compose.waitUntil(timeoutMillis = timeoutMillis) {
            try {
                compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            } catch (e: IllegalStateException) {
                false // "No compose hierarchies found in the app" during startup
            }
        }
    }

    companion object {
        /** Seed before the Compose rule launches the Activity so the inbox has data. */
        @BeforeClass
        @JvmStatic
        fun seedConversation() = runBlocking {
            val app = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().targetContext.applicationContext as SmsDetectorApplication
            app.db.conversationDao().upsert(
                Conversation(
                    address = "+15550001111",
                    displayName = "Elena Rostova",
                    initials = "ER",
                    lastMessage = "Let's review the new release mockup...",
                    lastMessageTs = System.currentTimeMillis(),
                    unreadCount = 2,
                    isPinned = true,
                    isRcs = true,
                    category = Category.PERSONAL
                )
            )
        }
    }
}
