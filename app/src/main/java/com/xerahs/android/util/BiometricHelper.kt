package com.xerahs.android.util

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    /**
     * Biometrics with the device PIN, pattern or password as a fallback. STRONG plus
     * DEVICE_CREDENTIAL is only supported from API 30, so API 28-29 use WEAK instead.
     */
    val authenticators: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

    fun status(activity: FragmentActivity): Int =
        BiometricManager.from(activity).canAuthenticate(authenticators)

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        status(activity) == BiometricManager.BIOMETRIC_SUCCESS

    /** True when the device has no screen lock or biometric enrolled. */
    fun needsScreenLockSetup(activity: FragmentActivity): Boolean =
        status(activity) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

    fun showPrompt(
        activity: FragmentActivity,
        title: String = "Authentication Required",
        subtitle: String = "Verify your identity to continue",
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }

            override fun onAuthenticationFailed() {
                // Individual attempt failed, prompt stays open
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            // No negative button: it is not allowed together with DEVICE_CREDENTIAL.
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}
