package com.smsdetector.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Message categories power the inbox filter chips and spam folder. */
enum class Category { PERSONAL, OTP, BANK, PROMOTION, SPAM, UNKNOWN }

enum class Direction { INCOMING, OUTGOING }
enum class MessageType { TEXT, OTP, IMAGE, VOICE }
enum class DeliveryStatus { SENDING, SENT, DELIVERED, READ, FAILED }

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val address: String,
    val displayName: String,
    val initials: String,
    val avatarColor: Int = 0xFF3A8DFF.toInt(),
    val lastMessage: String = "",
    val lastMessageTs: Long = 0L,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isRcs: Boolean = false,
    val isVerified: Boolean = false,
    val isMuted: Boolean = false,
    val isBlocked: Boolean = false,
    val category: Category = Category.UNKNOWN
)

@Entity(tableName = "messages", indices = [Index("threadId")])
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: String,
    val body: String,
    val timestamp: Long,
    val direction: Direction,
    val type: MessageType = MessageType.TEXT,
    val providerId: Long = 0L,
    val status: DeliveryStatus = DeliveryStatus.SENT,
    val category: Category = Category.UNKNOWN,
    val spamScore: Float = 0f,
    val isSpam: Boolean = false,
    val otpCode: String? = null,
    val otpExpiresAt: Long? = null
)

@Entity(tableName = "scheduled_messages")
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val displayName: String,
    val body: String,
    val scheduledAt: Long,
    val isRecurring: Boolean = false,
    val recurringLabel: String? = null,
    val status: String = "PENDING" // PENDING, SENT, CANCELLED
)

@Entity(tableName = "blocked_senders")
data class BlockedSender(
    @PrimaryKey val address: String,
    val reason: String,
    val category: Category,
    val interceptedAt: Long
)

data class ConversationWithLastMessage(
    val address: String,
    val displayName: String,
    val initials: String,
    val avatarColor: Int,
    val lastMessage: String,
    val lastMessageTs: Long,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isRcs: Boolean,
    val isVerified: Boolean,
    val category: Category
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isBlocked = 0 ORDER BY isPinned DESC, lastMessageTs DESC")
    fun observeConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE isBlocked = 1 ORDER BY lastMessageTs DESC")
    fun observeBlocked(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE address = :address")
    suspend fun get(address: String): Conversation?

    @Upsert
    suspend fun upsert(conversation: Conversation)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE address = :address")
    suspend fun markRead(address: String)

    @Query("UPDATE conversations SET isPinned = :pinned WHERE address = :address")
    suspend fun setPinned(address: String, pinned: Boolean)

    @Query("UPDATE conversations SET isBlocked = :blocked WHERE address = :address")
    suspend fun setBlocked(address: String, blocked: Boolean)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun observeThread(threadId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE isSpam = 1 ORDER BY timestamp DESC")
    fun observeSpam(): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE type = 'OTP' AND isSpam = 0 ORDER BY timestamp DESC LIMIT 20")
    fun observeOtps(): Flow<List<Message>>

    @Query("SELECT COUNT(*) FROM messages WHERE providerId = :id AND providerId != 0")
    suspend fun countByProviderId(id: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isSpam = 1")
    fun countSpam(): Flow<Int>

    @Insert
    suspend fun insert(message: Message): Long

    @Update
    suspend fun update(message: Message)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages WHERE timestamp < :before AND type = 'OTP'")
    suspend fun deleteOldOtps(before: Long)
}

@Dao
interface ScheduledDao {
    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' ORDER BY scheduledAt ASC")
    fun observePending(): Flow<List<ScheduledMessage>>

    @Upsert
    suspend fun upsert(item: ScheduledMessage)

    @Query("UPDATE scheduled_messages SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BlockedDao {
    @Query("SELECT * FROM blocked_senders ORDER BY interceptedAt DESC")
    fun observeBlocked(): Flow<List<BlockedSender>>

    @Query("SELECT * FROM blocked_senders WHERE address = :address")
    suspend fun get(address: String): BlockedSender?

    @Upsert
    suspend fun upsert(item: BlockedSender)

    @Query("DELETE FROM blocked_senders WHERE address = :address")
    suspend fun delete(address: String)
}

@Database(
    entities = [Conversation::class, Message::class, ScheduledMessage::class, BlockedSender::class],
    version = 1,
    exportSchema = false
)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun scheduledDao(): ScheduledDao
    abstract fun blockedDao(): BlockedDao

    companion object {
        @Volatile
        private var INSTANCE: SmsDatabase? = null

        fun get(context: android.content.Context): SmsDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SmsDatabase::class.java,
                    "sms_detector.db"
                ).build().also { INSTANCE = it }
            }
    }
}
