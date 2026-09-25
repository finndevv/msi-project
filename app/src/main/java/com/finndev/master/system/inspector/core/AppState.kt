package com.finndev.master.system.inspector.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** The three MSI UI themes (module 74 / theme selector). */
enum class MsiTheme(val key: String) {
    MATERIAL3("material3"),
    HACKER("hacker"),
    CLASSIC("classic")
}

/**
 * Global application state: persisted user preferences (language, theme, onboarding),
 * plus live root status. All values are Compose-observable via MutableStateFlow.
 */
object AppState {
    private lateinit var prefs: SharedPreferences

    /** ISO-ish language codes: tr / es / en / fr / it */
    val language = MutableStateFlow("en")
    val theme = MutableStateFlow(MsiTheme.MATERIAL3)
    val onboardingDone = MutableStateFlow(false)

    /** Set after the first root probe so dashboards can render immediately. */
    val rootState = MutableStateFlow<RootInfo?>(null)

    /** Custom PATH additions managed by module 24, applied to MSI shell sessions. */
    val customPath = MutableStateFlow("")

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("msi_state", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("lang", null)
        language.value = if (savedLang != null && Loc.isSupported(savedLang)) savedLang else "en"
        Loc.current = language.value
        val savedTheme = prefs.getString("theme", null)
        theme.value = MsiTheme.entries.firstOrNull { it.key == savedTheme } ?: MsiTheme.MATERIAL3
        onboardingDone.value = prefs.getBoolean("onboarded", false)
        customPath.value = prefs.getString("custom_path", "") ?: ""
    }

    fun setLanguage(code: String) {
        if (!Loc.isSupported(code)) return
        language.value = code
        Loc.current = code
        prefs.edit().putString("lang", code).apply()
    }

    fun setTheme(t: MsiTheme) {
        theme.value = t
        prefs.edit().putString("theme", t.key).apply()
    }

    fun setOnboardingDone() {
        onboardingDone.value = true
        prefs.edit().putBoolean("onboarded", true).apply()
    }

    fun resetOnboarding() {
        onboardingDone.value = false
        prefs.edit().putBoolean("onboarded", false).apply()
    }

    fun setCustomPath(v: String) {
        customPath.value = v
        prefs.edit().putString("custom_path", v).apply()
    }

    // ---------- generic persisted key/value helpers used by modules ----------
    fun prefString(key: String, def: String = ""): String = if (this::prefs.isInitialized) prefs.getString(key, def) ?: def else def
    fun prefLong(key: String, def: Long = 0L): Long = if (this::prefs.isInitialized) prefs.getLong(key, def) else def
    fun prefBool(key: String, def: Boolean = false): Boolean = if (this::prefs.isInitialized) prefs.getBoolean(key, def) else def
    fun putPref(key: String, value: String) { if (this::prefs.isInitialized) prefs.edit().putString(key, value).apply() }
    fun putPref(key: String, value: Long) { if (this::prefs.isInitialized) prefs.edit().putLong(key, value).apply() }
    fun putPref(key: String, value: Boolean) { if (this::prefs.isInitialized) prefs.edit().putBoolean(key, value).apply() }
}
