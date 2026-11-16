package com.example.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Fallback on-demand frame capture controller using user-authorized MediaProjection
 * for API < 30 or situations where AccessibilityService.takeScreenshot() is unavailable.
 * Properly registers MediaProjection.Callback per Android 14+ requirements.
 */
object MediaProjectionCaptureController {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    private var activeProjection: MediaProjection? = null

    @Volatile
    private var registeredCallback: MediaProjection.Callback? = null

    fun attachProjection(context: Context, projection: MediaProjection) {
        clearSession()
        val mainHandler = Handler(Looper.getMainLooper())
        val cb = object : MediaProjection.Callback() {
            override fun onStop() {
                clearSession()
                _lastError.value = "MediaProjection session revoked by user or system."
            }
        }
        runCatching {
            projection.registerCallback(cb, mainHandler)
        }
        registeredCallback = cb
        activeProjection = projection
        _lastError.value = null
        _isReady.value = true
    }

    fun markLastError(message: String) {
        _lastError.value = message
    }

    fun clearSession() {
        val projection = activeProjection
        val cb = registeredCallback
        activeProjection = null
        registeredCallback = null
        _isReady.value = false
        if (projection != null) {
            runCatching {
                if (cb != null) projection.unregisterCallback(cb)
            }
            runCatching { projection.stop() }
        }
    }

    suspend fun captureSingleFrame(context: Context, timeoutMs: Long = 1600L): Bitmap? {
        val projection = activeProjection ?: return null
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceIn(360, 1080)
        val height = metrics.heightPixels.coerceIn(640, 2400)
        val densityDpi = metrics.densityDpi.coerceAtLeast(160)

        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                    var virtualDisplay: VirtualDisplay? = null
                    var settled = false

                    fun cleanup() {
                        runCatching { virtualDisplay?.release() }
                        runCatching { imageReader.setOnImageAvailableListener(null, null) }
                        runCatching { imageReader.close() }
                    }

                    imageReader.setOnImageAvailableListener({ reader ->
                        if (settled) return@setOnImageAvailableListener
                        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                        settled = true
                        val bitmap = runCatching {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val pixelStride = plane.pixelStride
                            val rowStride = plane.rowStride
                            val rowPadding = rowStride - pixelStride * width
                            val rawBitmap = Bitmap.createBitmap(
                                width + (rowPadding / pixelStride).coerceAtLeast(0),
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            rawBitmap.copyPixelsFromBuffer(buffer)
                            if (rawBitmap.width != width) {
                                Bitmap.createBitmap(rawBitmap, 0, 0, width, height).also { rawBitmap.recycle() }
                            } else {
                                rawBitmap
                            }
                        }.getOrNull()
                        image.close()
                        cleanup()
                        if (cont.isActive) cont.resume(bitmap)
                    }, Handler(Looper.getMainLooper()))

                    virtualDisplay = runCatching {
                        projection.createVirtualDisplay(
                            "JarvisOneShotCapture",
                            width,
                            height,
                            densityDpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            imageReader.surface,
                            null,
                            Handler(Looper.getMainLooper())
                        )
                    }.getOrNull()

                    if (virtualDisplay == null && !settled) {
                        settled = true
                        cleanup()
                        clearSession()
                        if (cont.isActive) cont.resume(null)
                    }

                    cont.invokeOnCancellation {
                        settled = true
                        cleanup()
                    }
                }
            }
        }
    }
}
