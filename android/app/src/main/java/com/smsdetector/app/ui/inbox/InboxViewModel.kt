package com.smsdetector.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smsdetector.app.SmsDetectorApplication
import com.smsdetector.app.data.db.Category
import com.smsdetector.app.data.db.Conversation
import com.smsdetector.app.data.repository.SmsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class InboxFilter { ALL, PERSONAL, OTP, PROMOTION }

data class InboxState(
    val conversations: List<Conversation> = emptyList(),
    val pinned: List<Conversation> = emptyList(),
    val filter: InboxFilter = InboxFilter.ALL,
    val query: String = "",
    val isDefaultSms: Boolean = false,
    val totalUnread: Int = 0
)

class InboxViewModel(
    private val repo: SmsRepository,
    private val isDefaultProvider: () -> Boolean
) : ViewModel() {

    private val filter = MutableStateFlow(InboxFilter.ALL)
    private val query = MutableStateFlow("")

    val state: StateFlow<InboxState> = combine(
        repo.observeConversations(), filter, query
    ) { all, f, q ->
        val filtered = all.filter { conv ->
            val byCategory = when (f) {
                InboxFilter.ALL -> true
                InboxFilter.PERSONAL -> conv.category == Category.PERSONAL
                InboxFilter.OTP -> conv.category in listOf(Category.OTP, Category.BANK)
                InboxFilter.PROMOTION -> conv.category == Category.PROMOTION
            }
            val byQuery = q.isBlank() || conv.displayName.contains(q, true) || conv.lastMessage.contains(q, true)
            byCategory && byQuery
        }
        InboxState(
            conversations = filtered.filter { !it.isPinned },
            pinned = filtered.filter { it.isPinned },
            filter = f,
            query = q,
            isDefaultSms = isDefaultProvider(),
            totalUnread = all.sumOf { it.unreadCount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InboxState())

    fun setFilter(f: InboxFilter) { filter.value = f }
    fun setQuery(q: String) { query.value = q }

    fun markRead(address: String) = viewModelScope.launch { repo.markRead(address) }
    fun togglePin(address: String, pinned: Boolean) = viewModelScope.launch { repo.setPinned(address, pinned) }

    companion object {
        fun factory(app: SmsDetectorApplication, isDefault: () -> Boolean) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    InboxViewModel(app.repository, isDefault) as T
            }
    }
}
