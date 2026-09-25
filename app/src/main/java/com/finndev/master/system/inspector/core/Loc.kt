package com.finndev.master.system.inspector.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Runtime localization engine for MSI (module 73).
 *
 * String tables for all five languages (TR / ES / EN / FR / IT) live in
 * [LocDataEn], [LocDataTr], [LocDataEs], [LocDataFr], [LocDataIt].
 * Using an in-memory table (instead of resources) allows the ENTIRE interface
 * to switch language instantly with no activity recreation.
 */
object Loc {
    /** Language metadata shown on the onboarding selector. */
    data class LangMeta(val code: String, val nativeName: String, val englishName: String)

    val languages: List<LangMeta> = listOf(
        LangMeta("tr", "Türkçe", "Turkish"),
        LangMeta("es", "Español", "Spanish"),
        LangMeta("en", "English", "English"),
        LangMeta("fr", "Français", "French"),
        LangMeta("it", "Italiano", "Italian"),
    )

    @Volatile
    var current: String = "en"

    val table: Map<String, Map<String, String>> by lazy {
        mapOf(
            "en" to LocDataEn.data,
            "tr" to LocDataTr.data,
            "es" to LocDataEs.data,
            "fr" to LocDataFr.data,
            "it" to LocDataIt.data,
        )
    }

    fun isSupported(code: String): Boolean = table.containsKey(code)

    /** Synchronous lookup with graceful English fallback, then key fallback. */
    fun s(key: String): String =
        table[current]?.get(key) ?: table["en"]?.get(key) ?: key

    fun allKeys(): Set<String> = table["en"]?.keys ?: emptySet()
}

/** Compose lookup: recomposes automatically when the language changes. */
@Composable
fun L(key: String): String {
    val lang by AppState.language.collectAsState()
    return remember(lang, key) { Loc.table[lang]?.get(key) ?: Loc.table["en"]?.get(key) ?: key }
}

/** Formatted variant: {0},{1}... placeholders via String.format. */
@Composable
fun L(key: String, vararg args: Any?): String {
    val lang by AppState.language.collectAsState()
    return remember(lang, key) {
        val tmpl = Loc.table[lang]?.get(key) ?: Loc.table["en"]?.get(key) ?: key
        runCatching { java.text.MessageFormat(tmpl).format(args) }.getOrDefault(tmpl)
    }
}

/** Non-composable formatted lookup for services / callbacks. */
fun Ls(key: String): String = Loc.s(key)
