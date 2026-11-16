package com.smsdetector.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smsdetector.app.SmsDetectorApplication
import com.smsdetector.app.data.db.BlockedSender
import com.smsdetector.app.data.prefs.SettingsDataStore
import com.smsdetector.app.data.repository.SmsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SecurityState(
    val autoDeleteOtp: Boolean = true,
    val aiSpam: Boolean = true,
    val verifiedBadges: Boolean = true,
    val drivingMode: Boolean = false,
    val spamCount: Int = 0,
    val blocked: List<BlockedSender> = emptyList(),
    val ruleVersion: Int = 1
)

class SecurityViewModel(
    private val repo: SmsRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val toggles = combine(
        settings.autoDeleteOtp, settings.aiSpam, settings.verifiedBadges, settings.drivingMode
    ) { otp, ai, badges, driving -> arrayOf(otp, ai, badges, driving) }

    private val intel = combine(
        settings.ruleVersion, repo.observeSpamCount(), repo.observeBlockedSenders()
    ) { version, spamCount, blocked -> arrayOf(version, spamCount, blocked) }

    val state: StateFlow<SecurityState> = combine(toggles, intel) { t, i ->
        @Suppress("UNCHECKED_CAST")
        SecurityState(
            autoDeleteOtp = t[0] as Boolean,
            aiSpam = t[1] as Boolean,
            verifiedBadges = t[2] as Boolean,
            drivingMode = t[3] as Boolean,
            ruleVersion = i[0] as Int,
            spamCount = i[1] as Int,
            blocked = i[2] as List<BlockedSender>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecurityState())

    fun toggleOtp(v: Boolean) = viewModelScope.launch { settings.set(SettingsDataStore.KEY_AUTO_DELETE_OTP, v) }
    fun toggleAi(v: Boolean) = viewModelScope.launch { settings.set(SettingsDataStore.KEY_AI_SPAM, v) }
    fun toggleBadges(v: Boolean) = viewModelScope.launch { settings.set(SettingsDataStore.KEY_VERIFIED_BADGES, v) }
    fun toggleDriving(v: Boolean) = viewModelScope.launch { settings.set(SettingsDataStore.KEY_DRIVING_MODE, v) }
    fun unblock(address: String) = viewModelScope.launch { repo.unblock(address) }

    companion object {
        fun factory(app: SmsDetectorApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SecurityViewModel(app.repository, app.settings) as T
        }
    }
}
