package com.xerahs.android.ui.intake

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardIntakeTest {
    @Test fun readableUriWins() = assertEquals(ClipAction.CopyUri, ClipboardIntake.decide(true, "https://a.b"))
    @Test fun singleLinkOpensSheet() = assertEquals(ClipAction.Link("https://a.b/x"), ClipboardIntake.decide(null, " https://a.b/x\n"))
    @Test fun otherTextIsText() = assertEquals(ClipAction.Text("see https://a.b"), ClipboardIntake.decide(null, "see https://a.b"))
    @Test fun blockedUriFallsBackToText() = assertEquals(ClipAction.Text("hi"), ClipboardIntake.decide(false, "hi"))
    @Test fun blockedUriWithoutText() = assertEquals(ClipAction.Unreadable, ClipboardIntake.decide(false, null))
    @Test fun emptyClip() = assertEquals(ClipAction.Empty, ClipboardIntake.decide(null, null))
    @Test fun blankTextIsEmpty() = assertEquals(ClipAction.Empty, ClipboardIntake.decide(null, "  "))
}
