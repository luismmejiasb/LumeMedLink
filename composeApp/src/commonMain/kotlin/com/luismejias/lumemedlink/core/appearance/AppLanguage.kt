package com.luismejias.lumemedlink.core.appearance

import androidx.compose.runtime.Composable

/**
 * Puts [content] in [tag]'s language — the app's strings and the kit's — or in the phone's when it is `null`.
 *
 * Compose resources answer to the platform's locale, so each platform is told: Android's default locale and the
 * configuration the composition reads; iOS's `AppleLanguages` for this app only, which is also how iOS remembers a
 * per-app language. The content is rebuilt under the new language, so the caller keeps its state above this.
 */
@Composable
internal expect fun ProvideAppLanguage(tag: String?, content: @Composable () -> Unit)
