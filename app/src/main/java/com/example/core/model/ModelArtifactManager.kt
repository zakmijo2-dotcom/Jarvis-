package com.example.core.model

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

fun interface StorageSpaceProvider {
    fun availableBytes(directory: File): Long
}

sealed interface ArtifactTransferState {
    data object Idle : ArtifactTransferState
    data class Downloading(
        val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : ArtifactTransferState {
        val progress: Float
            get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }

    data class Importing(
        val modelId: String,
        val bytesCopied: Long
    ) : ArtifactTransferState

    data class Verifying(val modelId: String) : ArtifactTransferState
    data class Completed(val modelId: String, val filePath: String) : ArtifactTransferState
    data class Error(
        val modelId: String,
        val message: String,
        val isCorrupted: Boolean = false
    ) : ArtifactTransferState
}

class ModelArtifactManager(
    context: Context,
    private val modelManager: ModelManager,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val storageSpaceProvider: StorageSpaceProvider = defaultStorageSpaceProvider()
) {
    private val appContext = context.applicationContext
    private val transferScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeTransferJob: Job? = null

    private val _transferState = MutableStateFlow<ArtifactTransferState>(ArtifactTransferState.Idle)
    val transferState: StateFlow<ArtifactTransferState> = _transferState.asStateFlow()

    val modelsDirectory: File
        get() = File(appContext.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }

    fun availableStorageBytes(): Long = storageSpaceProvider.availableBytes(modelsDirectory)

    fun startDownload(slot: ModelSlot, bearerToken: String) {
        val url = slot.downloadUrl
        if (url.isNullOrBlank()) {
            _transferState.value = ArtifactTransferState.Error(
                modelId = slot.id,
                message = "No download URL configured for ${slot.displayName}. Use local .litertlm import instead."
            )
            return
        }

        activeTransferJob?.cancel()
        activeTransferJob = transferScope.launch {
            downloadArtifactInternal(slot, url, bearerToken)
        }
    }

    suspend fun downloadArtifactInternal(
        slot: ModelSlot,
        url: String,
        bearerToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val requiredBytes = if (slot.expectedSizeBytes > 0L) {
            slot.expectedSizeBytes + STORAGE_HEADROOM_BYTES
        } else {
            MIN_REQUIRED_SPACE_BYTES
        }
        val available = availableStorageBytes()
        if (available < requiredBytes) {
            val needMb = requiredBytes / (1024L * 1024L)
            val availMb = available / (1024L * 1024L)
            val msg = "Insufficient storage: ${availMb} MB free, requires at least ${needMb} MB."
            _transferState.value = ArtifactTransferState.Error(slot.id, msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val targetFile = File(modelsDirectory, "${slot.id}.litertlm")
        val partFile = File(modelsDirectory, "${slot.id}.litertlm.part")
        partFile.delete()

        modelManager.updateArtifactState(
            id = slot.id,
            localFilePath = null,
            artifactState = ArtifactState.DOWNLOADING,
            status = ModelStatus.UNCONFIGURED
        )

        val requestBuilder = Request.Builder().url(url).get()
        if (bearerToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${bearerToken.trim()}")
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.use { res ->
                if (!res.isSuccessful) {
                    val msg = when (res.code) {
                        401, 403 -> "Authorization required (HTTP ${res.code}). Accept the Gemma license on Hugging Face and provide a valid Read token, or import a local .litertlm file."
                        404 -> "Model artifact not found at remote URL (HTTP 404)."
                        else -> "Download failed with HTTP ${res.code}."
                    }
                    modelManager.updateArtifactState(
                        id = slot.id,
                        localFilePath = null,
                        artifactState = ArtifactState.NOT_INSTALLED,
                        status = ModelStatus.UNCONFIGURED
                    )
                    _transferState.value = ArtifactTransferState.Error(slot.id, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }

                val body = res.body ?: throw IllegalStateException("Empty HTTP response body.")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: slot.expectedSizeBytes

                if (totalBytes > 0L && availableStorageBytes() < totalBytes + STORAGE_HEADROOM_BYTES) {
                    val msg = "Insufficient storage for ${totalBytes / (1024L * 1024L)} MB artifact."
                    modelManager.updateArtifactState(
                        id = slot.id,
                        localFilePath = null,
                        artifactState = ArtifactState.NOT_INSTALLED,
                        status = ModelStatus.UNCONFIGURED
                    )
                    _transferState.value = ArtifactTransferState.Error(slot.id, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var bytesReadTotal = 0L
                val buffer = ByteArray(64 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            if (!coroutineContext.isActive) {
                                throw CancellationException("Download cancelled by user.")
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            bytesReadTotal += read
                            _transferState.value = ArtifactTransferState.Downloading(
                                modelId = slot.id,
                                bytesDownloaded = bytesReadTotal,
                                totalBytes = totalBytes
                            )
                        }
                        output.fd.sync()
                    }
                }

                _transferState.value = ArtifactTransferState.Verifying(slot.id)
                modelManager.updateArtifactState(
                    id = slot.id,
                    localFilePath = null,
                    artifactState = ArtifactState.VERIFYING,
                    status = ModelStatus.UNCONFIGURED
                )

                val computedSha = digest.digest().joinToString("") { "%02x".format(it) }
                val expectedSha = slot.expectedSha256?.trim()?.lowercase()
                if (!expectedSha.isNullOrEmpty() && computedSha != expectedSha) {
                    partFile.delete()
                    targetFile.delete()
                    val msg = "SHA-256 mismatch (expected ${expectedSha.take(12)}…, got ${computedSha.take(12)}…). Corrupted download removed."
                    modelManager.updateArtifactState(
                        id = slot.id,
                        localFilePath = null,
                        artifactState = ArtifactState.CORRUPTED,
                        status = ModelStatus.UNCONFIGURED
                    )
                    _transferState.value = ArtifactTransferState.Error(slot.id, msg, isCorrupted = true)
                    return@withContext Result.failure(IllegalStateException(msg))
                }

                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }

                modelManager.updateArtifactState(
                    id = slot.id,
                    localFilePath = targetFile.absolutePath,
                    artifactState = ArtifactState.READY,
                    status = ModelStatus.READY
                )
                _transferState.value = ArtifactTransferState.Completed(slot.id, targetFile.absolutePath)
                Result.success(targetFile.absolutePath)
            }
        } catch (e: CancellationException) {
            partFile.delete()
            modelManager.updateArtifactState(
                id = slot.id,
                localFilePath = null,
                artifactState = ArtifactState.NOT_INSTALLED,
                status = ModelStatus.UNCONFIGURED
            )
            _transferState.value = ArtifactTransferState.Idle
            Result.failure(e)
        } catch (e: Exception) {
            partFile.delete()
            modelManager.updateArtifactState(
                id = slot.id,
                localFilePath = null,
                artifactState = ArtifactState.NOT_INSTALLED,
                status = ModelStatus.UNCONFIGURED
            )
            val msg = e.localizedMessage ?: "Model download failed."
            _transferState.value = ArtifactTransferState.Error(slot.id, msg)
            Result.failure(e)
        }
    }

    fun startImportFromUri(slot: ModelSlot, uri: Uri) {
        activeTransferJob?.cancel()
        activeTransferJob = transferScope.launch {
            importFromUriInternal(slot, uri)
        }
    }

    suspend fun importFromUriInternal(slot: ModelSlot, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            val partFile = File(modelsDirectory, "${slot.id}.litertlm.part")
            val targetFile = File(modelsDirectory, "${slot.id}.litertlm")
            partFile.delete()

            try {
                val inputStream = appContext.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open selected model file.")

                copyAndVerifyStream(slot, inputStream, partFile, targetFile)
            } catch (e: CancellationException) {
                partFile.delete()
                _transferState.value = ArtifactTransferState.Idle
                Result.failure(e)
            } catch (e: Exception) {
                partFile.delete()
                val msg = e.localizedMessage ?: "Failed to import .litertlm artifact."
                modelManager.updateArtifactState(
                    id = slot.id,
                    localFilePath = null,
                    artifactState = ArtifactState.NOT_INSTALLED,
                    status = ModelStatus.UNCONFIGURED
                )
                _transferState.value = ArtifactTransferState.Error(slot.id, msg)
                Result.failure(e)
            }
        }

    suspend fun importFromFileInternal(slot: ModelSlot, sourceFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            val partFile = File(modelsDirectory, "${slot.id}.litertlm.part")
            val targetFile = File(modelsDirectory, "${slot.id}.litertlm")
            partFile.delete()
            try {
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    throw IllegalStateException("Source file does not exist.")
                }
                val required = sourceFile.length() + STORAGE_HEADROOM_BYTES
                if (availableStorageBytes() < required) {
                    val msg = "Insufficient storage to copy ${sourceFile.length() / (1024L * 1024L)} MB model."
                    _transferState.value = ArtifactTransferState.Error(slot.id, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                copyAndVerifyStream(slot, FileInputStream(sourceFile), partFile, targetFile)
            } catch (e: Exception) {
                partFile.delete()
                val msg = e.localizedMessage ?: "Failed to import local file."
                _transferState.value = ArtifactTransferState.Error(slot.id, msg)
                Result.failure(e)
            }
        }

    private suspend fun copyAndVerifyStream(
        slot: ModelSlot,
        inputStream: InputStream,
        partFile: File,
        targetFile: File
    ): Result<String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        val buffer = ByteArray(64 * 1024)

        inputStream.use { input ->
            FileOutputStream(partFile).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    copied += read
                    _transferState.value = ArtifactTransferState.Importing(slot.id, copied)
                }
                output.fd.sync()
            }
        }

        if (copied <= 1024L) {
            partFile.delete()
            val msg = "Selected file is too small ($copied bytes) to be a valid .litertlm model."
            modelManager.updateArtifactState(
                id = slot.id,
                localFilePath = null,
                artifactState = ArtifactState.CORRUPTED,
                status = ModelStatus.UNCONFIGURED
            )
            _transferState.value = ArtifactTransferState.Error(slot.id, msg, isCorrupted = true)
            return Result.failure(IllegalStateException(msg))
        }

        _transferState.value = ArtifactTransferState.Verifying(slot.id)
        val computedSha = digest.digest().joinToString("") { "%02x".format(it) }
        val expectedSha = slot.expectedSha256?.trim()?.lowercase()
        if (!expectedSha.isNullOrEmpty() && computedSha != expectedSha) {
            partFile.delete()
            val msg = "Integrity check failed: SHA-256 does not match official ${slot.displayName} artifact."
            modelManager.updateArtifactState(
                id = slot.id,
                localFilePath = null,
                artifactState = ArtifactState.CORRUPTED,
                status = ModelStatus.UNCONFIGURED
            )
            _transferState.value = ArtifactTransferState.Error(slot.id, msg, isCorrupted = true)
            return Result.failure(IllegalStateException(msg))
        }

        if (targetFile.exists()) targetFile.delete()
        if (!partFile.renameTo(targetFile)) {
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()
        }

        modelManager.updateArtifactState(
            id = slot.id,
            localFilePath = targetFile.absolutePath,
            artifactState = ArtifactState.READY,
            status = ModelStatus.READY
        )
        _transferState.value = ArtifactTransferState.Completed(slot.id, targetFile.absolutePath)
        return Result.success(targetFile.absolutePath)
    }

    suspend fun verifyInstalledArtifact(slot: ModelSlot): Result<String> = withContext(Dispatchers.IO) {
        val path = slot.localFilePath
        if (path.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("No local model file path recorded."))
        }
        val file = File(path)
        if (!file.exists() || file.length() <= 1024L) {
            file.delete()
            modelManager.updateArtifactState(
                id = slot.id,
                localFilePath = null,
                artifactState = ArtifactState.CORRUPTED,
                status = ModelStatus.UNCONFIGURED
            )
            val msg = "Model file missing or truncated. Marked corrupted."
            _transferState.value = ArtifactTransferState.Error(slot.id, msg, isCorrupted = true)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        _transferState.value = ArtifactTransferState.Verifying(slot.id)
        val expectedSha = slot.expectedSha256?.trim()?.lowercase()
        if (!expectedSha.isNullOrEmpty()) {
            val actualSha = computeSha256(file)
            if (actualSha != expectedSha) {
                file.delete()
                modelManager.updateArtifactState(
                    id = slot.id,
                    localFilePath = null,
                    artifactState = ArtifactState.CORRUPTED,
                    status = ModelStatus.UNCONFIGURED
                )
                val msg = "Model file checksum failed verification and was removed."
                _transferState.value = ArtifactTransferState.Error(slot.id, msg, isCorrupted = true)
                return@withContext Result.failure(IllegalStateException(msg))
            }
        }

        modelManager.updateArtifactState(
            id = slot.id,
            localFilePath = file.absolutePath,
            artifactState = ArtifactState.READY,
            status = ModelStatus.READY
        )
        _transferState.value = ArtifactTransferState.Completed(slot.id, file.absolutePath)
        Result.success(file.absolutePath)
    }

    suspend fun markCorruptedAndClean(slot: ModelSlot, reason: String) = withContext(Dispatchers.IO) {
        slot.localFilePath?.let { runCatching { File(it).delete() } }
        modelManager.updateArtifactState(
            id = slot.id,
            localFilePath = null,
            artifactState = ArtifactState.CORRUPTED,
            status = ModelStatus.UNCONFIGURED
        )
        _transferState.value = ArtifactTransferState.Error(slot.id, reason, isCorrupted = true)
    }

    suspend fun deleteArtifact(slot: ModelSlot) = withContext(Dispatchers.IO) {
        slot.localFilePath?.let { runCatching { File(it).delete() } }
        File(modelsDirectory, "${slot.id}.litertlm").delete()
        File(modelsDirectory, "${slot.id}.litertlm.part").delete()
        modelManager.updateArtifactState(
            id = slot.id,
            localFilePath = null,
            artifactState = ArtifactState.NOT_INSTALLED,
            status = ModelStatus.UNCONFIGURED
        )
        _transferState.value = ArtifactTransferState.Idle
    }

    fun cancelTransfer() {
        activeTransferJob?.cancel()
        activeTransferJob = null
    }

    fun clearTransferStatus() {
        if (_transferState.value !is ArtifactTransferState.Downloading &&
            _transferState.value !is ArtifactTransferState.Importing
        ) {
            _transferState.value = ArtifactTransferState.Idle
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val STORAGE_HEADROOM_BYTES = 128L * 1024L * 1024L
        private const val MIN_REQUIRED_SPACE_BYTES = 256L * 1024L * 1024L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun defaultStorageSpaceProvider(): StorageSpaceProvider = StorageSpaceProvider { dir ->
            runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(0L)
        }
    }
}
