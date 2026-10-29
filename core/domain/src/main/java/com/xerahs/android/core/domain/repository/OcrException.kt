package com.xerahs.android.core.domain.repository

sealed class OcrException(message: String) : Exception(message) {
    /** Play Services is missing or can't run text recognition on this device. */
    class Unavailable : OcrException("Text recognition isn't available on this device")

    /** The recognition model is still being downloaded. */
    class Downloading : OcrException("Text recognition is still downloading. Try again in a moment")
}
