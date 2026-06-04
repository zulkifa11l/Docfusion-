package com.example.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("docfusion_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_LOCK_PIN = "app_lock_pin"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        private const val KEY_BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"
    }

    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()

    var appLockPin: String?
        get() = prefs.getString(KEY_APP_LOCK_PIN, null)
        set(value) = prefs.edit().putString(KEY_APP_LOCK_PIN, value).apply()

    var isDarkModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, value).apply()

    fun verifyPin(enteredPin: String): Boolean {
        val pin = appLockPin ?: return false
        return pin == enteredPin
    }

    fun clearPin() {
        prefs.edit().remove(KEY_APP_LOCK_PIN).putBoolean(KEY_APP_LOCK_ENABLED, false).apply()
    }
}
