package com.luismejias.lumemedlink.features.auth.welcome

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeBrandLockupSize
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCarousel
import cl.lume.uicomposer.components.LumeCarouselItem
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeLink
import cl.lume.uicomposer.components.LumeLinkSize
import cl.lume.uicomposer.components.LumeLinkTone
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.features.auth.flow.AccessBrand
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.welcome_card_change_body
import com.luismejias.lumemedlink.resources.welcome_card_change_title
import com.luismejias.lumemedlink.resources.welcome_card_page
import com.luismejias.lumemedlink.resources.welcome_card_rate_body
import com.luismejias.lumemedlink.resources.welcome_card_rate_title
import com.luismejias.lumemedlink.resources.welcome_card_search_body
import com.luismejias.lumemedlink.resources.welcome_card_search_title
import com.luismejias.lumemedlink.resources.welcome_create_account
import com.luismejias.lumemedlink.resources.welcome_doctor_link
import com.luismejias.lumemedlink.resources.welcome_have_account
import org.jetbrains.compose.resources.stringResource

/**
 * 1a: the brand, what the app does in the kit's carousel (`LumeCarousel`, built for this screen and authorized by
 * the author on 2026-10-11), and the three ways in. The carousel keeps the vertical drag that starts on it, so
 * the screen does not scroll.
 */
/** The design's three cards. */
private const val WELCOME_CARDS = 3

@Composable
internal fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit, onDoctor: () -> Unit) {
    LumeContainer(
        alignment = LumeContainerAlignment.CenterAbovePinnedAction,
        action = {
            LumeStack(spacing = { small }) {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.welcome_create_account)),
                    width = LumeButtonWidth.Fill,
                    onClick = onCreateAccount,
                )
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.welcome_have_account)),
                    emphasis = LumeButtonEmphasis.Outline,
                    width = LumeButtonWidth.Fill,
                    onClick = onSignIn,
                )
                LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.Center) {
                    LumeLink(
                        title = stringResource(Res.string.welcome_doctor_link),
                        size = LumeLinkSize.Small,
                        tone = LumeLinkTone.Quiet,
                        icon = LumeIcon.Stethoscope,
                        onClick = onDoctor,
                    )
                }
            }
        },
    ) {
        val pageOf = pageDescriber(count = WELCOME_CARDS)
        LumeStack(spacing = { xLarge }) {
            AccessBrand(size = LumeBrandLockupSize.Large)
            LumeCarousel(
                items = listOf(
                    LumeCarouselItem(
                        LumeIcon.Mobile,
                        stringResource(Res.string.welcome_card_change_title),
                        stringResource(Res.string.welcome_card_change_body),
                    ),
                    LumeCarouselItem(
                        LumeIcon.Check,
                        stringResource(Res.string.welcome_card_rate_title),
                        stringResource(Res.string.welcome_card_rate_body),
                    ),
                    LumeCarouselItem(
                        LumeIcon.Search,
                        stringResource(Res.string.welcome_card_search_title),
                        stringResource(Res.string.welcome_card_search_body),
                    ),
                ),
                pageDescription = pageOf,
            )
        }
    }
}

/** "Página 2 de 3" for each card, resolved here: the carousel asks for it outside composition. */
@Composable
private fun pageDescriber(count: Int): (Int, Int) -> String {
    val pages = (1..count).map { stringResource(Res.string.welcome_card_page, it, count) }
    return { page, _ -> pages[page.coerceIn(0, count - 1)] }
}
