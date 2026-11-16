package com.example.core.vision

import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Offline on-device OCR powered by bundled ML Kit Latin Text Recognition (`16.0.1`).
 * Requires zero cloud API calls. Provides a deterministic test hook for JVM Robolectric suites.
 */
class OnDeviceOcrEngine {

    @Volatile
    var testOcrOverride: ((Bitmap) -> OcrResult)? = null

    suspend fun recognizeBitmap(bitmap: Bitmap): OcrResult {
        testOcrOverride?.let { overrideFn ->
            return overrideFn(bitmap)
        }
        val startMs = SystemClock.elapsedRealtime()
        return runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine<OcrResult> { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                        val blocks = visionText.textBlocks.mapNotNull { blk ->
                            val rect = blk.boundingBox ?: return@mapNotNull null
                            val clean = blk.text.trim()
                            if (clean.isEmpty()) return@mapNotNull null
                            OcrTextBlock(
                                text = clean,
                                left = rect.left,
                                top = rect.top,
                                right = rect.right,
                                bottom = rect.bottom
                            )
                        }
                        if (cont.isActive) {
                            cont.resume(
                                OcrResult(
                                    fullText = visionText.text.trim(),
                                    blocks = blocks,
                                    durationMs = elapsed,
                                    succeeded = true
                                )
                            )
                        }
                        runCatching { recognizer.close() }
                    }
                    .addOnFailureListener { err ->
                        val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                        if (cont.isActive) {
                            cont.resume(
                                OcrResult(
                                    fullText = "",
                                    blocks = emptyList(),
                                    durationMs = elapsed,
                                    succeeded = false,
                                    errorMessage = err.localizedMessage ?: err.javaClass.simpleName
                                )
                            )
                        }
                        runCatching { recognizer.close() }
                    }
            }
        }.getOrElse { err ->
            OcrResult(
                fullText = "",
                blocks = emptyList(),
                durationMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L),
                succeeded = false,
                errorMessage = err.localizedMessage ?: "ML Kit OCR unavailable in current runtime."
            )
        }
    }
}
