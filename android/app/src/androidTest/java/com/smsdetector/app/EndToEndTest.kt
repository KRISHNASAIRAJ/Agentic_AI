package com.smsdetector.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smsdetector.app.data.db.Category
import com.smsdetector.app.data.db.Conversation
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end journey: seed a conversation, open the inbox, enter a thread,
 * then move through Scheduled and Security tabs.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun seed() = runBlocking {
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

    @Test
    fun navigateInboxToThreadAndTabs() {
        // Inbox renders (wait defensively: hierarchy may not be attached yet)
        waitForText("Elena Rostova")
        compose.onNodeWithText("Elena Rostova").assertIsDisplayed()
        compose.onNodeWithText("Start chat").assertIsDisplayed()

        // Open thread
        compose.onNodeWithText("Elena Rostova").performClick()
        waitForText("End-to-end encrypted")
        compose.onNodeWithContentDescription("Back").performClick()

        // Scheduled tab
        waitForText("Scheduled")
        compose.onNodeWithText("Scheduled").performClick()
        waitForText("Scheduled & Smart Tools")
        compose.onNodeWithText("Scheduled & Smart Tools").assertIsDisplayed()

        // Security tab
        waitForText("Security")
        compose.onNodeWithText("Security").performClick()
        waitForText("Device Protected")
        compose.onNodeWithText("Device Protected").assertIsDisplayed()
    }

    /** Retries until the text appears; tolerates the brief window before Compose attaches. */
    private fun waitForText(text: String, timeoutMillis: Long = 20_000) {
        compose.waitUntil(timeoutMillis = timeoutMillis) {
            try {
                compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            } catch (e: IllegalStateException) {
                false // "No compose hierarchies found in the app" during startup
            }
        }
    }
}
