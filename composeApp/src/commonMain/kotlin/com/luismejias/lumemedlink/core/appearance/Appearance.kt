package com.luismejias.lumemedlink.core.appearance

import com.luismejias.lumemedlink.core.session.SecureStore
import com.luismejias.lumemedlink.core.session.SecureStoreKey
import com.luismejias.lumemedlink.core.session.SecureStoreUnreadableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Configuración's theme: the phone's, or one of the two the kit draws. */
internal enum class ThemeChoice(val stored: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }

/**
 * Configuración's language: the phone's, or one of the two the app is written in. [tag] is the BCP 47 language the
 * resources answer to; `null` follows the phone.
 */
internal enum class LanguageChoice(val stored: String, val tag: String?) {
    SYSTEM("system", null),
    SPANISH("es", "es"),
    ENGLISH("en", "en"),
}

/**
 * The person's theme and language (the author, 2026-10-13: «Dark, Light y system … inglés, español, system siendo
 * system el default»). Both start on the phone's and stay there until chosen.
 *
 * Kept in the secure store, as the telemetry consent is: not because they are secret, but so the logout's wipe takes
 * them too (§8.13). An unreadable store reads as the phone's — the safe answer, since it is the default.
 */
internal class Appearance(private val store: SecureStore) {
    private val currentTheme = MutableStateFlow(ThemeChoice.SYSTEM)
    private val currentLanguage = MutableStateFlow(LanguageChoice.SYSTEM)
    val theme: StateFlow<ThemeChoice> = currentTheme.asStateFlow()
    val language: StateFlow<LanguageChoice> = currentLanguage.asStateFlow()

    /** At launch, and after the logout's wipe: the choices follow what the store says now. */
    suspend fun restore() {
        currentTheme.value = read(SecureStoreKey.APPEARANCE_THEME, ThemeChoice.entries, ThemeChoice::stored)
            ?: ThemeChoice.SYSTEM
        currentLanguage.value = read(SecureStoreKey.APPEARANCE_LANGUAGE, LanguageChoice.entries, LanguageChoice::stored)
            ?: LanguageChoice.SYSTEM
    }

    suspend fun choose(theme: ThemeChoice) {
        currentTheme.value = theme
        write(SecureStoreKey.APPEARANCE_THEME, theme.stored.takeUnless { theme == ThemeChoice.SYSTEM })
    }

    suspend fun choose(language: LanguageChoice) {
        currentLanguage.value = language
        write(SecureStoreKey.APPEARANCE_LANGUAGE, language.stored.takeUnless { language == LanguageChoice.SYSTEM })
    }

    @Suppress("SwallowedException")
    private suspend fun <T> read(key: SecureStoreKey, choices: List<T>, stored: (T) -> String): T? = try {
        store.get(key.storageKey)?.let { value -> choices.firstOrNull { stored(it) == value } }
    } catch (unreadable: SecureStoreUnreadableException) {
        null
    }

    private suspend fun write(key: SecureStoreKey, value: String?) {
        if (value == null) store.remove(key.storageKey) else store.put(key.storageKey, value)
    }
}
