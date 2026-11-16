package com.smsdetector.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val KEY_AUTO_DELETE_OTP = booleanPreferencesKey("auto_delete_otp")
        val KEY_AI_SPAM = booleanPreferencesKey("ai_spam")
        val KEY_VERIFIED_BADGES = booleanPreferencesKey("verified_badges")
        val KEY_DRIVING_MODE = booleanPreferencesKey("driving_mode")
        val KEY_SIGNATURE_VERSION = stringPreferencesKey("signature_version")
        val KEY_RULE_VERSION = intPreferencesKey("rule_version")
    }

    val autoDeleteOtp: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_DELETE_OTP] ?: true }
    val aiSpam: Flow<Boolean> = context.dataStore.data.map { it[KEY_AI_SPAM] ?: true }
    val verifiedBadges: Flow<Boolean> = context.dataStore.data.map { it[KEY_VERIFIED_BADGES] ?: true }
    val drivingMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_DRIVING_MODE] ?: false }
    val ruleVersion: Flow<Int> = context.dataStore.data.map { it[KEY_RULE_VERSION] ?: 1 }

    suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
}
