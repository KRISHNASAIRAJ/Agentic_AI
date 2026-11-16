package com.smsdetector.app.ui.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smsdetector.app.SmsDetectorApplication
import com.smsdetector.app.data.db.ScheduledMessage
import com.smsdetector.app.data.prefs.SettingsDataStore
import com.smsdetector.app.data.repository.SmsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScheduledState(
    val pending: List<ScheduledMessage> = emptyList(),
    val drivingMode: Boolean = false
)

class ScheduledViewModel(
    private val repo: SmsRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    val state: StateFlow<ScheduledState> = combine(
        repo.observeScheduled(), settings.drivingMode
    ) { pending, driving -> ScheduledState(pending, driving) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduledState())

    fun schedule(address: String, body: String, at: Long, recurring: String? = null) =
        viewModelScope.launch { repo.schedule(address, body, at, recurring) }

    fun cancel(id: Long) = viewModelScope.launch { repo.deleteScheduled(id) }

    fun toggleDriving(v: Boolean) = viewModelScope.launch { settings.set(SettingsDataStore.KEY_DRIVING_MODE, v) }

    companion object {
        fun factory(app: SmsDetectorApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScheduledViewModel(app.repository, app.settings) as T
        }
    }
}
