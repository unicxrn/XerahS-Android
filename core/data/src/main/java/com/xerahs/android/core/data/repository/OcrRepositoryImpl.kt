package com.xerahs.android.core.data.repository

import android.content.Context
import android.net.Uri
import com.google.android.gms.common.api.ApiException
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.xerahs.android.core.domain.repository.OcrRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume

class OcrRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrRepository {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(imagePath: String): Result<String> =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(context, Uri.fromFile(File(imagePath)))
                recognizer.process(image)
                    .addOnSuccessListener { result -> if (cont.isActive) cont.resume(Result.success(result.text)) }
                    .addOnFailureListener { e -> if (cont.isActive) cont.resume(Result.failure(mapFailure(e))) }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Result.failure(mapFailure(e)))
            }
        }

    private fun mapFailure(e: Exception): Exception {
        val unavailable = (e is MlKitException && e.errorCode == MlKitException.UNAVAILABLE) ||
            e is ApiException
        return if (unavailable) Exception(UNAVAILABLE_MESSAGE, e) else e
    }

    private companion object {
        const val UNAVAILABLE_MESSAGE = "Text recognition isn't available on this device"
    }
}
