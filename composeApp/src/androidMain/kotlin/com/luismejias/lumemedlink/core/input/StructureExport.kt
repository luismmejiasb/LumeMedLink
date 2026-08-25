package com.luismejias.lumemedlink.core.input

import android.content.Context
import android.os.Build
import android.view.View
import android.view.contentcapture.ContentCaptureManager

/*
 * The operating system's *structure-export* channels, and what this app does about each (F3
 * reopened, ADR-0024).
 *
 * These are not screenshots and FLAG_SECURE does not govern them. They hand another process a
 * structured copy of what is on screen — node by node, with the text — and Compose opts into them
 * on the app's behalf, without the app asking for it.
 *
 * ## Channel 1 — autofill. It was open; [denyAutofillExport] closes it.
 *
 * Every Compose text field publishes `ContentDataType.Text` semantics unconditionally
 * (`TextFieldDecoratorModifier` and `CoreTextFieldSemanticsModifier` set it with no condition and
 * no opt-out), and `AndroidAutofillManager.populateViewStructure` exports exactly the nodes that
 * carry it. So every field in this app was a node in the structure handed to whichever autofill
 * service the user has installed — a third-party app. FLAG_SECURE does not touch this path and
 * cannot: banking apps are FLAG_SECURE and still autofill.
 *
 * ## Channel 2 — content capture. Already shut; [denyContentCapture] keeps it shut independently.
 *
 * Compose force-sets `IMPORTANT_FOR_CONTENT_CAPTURE_YES` when it acquires a session. The channel
 * is nevertheless already closed here by FLAG_SECURE: `ContentCaptureManager.updateWindowAttributes`
 * raises `FLAG_DISABLED_BY_FLAG_SECURE` for a secure window. So [denyContentCapture] does not
 * close a hole — it raises the *independent* `FLAG_DISABLED_BY_APP`, which survives a future screen
 * that loses FLAG_SECURE. Presenting it as the fix would be claiming credit for FLAG_SECURE's work.
 *
 * ## Channel 3 — assist. Empty by construction. Nothing to call, and that is the point.
 *
 * The assist walk uses `onProvideVirtualStructure`, which Compose does not implement at all — only
 * the autofill variant. Compose content is not made of Views, so assist receives a node for the
 * Compose view and nothing beneath it. Recorded here so the next reader does not add a "fix" and
 * believe they gained something.
 */
/**
 * Excludes this view and its whole subtree from the autofill structure.
 *
 * Call it on the **root of a window** (the decor view). Calling it on the Compose view instead
 * does nothing at all, and that trap is the reason this function exists rather than a bare
 * assignment at the call site: `AndroidComposeView` overrides `getImportantForAutofill()` to
 * `return IMPORTANT_FOR_AUTOFILL_YES` with no backing field, so `composeView.importantForAutofill =
 * …` writes a value the getter never reads. The setter reports no error. A fix applied there would
 * have looked applied and done nothing.
 *
 * The working lever is an **ancestor**. `View.isImportantForAutofill()` walks up the parents
 * *before* consulting the view's own value and hard-returns `false` at the first ancestor marked
 * `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` — read in the platform source, not recalled, and
 * re-proven on a device by `StructureExportTest`.
 *
 * **Residual, declared because it is real.** `AssistStructure.resolveViewAutofillFlags` re-admits
 * excluded views under exactly three conditions: a *manual* request (the user long-presses and
 * chooses Autofill), autofill compatibility mode, and PCC detection. Automatic requests — the ones
 * that fire merely because a field took focus, which is the entire exposure this closes — are not
 * among them. A user who deliberately asks for autofill still gets it; nobody gets it silently.
 */
public fun View.denyAutofillExport() {
    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
}

/**
 * Raises `FLAG_DISABLED_BY_APP` on content capture for this app's process.
 *
 * Belt on braces, and labelled as such: FLAG_SECURE already disables the channel (see the file
 * KDoc). This call is what keeps it disabled if a screen ever loses FLAG_SECURE.
 *
 * `ContentCaptureManager` is API 29+. Below that the channel does not exist, so there is nothing
 * to disable — an absence, not an omission.
 */
public fun Context.denyContentCapture() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        getSystemService(ContentCaptureManager::class.java)?.setContentCaptureEnabled(false)
    }
}
