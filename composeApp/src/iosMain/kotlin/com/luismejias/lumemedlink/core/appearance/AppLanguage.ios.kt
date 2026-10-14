package com.luismejias.lumemedlink.core.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import platform.Foundation.NSUserDefaults

private const val APPLE_LANGUAGES = "AppleLanguages"

/**
 * `AppleLanguages` in the app's own defaults is iOS's per-app language: the resources read it through
 * `NSLocale.preferredLanguages`, and removing it hands the choice back to the phone.
 */
@Composable
internal actual fun ProvideAppLanguage(tag: String?, content: @Composable () -> Unit) {
    val defaults = NSUserDefaults.standardUserDefaults
    if (tag == null) defaults.removeObjectForKey(APPLE_LANGUAGES) else defaults.setObject(listOf(tag), APPLE_LANGUAGES)
    key(tag) { content() }
}
