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
        // Inbox renders
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Elena Rostova").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Elena Rostova").assertIsDisplayed()
        compose.onNodeWithText("Start chat").assertIsDisplayed()

        // Open thread
        compose.onNodeWithText("Elena Rostova").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("End-to-end encrypted").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back").performClick()

        // Scheduled tab
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Scheduled").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Scheduled").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Scheduled & Smart Tools").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Scheduled & Smart Tools").assertIsDisplayed()

        // Security tab
        compose.onNodeWithText("Security").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Device Protected").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Device Protected").assertIsDisplayed()
    }
}
