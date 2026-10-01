package com.akash.qrdrawer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.drawerDataStore by preferencesDataStore(name = "drawer_settings")

enum class BubbleSize(val dp: Int, val label: String) {
    ExtraSmall(32, "XS"),
    Small(40, "S"),
    Medium(48, "M"),
    Large(56, "L"),
}

const val MIN_BUBBLE_OPACITY = 0.3f
const val DEFAULT_BUBBLE_OPACITY = 0.6f

data class DrawerSettings(
    val bubbleEnabled: Boolean = false,
    /** Temporarily hidden via the × target; the service keeps running with its notification. */
    val bubbleHidden: Boolean = false,
    val bubbleX: Int = -1,
    val bubbleY: Int = -1,
    val bubbleOpacity: Float = DEFAULT_BUBBLE_OPACITY,
    val bubbleSize: BubbleSize = BubbleSize.Medium,
    val startOnBoot: Boolean = false,
    /** Require biometric / device credential to open the manager. */
    val appLock: Boolean = false,
    /** Also require it before showing a QR from the drawer (off by default for speed). */
    val lockViewer: Boolean = false,
    /** Block screenshots and the recents thumbnail of the manager. */
    val secureManager: Boolean = false,
    val onboardingDone: Boolean = false,
)

class DrawerPreferences(context: Context) {
    private val dataStore = context.applicationContext.drawerDataStore

    val settings: Flow<DrawerSettings> = dataStore.data.map { preferences ->
        DrawerSettings(
            bubbleEnabled = preferences[BubbleEnabled] ?: false,
            bubbleHidden = preferences[BubbleHidden] ?: false,
            bubbleX = preferences[BubbleX] ?: -1,
            bubbleY = preferences[BubbleY] ?: -1,
            bubbleOpacity = (preferences[BubbleOpacity] ?: DEFAULT_BUBBLE_OPACITY).coerceIn(MIN_BUBBLE_OPACITY, 1f),
            bubbleSize = BubbleSize.entries.firstOrNull { it.name == preferences[BubbleSizeKey] } ?: BubbleSize.Medium,
            startOnBoot = preferences[StartOnBoot] ?: false,
            appLock = preferences[AppLock] ?: false,
            lockViewer = preferences[LockViewer] ?: false,
            secureManager = preferences[SecureManager] ?: false,
            onboardingDone = preferences[OnboardingDone] ?: false,
        )
    }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        dataStore.edit {
            it[BubbleEnabled] = enabled
            it[BubbleHidden] = false
        }
    }

    suspend fun setBubbleHidden(hidden: Boolean) {
        dataStore.edit { it[BubbleHidden] = hidden }
    }

    suspend fun setPosition(x: Int, y: Int) {
        dataStore.edit {
            it[BubbleX] = x
            it[BubbleY] = y
        }
    }

    suspend fun resetPosition() {
        dataStore.edit {
            it.remove(BubbleX)
            it.remove(BubbleY)
        }
    }

    suspend fun setOpacity(opacity: Float) {
        dataStore.edit { it[BubbleOpacity] = opacity.coerceIn(MIN_BUBBLE_OPACITY, 1f) }
    }

    suspend fun setSize(size: BubbleSize) {
        dataStore.edit { it[BubbleSizeKey] = size.name }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        dataStore.edit { it[StartOnBoot] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean) {
        dataStore.edit {
            it[AppLock] = enabled
            if (!enabled) it[LockViewer] = false
        }
    }

    suspend fun setLockViewer(enabled: Boolean) {
        dataStore.edit { it[LockViewer] = enabled }
    }

    suspend fun setSecureManager(enabled: Boolean) {
        dataStore.edit { it[SecureManager] = enabled }
    }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[OnboardingDone] = true }
    }

    private companion object {
        val BubbleEnabled = booleanPreferencesKey("bubble_enabled")
        val BubbleHidden = booleanPreferencesKey("bubble_hidden")
        val BubbleX = intPreferencesKey("bubble_x")
        val BubbleY = intPreferencesKey("bubble_y")
        val BubbleOpacity = floatPreferencesKey("bubble_opacity")
        val BubbleSizeKey = stringPreferencesKey("bubble_size")
        val StartOnBoot = booleanPreferencesKey("start_on_boot")
        val AppLock = booleanPreferencesKey("app_lock")
        val LockViewer = booleanPreferencesKey("lock_viewer")
        val SecureManager = booleanPreferencesKey("secure_manager")
        val OnboardingDone = booleanPreferencesKey("onboarding_done")
    }
}
