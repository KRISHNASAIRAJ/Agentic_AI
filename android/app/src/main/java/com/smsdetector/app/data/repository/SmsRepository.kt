package com.smsdetector.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.smsdetector.app.data.db.*
import com.smsdetector.app.data.detection.DetectionEngine
import com.smsdetector.app.data.prefs.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Bridges the Android SMS provider with our local Room store. */
class SmsRepository(
    private val context: Context,
    private val db: SmsDatabase,
    private val settings: SettingsDataStore
) {
    private val conversations = db.conversationDao()
    private val messages = db.messageDao()
    private val blocked = db.blockedDao()

    fun isDefaultSmsApp(): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun observeConversations() = conversations.observeConversations()
    fun observeBlockedConversations() = conversations.observeBlocked()
    fun observeThread(address: String) = messages.observeThread(address)
    fun observeSpam() = messages.observeSpam()
    fun observeOtps() = messages.observeOtps()
    fun observeScheduled() = db.scheduledDao().observePending()
    fun observeBlockedSenders() = blocked.observeBlocked()
    fun observeSpamCount() = messages.countSpam()

    /** First-run import from the system SMS provider (requires READ_SMS). */
    suspend fun syncInbox(): Int = withContext(Dispatchers.IO) {
        if (!isDefaultSmsApp()) return@withContext 0
        val names = loadContactNames()
        var imported = 0
        val uri: Uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
            Telephony.Sms.DATE, Telephony.Sms.TYPE
        )
        try {
            context.contentResolver.query(uri, projection, null, null, "date DESC LIMIT 200")?.use { c ->
                val idxAddr = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val idxBody = c.getColumnIndex(Telephony.Sms.BODY)
                val idxDate = c.getColumnIndex(Telephony.Sms.DATE)
                val idxType = c.getColumnIndex(Telephony.Sms.TYPE)
                while (c.moveToNext()) {
                    val addr = c.getString(idxAddr) ?: continue
                    val body = c.getString(idxBody) ?: continue
                    val ts = c.getLong(idxDate)
                    val type = c.getInt(idxType)
                    val providerId = c.getLong(c.getColumnIndex(Telephony.Sms._ID))
                    val direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX)
                        Direction.INCOMING else Direction.OUTGOING
                    ensureConversation(addr, body, ts, names[addr], direction == Direction.INCOMING)
                    if (messages.countByProviderId(providerId) == 0) {
                        persistMessage(addr, body, ts, direction, providerId)
                        imported++
                    }
                }
            }
        } catch (_: SecurityException) { /* permission not granted */ }
        imported
    }

    /** Process an SMS_RECEIVED broadcast. */
    suspend fun handleIncoming(sender: String, body: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val result = DetectionEngine.classify(body, sender)
        val name = loadContactNames()[sender]
        val blockedEntry = blocked.get(sender)
        val isSpam = result.isSpam || blockedEntry != null

        ensureConversation(sender, body, timestamp, name, incoming = true, spam = isSpam)
        persistMessage(
            address = sender, body = body, ts = timestamp,
            direction = Direction.INCOMING,
            type = if (result.otpCode != null) MessageType.OTP else MessageType.TEXT,
            category = result.category,
            spamScore = result.spamScore,
            isSpam = isSpam,
            otpCode = result.otpCode,
            otpExpiresAt = result.otpExpiresAtMillis
        )

        if (isSpam && blockedEntry == null) {
            blocked.upsert(BlockedSender(sender, result.label ?: "Spam", result.category, timestamp))
        }

        // OTP auto-delete: keep the code in our own secure store, remove from system inbox.
        if (settings.autoDeleteOtp.first() && result.otpCode != null) {
            try {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "address = ? AND body = ?",
                    arrayOf(sender, body)
                )
            } catch (_: Exception) { /* best effort */ }
        }

        // Expire stale OTPs in local store.
        messages.deleteOldOtps(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
    }

    suspend fun sendSms(address: String, body: String) = withContext(Dispatchers.IO) {
        val manager = SmsManager.getDefault()
        val parts = manager.divideMessage(body)
        if (parts.size > 1) manager.sendMultipartTextMessage(address, null, parts, null, null)
        else manager.sendTextMessage(address, null, body, null, null)

        val now = System.currentTimeMillis()
        ensureConversation(address, body, now, loadContactNames()[address], incoming = false)
        persistMessage(address, body, now, Direction.OUTGOING, category = DetectionEngine.classify(body).category)
    }

    suspend fun schedule(address: String, body: String, at: Long, recurring: String? = null) {
        db.scheduledDao().upsert(
            ScheduledMessage(
                address = address,
                displayName = loadContactNames()[address] ?: DetectionEngine.displayName(address),
                body = body, scheduledAt = at,
                isRecurring = recurring != null, recurringLabel = recurring
            )
        )
    }

    suspend fun markRead(address: String) = conversations.markRead(address)
    suspend fun setPinned(address: String, pinned: Boolean) = conversations.setPinned(address, pinned)
    suspend fun unblock(address: String) {
        conversations.setBlocked(address, false)
        blocked.delete(address)
    }
    suspend fun deleteMessage(id: Long) = messages.delete(id)
    suspend fun deleteScheduled(id: Long) = db.scheduledDao().delete(id)

    private suspend fun ensureConversation(
        address: String, body: String, ts: Long, contactName: String?,
        incoming: Boolean, spam: Boolean = false
    ) {
        val existing = conversations.get(address)
        val display = contactName ?: DetectionEngine.displayName(address)
        val cat = DetectionEngine.classify(body, address).category
        conversations.upsert(
            Conversation(
                address = address,
                displayName = display,
                initials = DetectionEngine.initials(display),
                avatarColor = avatarColor(address),
                lastMessage = body,
                lastMessageTs = ts,
                unreadCount = if (incoming) (existing?.unreadCount ?: 0) + 1 else existing?.unreadCount ?: 0,
                isPinned = existing?.isPinned ?: false,
                isRcs = existing?.isRcs ?: false,
                isVerified = existing?.isVerified ?: (cat in listOf(Category.BANK, Category.OTP)),
                isBlocked = spam || (existing?.isBlocked ?: false),
                category = cat
            )
        )
    }

    private suspend fun persistMessage(
        address: String, body: String, ts: Long, direction: Direction,
        providerId: Long = 0L, type: MessageType = MessageType.TEXT,
        category: Category = Category.UNKNOWN, spamScore: Float = 0f,
        isSpam: Boolean = false, otpCode: String? = null, otpExpiresAt: Long? = null
    ) {
        messages.insert(
            Message(
                threadId = address, body = body, timestamp = ts, direction = direction,
                type = type, providerId = providerId, category = category, spamScore = spamScore, isSpam = isSpam,
                otpCode = otpCode, otpExpiresAt = otpExpiresAt
            )
        )
    }

    private fun avatarColor(key: String): Int {
        val palette = listOf(
            0xFF3A8DFF.toInt(), 0xFF8B5CF6.toInt(), 0xFF14B8A6.toInt(),
            0xFFF59E0B.toInt(), 0xFFEC4899.toInt(), 0xFF6366F1.toInt()
        )
        return palette[(key.hashCode().ushr(1) and Int.MAX_VALUE) % palette.size]
    }

    private fun loadContactNames(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ), null, null, null
            )?.use { c ->
                val ni = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val di = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val number = c.getString(ni)?.replace(Regex("""[^\d+]"""), "") ?: continue
                    map[number] = c.getString(di) ?: continue
                }
            }
        } catch (_: SecurityException) { /* permission not granted */ }
        return map
    }
}
