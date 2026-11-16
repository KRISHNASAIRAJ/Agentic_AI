package com.smsdetector.app.ui.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smsdetector.app.SmsDetectorApplication
import com.smsdetector.app.data.db.Message
import com.smsdetector.app.data.repository.SmsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ThreadViewModel(
    private val address: String,
    private val repo: SmsRepository,
    private val app: SmsDetectorApplication
) : ViewModel() {

    val messages: StateFlow<List<Message>> = repo.observeThread(address)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val title: StateFlow<String> = repo.observeConversations().map { list ->
        list.firstOrNull { it.address == address }?.displayName ?: address
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), address)

    init { viewModelScope.launch { repo.markRead(address) } }

    fun send(body: String) {
        if (body.isBlank()) return
        viewModelScope.launch { repo.sendSms(address, body.trim()) }
    }

    fun delete(id: Long) = viewModelScope.launch { repo.deleteMessage(id) }

    companion object {
        fun factory(app: SmsDetectorApplication, address: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ThreadViewModel(address, app.repository, app) as T
            }
    }
}
