package com.example.core.model

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.core.security.SecureCredentialStore
import com.example.data.local.db.DownloadQueueDao
import com.example.data.local.db.DownloadTaskEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

enum class DownloadQueueStatus(val label: String) {
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    VERIFYING("Verifying SHA-256"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

data class DownloadQueueItem(
    val id: String,
    val repoId: String,
    val fileName: String,
    val displayName: String,
    val downloadUrl: String,
    val destinationPath: String,
    val expectedSha256: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
    val status: DownloadQueueStatus,
    val supportsResume: Boolean,
    val errorMessage: String?,
    val modelSlotId: String,
    val runtimeFormat: RuntimeFormatId,
    val architecture: String?,
    val quantization: String?,
    val parameterCount: String?,
    val license: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val contextWindow: Int = 4096
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val remainingBytes: Long
        get() = if (totalBytes > downloadedBytes) totalBytes - downloadedBytes else 0L
}

data class EnqueueDownloadRequest(
    val repoId: String,
    val fileName: String,
    val displayName: String,
    val downloadUrl: String,
    val expectedSha256: String? = null,
    val totalBytes: Long = 0L,
    val modelSlotId: String? = null,
    val runtimeFormat: RuntimeFormatId = RuntimeFormatId.LITERT_LM,
    val architecture: String? = null,
    val quantization: String? = null,
    val parameterCount: String? = null,
    val license: String? = null,
    val contextWindow: Int = 4096
)

sealed interface EnqueueResult {
    data class Enqueued(val item: DownloadQueueItem) : EnqueueResult
    data class Duplicate(val existing: DownloadQueueItem, val reason: String) : EnqueueResult
    data class Rejected(val reason: String) : EnqueueResult
}

fun interface DownloadServiceBridge {
    fun syncServiceState(hasActiveDownloads: Boolean, title: String, subtitle: String, progressPercent: Int)
}

class DownloadQueueManager(
    context: Context,
    private val queueDao: DownloadQueueDao,
    private val modelManager: ModelManager,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val storageSpaceProvider: StorageSpaceProvider = ModelArtifactManager.defaultStorageSpaceProvider(),
    private val serviceBridge: DownloadServiceBridge = defaultServiceBridge(context.applicationContext)
) {
    private val appContext = context.applicationContext
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enqueueMutex = Mutex()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()

    val modelsDirectory: File
        get() = File(appContext.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }

    fun observeQueue(): Flow<List<DownloadQueueItem>> =
        queueDao.observeAllTasks().map { list -> list.map { it.toDomain() } }

    suspend fun enqueueAndStart(
        request: EnqueueDownloadRequest,
        bearerToken: String,
        autoStart: Boolean = true
    ): EnqueueResult = enqueueMutex.withLock {
        if (!SecureCredentialStore.isSafeModelFilename(request.fileName)) {
            return@withLock EnqueueResult.Rejected("Security policy blocked unsafe filename '${request.fileName}'.")
        }
        if (!isSafeDownloadUrl(request.downloadUrl)) {
            return@withLock EnqueueResult.Rejected("Security policy blocked insecure or invalid download URL.")
        }

        val existing = queueDao.findByRepoAndFile(request.repoId, request.fileName)?.toDomain()
        if (existing != null && existing.status in ACTIVE_OR_PAUSED_STATUSES) {
            return@withLock EnqueueResult.Duplicate(
                existing = existing,
                reason = "${request.fileName} is already in the download queue (${existing.status.label})."
            )
        }

        val safeLeaf = SecureCredentialStore.sanitizeLocalFileLeaf(request.repoId, request.fileName)
        val destFile = File(modelsDirectory, safeLeaf)
        val canonicalModelsRoot = runCatching { modelsDirectory.canonicalPath }.getOrNull()
        val canonicalDest = runCatching { destFile.canonicalPath }.getOrNull()
        if (canonicalModelsRoot == null || canonicalDest == null || !canonicalDest.startsWith("$canonicalModelsRoot${File.separator}")) {
            return@withLock EnqueueResult.Rejected("Security policy blocked invalid download destination path.")
        }
        if (existing?.status == DownloadQueueStatus.COMPLETED && destFile.exists() && destFile.length() > 1024L) {
            return@withLock EnqueueResult.Duplicate(
                existing = existing,
                reason = "${request.fileName} is already downloaded and verified."
            )
        }

        val requiredSpace = if (request.totalBytes > 0L) {
            request.totalBytes + STORAGE_HEADROOM_BYTES
        } else {
            MIN_REQUIRED_SPACE_BYTES
        }
        val availableSpace = storageSpaceProvider.availableBytes(modelsDirectory)
        if (availableSpace < requiredSpace) {
            val needMb = requiredSpace / (1024L * 1024L)
            val availMb = availableSpace / (1024L * 1024L)
            return@withLock EnqueueResult.Rejected(
                "Insufficient storage: ${availMb} MB free, requires at least ${needMb} MB."
            )
        }

        val now = System.currentTimeMillis()
        val taskId = existing?.id ?: "dl_${UUID.randomUUID()}"
        val resolvedSlotId = request.modelSlotId ?: "hf_${safeLeaf.substringBeforeLast('.').lowercase()}"
        val entity = DownloadTaskEntity(
            id = taskId,
            repoId = request.repoId,
            fileName = request.fileName,
            displayName = request.displayName,
            downloadUrl = request.downloadUrl,
            destinationPath = destFile.absolutePath,
            expectedSha256 = request.expectedSha256,
            totalBytes = request.totalBytes,
            downloadedBytes = 0L,
            speedBytesPerSec = 0L,
            etaSeconds = -1L,
            status = DownloadQueueStatus.QUEUED.name,
            supportsResume = false,
            errorMessage = null,
            modelSlotId = resolvedSlotId,
            runtimeFormat = request.runtimeFormat.name,
            architecture = request.architecture,
            quantization = request.quantization,
            parameterCount = request.parameterCount,
            license = request.license,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            contextWindow = request.contextWindow.coerceIn(512, 131072)
        )
        File("${destFile.absolutePath}.part").delete()
        queueDao.upsertTask(entity)

        if (autoStart) {
            launchTaskJob(entity.toDomain(), bearerToken)
        }
        EnqueueResult.Enqueued(entity.toDomain())
    }

    fun startOrResumeTask(taskId: String, bearerToken: String) {
        managerScope.launch {
            val item = queueDao.getTaskById(taskId)?.toDomain() ?: return@launch
            if (activeJobs.containsKey(taskId)) return@launch
            launchTaskJob(item, bearerToken)
        }
    }

    fun pauseDownload(taskId: String) {
        pausedIds.add(taskId)
        activeJobs.remove(taskId)?.cancel()
        managerScope.launch {
            val task = queueDao.getTaskById(taskId) ?: return@launch
            val partLen = File("${task.destinationPath}.part").let { if (it.exists()) it.length() else task.downloadedBytes }
            queueDao.updateProgress(
                id = taskId,
                downloadedBytes = partLen,
                totalBytes = task.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = -1L,
                status = DownloadQueueStatus.PAUSED.name,
                supportsResume = task.supportsResume,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
            refreshBackgroundServiceStatus()
        }
    }

    fun cancelDownload(taskId: String) {
        cancelledIds.add(taskId)
        pausedIds.remove(taskId)
        activeJobs.remove(taskId)?.cancel()
        managerScope.launch {
            val task = queueDao.getTaskById(taskId) ?: return@launch
            File("${task.destinationPath}.part").delete()
            queueDao.updateProgress(
                id = taskId,
                downloadedBytes = 0L,
                totalBytes = task.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = -1L,
                status = DownloadQueueStatus.CANCELLED.name,
                supportsResume = false,
                errorMessage = "Cancelled by user",
                updatedAt = System.currentTimeMillis()
            )
            refreshBackgroundServiceStatus()
        }
    }

    suspend fun clearFinished() {
        queueDao.clearFinished()
    }

    private fun launchTaskJob(item: DownloadQueueItem, bearerToken: String) {
        pausedIds.remove(item.id)
        cancelledIds.remove(item.id)
        val job = managerScope.launch {
            try {
                executeDownloadTask(item.id, bearerToken)
            } finally {
                activeJobs.remove(item.id)
                refreshBackgroundServiceStatus()
            }
        }
        activeJobs[item.id] = job
    }

    internal suspend fun executeDownloadTask(taskId: String, bearerToken: String): Result<String> =
        withContext(Dispatchers.IO) {
            val initialTask = queueDao.getTaskById(taskId)?.toDomain()
                ?: return@withContext Result.failure(IllegalStateException("Download task not found."))

            val targetFile = File(initialTask.destinationPath)
            val partFile = File("${initialTask.destinationPath}.part")
            val existingPartBytes = if (partFile.exists()) partFile.length() else 0L

            val remainingExpected = (initialTask.totalBytes - existingPartBytes).coerceAtLeast(0L)
            val requiredCheck = if (remainingExpected > 0L) remainingExpected + STORAGE_HEADROOM_BYTES else MIN_REQUIRED_SPACE_BYTES
            val available = storageSpaceProvider.availableBytes(modelsDirectory)
            if (available < requiredCheck) {
                val msg = "Insufficient storage (${available / (1024L * 1024L)} MB free)."
                queueDao.updateProgress(
                    id = taskId,
                    downloadedBytes = existingPartBytes,
                    totalBytes = initialTask.totalBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                    status = DownloadQueueStatus.FAILED.name,
                    supportsResume = initialTask.supportsResume,
                    errorMessage = msg,
                    updatedAt = System.currentTimeMillis()
                )
                return@withContext Result.failure(IllegalStateException(msg))
            }

            // Update status to DOWNLOADING immediately
            queueDao.updateProgress(
                id = taskId,
                downloadedBytes = existingPartBytes,
                totalBytes = initialTask.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = -1L,
                status = DownloadQueueStatus.DOWNLOADING.name,
                supportsResume = initialTask.supportsResume,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
            serviceBridge.syncServiceState(true, initialTask.displayName, "Connecting…", 0)

            val requestBuilder = Request.Builder()
                .url(initialTask.downloadUrl)
                .header("User-Agent", "JarvisAndroidModelHub/1.0")
                .get()

            val trimmedToken = bearerToken.trim()
            if (trimmedToken.isNotEmpty() && isTrustedAuthHost(initialTask.downloadUrl)) {
                requestBuilder.header("Authorization", "Bearer $trimmedToken")
            }

            if (existingPartBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingPartBytes-")
            }

            try {
                httpClient.newCall(requestBuilder.build()).execute().use { res ->
                    if (res.code == 416 && existingPartBytes > 0L && initialTask.totalBytes in 1L..existingPartBytes) {
                        return@withContext finalizeAndVerify(initialTask, partFile, targetFile)
                    }

                    if (!res.isSuccessful) {
                        val msg = when (res.code) {
                            401, 403 -> "HTTP ${res.code}: Gated repository requires license acceptance & valid HF Read token."
                            404 -> "HTTP 404: Artifact not found in repository."
                            else -> "Download failed (HTTP ${res.code})."
                        }
                        queueDao.updateProgress(
                            id = taskId,
                            downloadedBytes = existingPartBytes,
                            totalBytes = initialTask.totalBytes,
                            speedBytesPerSec = 0L,
                            etaSeconds = -1L,
                            status = DownloadQueueStatus.FAILED.name,
                            supportsResume = initialTask.supportsResume,
                            errorMessage = msg,
                            updatedAt = System.currentTimeMillis()
                        )
                        return@withContext Result.failure(IllegalStateException(msg))
                    }

                    val body = res.body ?: throw IllegalStateException("Empty download response body.")
                    val isPartial206 = res.code == 206
                    val acceptRanges = res.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true || isPartial206

                    val appendMode = isPartial206 && existingPartBytes > 0L
                    var bytesWritten = if (appendMode) existingPartBytes else 0L
                    val bodyLen = body.contentLength()
                    val resolvedTotal = when {
                        isPartial206 && bodyLen > 0L -> existingPartBytes + bodyLen
                        !isPartial206 && bodyLen > 0L -> bodyLen
                        else -> initialTask.totalBytes
                    }

                    if (!appendMode && partFile.exists()) {
                        partFile.delete()
                    }

                    val buffer = ByteArray(64 * 1024)
                    var lastReportTimeMs = System.currentTimeMillis()
                    var bytesSinceLastReport = 0L
                    var emaSpeed = 0.0

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, appendMode).use { output ->
                            while (true) {
                                if (!coroutineContext.isActive || pausedIds.contains(taskId) || cancelledIds.contains(taskId)) {
                                    output.flush()
                                    throw CancellationException("Download interrupted.")
                                }
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesWritten += read
                                bytesSinceLastReport += read

                                val nowMs = System.currentTimeMillis()
                                val elapsedMs = nowMs - lastReportTimeMs
                                if (elapsedMs >= 300L) {
                                    val instantBps = (bytesSinceLastReport * 1000.0) / elapsedMs.coerceAtLeast(1L)
                                    emaSpeed = if (emaSpeed <= 0.0) instantBps else (0.35 * instantBps + 0.65 * emaSpeed)
                                    val speedLong = emaSpeed.toLong().coerceAtLeast(0L)
                                    val etaSec = if (speedLong > 0L && resolvedTotal > bytesWritten) {
                                        (resolvedTotal - bytesWritten) / speedLong
                                    } else {
                                        -1L
                                    }
                                    queueDao.updateProgress(
                                        id = taskId,
                                        downloadedBytes = bytesWritten,
                                        totalBytes = resolvedTotal,
                                        speedBytesPerSec = speedLong,
                                        etaSeconds = etaSec,
                                        status = DownloadQueueStatus.DOWNLOADING.name,
                                        supportsResume = acceptRanges,
                                        errorMessage = null,
                                        updatedAt = nowMs
                                    )
                                    val pct = if (resolvedTotal > 0L) ((bytesWritten * 100L) / resolvedTotal).toInt() else 0
                                    val mbSpeed = emaSpeed / (1024.0 * 1024.0)
                                    serviceBridge.syncServiceState(
                                        hasActiveDownloads = true,
                                        title = initialTask.displayName,
                                        subtitle = String.format(java.util.Locale.US, "%.1f MB/s • %d%%", mbSpeed, pct),
                                        progressPercent = pct
                                    )
                                    lastReportTimeMs = nowMs
                                    bytesSinceLastReport = 0L
                                }
                            }
                            output.fd.sync()
                        }
                    }

                    val updatedTask = initialTask.copy(
                        downloadedBytes = bytesWritten,
                        totalBytes = if (resolvedTotal > 0L) resolvedTotal else bytesWritten,
                        supportsResume = acceptRanges
                    )
                    return@withContext finalizeAndVerify(updatedTask, partFile, targetFile)
                }
            } catch (e: CancellationException) {
                val now = System.currentTimeMillis()
                if (cancelledIds.remove(taskId)) {
                    partFile.delete()
                    queueDao.updateProgress(
                        id = taskId,
                        downloadedBytes = 0L,
                        totalBytes = initialTask.totalBytes,
                        speedBytesPerSec = 0L,
                        etaSeconds = -1L,
                        status = DownloadQueueStatus.CANCELLED.name,
                        supportsResume = false,
                        errorMessage = "Cancelled",
                        updatedAt = now
                    )
                } else {
                    pausedIds.remove(taskId)
                    val savedBytes = if (partFile.exists()) partFile.length() else initialTask.downloadedBytes
                    queueDao.updateProgress(
                        id = taskId,
                        downloadedBytes = savedBytes,
                        totalBytes = initialTask.totalBytes,
                        speedBytesPerSec = 0L,
                        etaSeconds = -1L,
                        status = DownloadQueueStatus.PAUSED.name,
                        supportsResume = initialTask.supportsResume,
                        errorMessage = null,
                        updatedAt = now
                    )
                }
                Result.failure(e)
            } catch (e: Exception) {
                val savedBytes = if (partFile.exists()) partFile.length() else initialTask.downloadedBytes
                queueDao.updateProgress(
                    id = taskId,
                    downloadedBytes = savedBytes,
                    totalBytes = initialTask.totalBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                    status = DownloadQueueStatus.FAILED.name,
                    supportsResume = initialTask.supportsResume,
                    errorMessage = e.localizedMessage ?: "Network download failed.",
                    updatedAt = System.currentTimeMillis()
                )
                Result.failure(e)
            }
        }

    private suspend fun finalizeAndVerify(
        task: DownloadQueueItem,
        partFile: File,
        targetFile: File
    ): Result<String> {
        queueDao.updateProgress(
            id = task.id,
            downloadedBytes = partFile.length(),
            totalBytes = task.totalBytes.coerceAtLeast(partFile.length()),
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            status = DownloadQueueStatus.VERIFYING.name,
            supportsResume = task.supportsResume,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )

        if (!partFile.exists() || partFile.length() <= 1024L) {
            partFile.delete()
            val msg = "Downloaded file is missing or truncated."
            queueDao.updateProgress(
                id = task.id,
                downloadedBytes = 0L,
                totalBytes = task.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = -1L,
                status = DownloadQueueStatus.FAILED.name,
                supportsResume = false,
                errorMessage = msg,
                updatedAt = System.currentTimeMillis()
            )
            return Result.failure(IllegalStateException(msg))
        }

        val expectedSha = task.expectedSha256?.trim()?.lowercase()
        if (!expectedSha.isNullOrEmpty()) {
            val actualSha = computeSha256(partFile)
            if (actualSha != expectedSha) {
                partFile.delete()
                targetFile.delete()
                val msg = "SHA-256 checksum mismatch (corrupted download removed)."
                queueDao.updateProgress(
                    id = task.id,
                    downloadedBytes = 0L,
                    totalBytes = task.totalBytes,
                    speedBytesPerSec = 0L,
                    etaSeconds = -1L,
                    status = DownloadQueueStatus.FAILED.name,
                    supportsResume = false,
                    errorMessage = msg,
                    updatedAt = System.currentTimeMillis()
                )
                return Result.failure(IllegalStateException(msg))
            }
        }

        if (targetFile.exists()) targetFile.delete()
        if (!partFile.renameTo(targetFile)) {
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()
        }

        val finalSize = targetFile.length()
        val isExecutableNow = task.runtimeFormat == RuntimeFormatId.LITERT_LM
        val slotStatus = if (isExecutableNow) ModelStatus.READY else ModelStatus.PHASE_3_PENDING
        val existingSlot = modelManager.getModelById(task.modelSlotId)

        modelManager.upsertHubModelSlot(
            ModelSlot(
                id = task.modelSlotId,
                displayName = existingSlot?.displayName ?: task.displayName,
                providerType = ModelProviderType.LOCAL_ON_DEVICE,
                engineFamily = existingSlot?.engineFamily ?: task.runtimeFormat.label,
                parameterHint = existingSlot?.parameterHint
                    ?: listOfNotNull(task.parameterCount, task.quantization, "${finalSize / (1024L * 1024L)} MB")
                        .joinToString(" • "),
                contextWindow = existingSlot?.contextWindow ?: task.contextWindow.coerceIn(512, 131072),
                status = slotStatus,
                isActive = existingSlot?.isActive ?: false,
                isCustom = existingSlot?.isCustom ?: false,
                localFilePath = targetFile.absolutePath,
                downloadUrl = task.downloadUrl,
                expectedSha256 = task.expectedSha256,
                expectedSizeBytes = finalSize,
                artifactState = ArtifactState.READY,
                sourceRepoId = task.repoId,
                license = task.license,
                architecture = task.architecture,
                quantization = task.quantization,
                parameterCount = task.parameterCount,
                runtimeFormat = task.runtimeFormat
            )
        )

        queueDao.updateProgress(
            id = task.id,
            downloadedBytes = finalSize,
            totalBytes = finalSize,
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            status = DownloadQueueStatus.COMPLETED.name,
            supportsResume = task.supportsResume,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )
        return Result.success(targetFile.absolutePath)
    }

    private suspend fun refreshBackgroundServiceStatus() {
        val tasks = queueDao.getAllTasksOnce()
        val active = tasks.firstOrNull {
            it.status == DownloadQueueStatus.DOWNLOADING.name || it.status == DownloadQueueStatus.VERIFYING.name
        }
        if (active == null) {
            serviceBridge.syncServiceState(false, "", "", 0)
        }
    }

    private fun isSafeDownloadUrl(url: String): Boolean {
        val parsed = url.trim().toHttpUrlOrNull() ?: return false
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
        val host = parsed.host.lowercase()
        if (host == "169.254.169.254" || host == "metadata.google.internal" || host.startsWith("169.254.")) {
            return false
        }
        return parsed.isHttps || (parsed.scheme == "http" && (host == "localhost" || host == "127.0.0.1"))
    }

    private fun isTrustedAuthHost(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val host = parsed.host.lowercase()
        val isTrustedHttpsHf = parsed.isHttps && (
            host == "huggingface.co" ||
                host.endsWith(".huggingface.co") ||
                host.endsWith(".hf.co")
            )
        val isLoopback = host == "localhost" || host == "127.0.0.1"
        return isTrustedHttpsHf || isLoopback
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

    private fun DownloadTaskEntity.toDomain(): DownloadQueueItem = DownloadQueueItem(
        id = id,
        repoId = repoId,
        fileName = fileName,
        displayName = displayName,
        downloadUrl = downloadUrl,
        destinationPath = destinationPath,
        expectedSha256 = expectedSha256,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        speedBytesPerSec = speedBytesPerSec,
        etaSeconds = etaSeconds,
        status = runCatching { DownloadQueueStatus.valueOf(status) }.getOrDefault(DownloadQueueStatus.FAILED),
        supportsResume = supportsResume,
        errorMessage = errorMessage,
        modelSlotId = modelSlotId,
        runtimeFormat = runCatching { RuntimeFormatId.valueOf(runtimeFormat) }.getOrDefault(RuntimeFormatId.UNKNOWN),
        architecture = architecture,
        quantization = quantization,
        parameterCount = parameterCount,
        license = license,
        createdAt = createdAt,
        updatedAt = updatedAt,
        contextWindow = contextWindow
    )

    companion object {
        private const val STORAGE_HEADROOM_BYTES = 128L * 1024L * 1024L
        private const val MIN_REQUIRED_SPACE_BYTES = 256L * 1024L * 1024L
        private val ACTIVE_OR_PAUSED_STATUSES = setOf(
            DownloadQueueStatus.QUEUED,
            DownloadQueueStatus.DOWNLOADING,
            DownloadQueueStatus.PAUSED,
            DownloadQueueStatus.VERIFYING
        )

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun defaultServiceBridge(appContext: Context): DownloadServiceBridge =
            DownloadServiceBridge { hasActive, title, subtitle, progress ->
                runCatching {
                    if (hasActive) {
                        val intent = Intent(appContext, ModelDownloadService::class.java).apply {
                            action = ModelDownloadService.ACTION_UPDATE
                            putExtra(ModelDownloadService.EXTRA_TITLE, title)
                            putExtra(ModelDownloadService.EXTRA_SUBTITLE, subtitle)
                            putExtra(ModelDownloadService.EXTRA_PROGRESS, progress)
                        }
                        ContextCompat.startForegroundService(appContext, intent)
                    } else {
                        appContext.stopService(Intent(appContext, ModelDownloadService::class.java))
                    }
                }
            }
    }
}
