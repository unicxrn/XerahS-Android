package com.xerahs.android.core.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.xerahs.android.core.domain.repository.OcrException
import com.xerahs.android.core.domain.repository.OcrRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.resume

/** Maps a raw recognition failure to a typed [OcrException], or returns it unchanged. */
fun mapOcrFailure(playServicesOk: Boolean, error: Throwable): Throwable = when {
    !playServicesOk -> OcrException.Unavailable()
    error is MlKitException && error.errorCode == MlKitException.UNAVAILABLE -> OcrException.Downloading()
    error is ApiException && error.statusCode == CommonStatusCodes.API_NOT_CONNECTED -> OcrException.Unavailable()
    else -> error
}

class OcrRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrRepository {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val installRequested = AtomicBoolean(false)

    private fun playServicesOk(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Asks Play Services to download the model once per app start, without blocking. */
    private fun requestModuleInstall() {
        if (!installRequested.compareAndSet(false, true)) return
        try {
            val client = ModuleInstall.getClient(context)
            client.areModulesAvailable(recognizer)
                .addOnSuccessListener { response ->
                    if (!response.areModulesAvailable()) {
                        client.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build())
                            .addOnFailureListener { Log.w(TAG, "OCR model install request failed", it) }
                    }
                }
                .addOnFailureListener { Log.w(TAG, "OCR model availability check failed", it) }
        } catch (e: Exception) {
            Log.w(TAG, "OCR model install request failed", e)
        }
    }

    override suspend fun recognizeText(imagePath: String): Result<String> {
        val ok = playServicesOk()
        if (ok) requestModuleInstall()
        return suspendCancellableCoroutine { cont ->
            fun fail(e: Exception) {
                Log.w(TAG, "Text recognition failed", e)
                if (cont.isActive) cont.resume(Result.failure(mapOcrFailure(ok, e)))
            }
            if (!ok) {
                fail(IllegalStateException("Google Play services unavailable"))
                return@suspendCancellableCoroutine
            }
            try {
                val image = InputImage.fromFilePath(context, Uri.fromFile(File(imagePath)))
                recognizer.process(image)
                    .addOnSuccessListener { result -> if (cont.isActive) cont.resume(Result.success(result.text)) }
                    .addOnFailureListener { e -> fail(e) }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private companion object {
        const val TAG = "OcrRepository"
    }
}
