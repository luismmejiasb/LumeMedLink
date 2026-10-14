package com.luismejias.lumemedlink.core.appearance

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * The phone's language is read from the system's resources every time, never remembered from the process: once this
 * has set the default locale, `Locale.getDefault()` answers with the app's choice, not the phone's.
 */
@Composable
internal actual fun ProvideAppLanguage(tag: String?, content: @Composable () -> Unit) {
    val locale = tag?.let(Locale::forLanguageTag) ?: Resources.getSystem().configuration.locales[0]
    Locale.setDefault(locale)
    val base = LocalConfiguration.current
    val context = LocalContext.current
    val configuration = remember(base, locale) { Configuration(base).apply { setLocale(locale) } }
    val localized = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(LocalConfiguration provides configuration, LocalContext provides localized) {
        key(locale.toLanguageTag()) { content() }
    }
}
