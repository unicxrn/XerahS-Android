package com.xerahs.android.ui.intake

import com.xerahs.android.feature.upload.intake.singleUrlOrNull

/** What "Upload clipboard" does with the current clip. */
sealed interface ClipAction {
    /** A file or image the app may copy. */
    data object CopyUri : ClipAction
    data class Link(val url: String) : ClipAction
    data class Text(val text: String) : ClipAction
    data object Unreadable : ClipAction
    data object Empty : ClipAction
}

object ClipboardIntake {
    /**
     * [uriAllowed] is null when the clip has no URI, else whether [IntakeUriPolicy] accepts it.
     * A readable URI wins; otherwise the clip's text is used, and a single link opens the link sheet.
     */
    fun decide(uriAllowed: Boolean?, text: String?): ClipAction {
        if (uriAllowed == true) return ClipAction.CopyUri
        if (!text.isNullOrBlank()) {
            return singleUrlOrNull(text)?.let(ClipAction::Link) ?: ClipAction.Text(text)
        }
        return if (uriAllowed == false) ClipAction.Unreadable else ClipAction.Empty
    }
}
