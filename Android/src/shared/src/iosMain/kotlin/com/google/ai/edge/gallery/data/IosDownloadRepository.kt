/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.data

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData

private const val TAG = "IosDownloadRepository"
private const val BUFFER_SIZE = 8192
private const val PROGRESS_UPDATE_INTERVAL_MS = 200L

/**
 * iOS implementation of [DownloadRepository] using Ktor with the Darwin engine
 * for downloading model files.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDownloadRepository(
  private val dataStoreRepository: DataStoreRepository,
) : DownloadRepository {

  private val fileManager = NSFileManager.defaultManager
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val httpClient = HttpClient()
  private val activeJobs = mutableMapOf<String, Job>()

  private val documentsDir: String by lazy {
    val paths = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    (paths.firstOrNull() as? NSURL)?.path ?: ""
  }

  override fun downloadModel(
    task: Task?,
    model: Model,
    onStatusUpdated: (model: Model, status: ModelDownloadStatus) -> Unit,
  ) {
    val url = model.url
    if (url.isEmpty()) {
      onStatusUpdated(
        model,
        ModelDownloadStatus(
          status = ModelDownloadStatusType.FAILED,
          errorMessage = "Model URL is empty",
        ),
      )
      return
    }

    // Cancel any existing download for this model.
    activeJobs[model.name]?.cancel()

    val job = scope.launch {
      try {
        downloadAllFiles(model, onStatusUpdated)
      } catch (e: CancellationException) {
        Napier.d(tag = TAG) { "Download cancelled for ${model.name}" }
        onStatusUpdated(
          model,
          ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED),
        )
      } catch (e: Exception) {
        Napier.e(tag = TAG) { "Download failed for ${model.name}: ${e.message}" }
        onStatusUpdated(
          model,
          ModelDownloadStatus(
            status = ModelDownloadStatusType.FAILED,
            errorMessage = e.message ?: "Download failed",
          ),
        )
      } finally {
        activeJobs.remove(model.name)
      }
    }
    activeJobs[model.name] = job
  }

  /**
   * Downloads the main model file plus any extra data files sequentially,
   * reporting combined progress.
   */
  private suspend fun downloadAllFiles(
    model: Model,
    onStatusUpdated: (model: Model, status: ModelDownloadStatus) -> Unit,
  ) {
    // Build the list of files to download: main file + extra data files.
    data class DownloadFile(val url: String, val fileName: String, val sizeInBytes: Long)

    val files = mutableListOf<DownloadFile>()
    files.add(DownloadFile(model.url, model.downloadFileName, model.sizeInBytes))
    for (extra in model.extraDataFiles) {
      files.add(DownloadFile(extra.url, extra.downloadFileName, extra.sizeInBytes))
    }

    val totalBytes = model.totalBytes.takeIf { it > 0 }
      ?: files.sumOf { it.sizeInBytes }

    // Ensure destination directory exists.
    val destDir = model.getPath(basePath = documentsDir).substringBeforeLast("/")
    if (!fileManager.fileExistsAtPath(destDir)) {
      fileManager.createDirectoryAtPath(
        destDir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
      )
    }

    var cumulativeDownloaded: Long = 0

    for (file in files) {
      val filePath = model.getPath(basePath = documentsDir, fileName = file.fileName)
      val tmpPath = "$filePath.$TMP_FILE_EXT"

      // Check for existing partial download.
      var existingBytes: Long = 0
      if (fileManager.fileExistsAtPath(tmpPath)) {
        val attrs = fileManager.attributesOfItemAtPath(tmpPath, error = null)
        existingBytes = (attrs?.get("NSFileSize") as? Number)?.toLong() ?: 0
      } else if (fileManager.fileExistsAtPath(filePath)) {
        // File already fully downloaded — skip it.
        val attrs = fileManager.attributesOfItemAtPath(filePath, error = null)
        val fileSize = (attrs?.get("NSFileSize") as? Number)?.toLong() ?: 0
        cumulativeDownloaded += fileSize
        continue
      }

      cumulativeDownloaded += existingBytes

      downloadSingleFile(
        url = file.url,
        tmpPath = tmpPath,
        finalPath = filePath,
        accessToken = model.accessToken,
        existingBytes = existingBytes,
        totalBytesAllFiles = totalBytes,
        previouslyDownloaded = cumulativeDownloaded,
        onProgress = { downloaded, bytesPerSecond, remainingMs ->
          onStatusUpdated(
            model,
            ModelDownloadStatus(
              status = ModelDownloadStatusType.IN_PROGRESS,
              totalBytes = totalBytes,
              receivedBytes = downloaded,
              bytesPerSecond = bytesPerSecond,
              remainingMs = remainingMs,
            ),
          )
        },
      )

      cumulativeDownloaded = cumulativeDownloaded - existingBytes +
        ((fileManager.attributesOfItemAtPath(filePath, error = null)
          ?.get("NSFileSize") as? Number)?.toLong() ?: file.sizeInBytes)
    }

    // All files downloaded successfully.
    onStatusUpdated(
      model,
      ModelDownloadStatus(
        status = ModelDownloadStatusType.SUCCEEDED,
        totalBytes = totalBytes,
        receivedBytes = totalBytes,
      ),
    )
  }

  /**
   * Downloads a single file using Ktor streaming with progress reporting.
   * Writes to a temp file, then renames on completion.
   */
  private suspend fun downloadSingleFile(
    url: String,
    tmpPath: String,
    finalPath: String,
    accessToken: String?,
    existingBytes: Long,
    totalBytesAllFiles: Long,
    previouslyDownloaded: Long,
    onProgress: (totalDownloaded: Long, bytesPerSecond: Long, remainingMs: Long) -> Unit,
  ) {
    // Create temp file if it doesn't exist.
    if (!fileManager.fileExistsAtPath(tmpPath)) {
      fileManager.createFileAtPath(tmpPath, contents = null, attributes = null)
    }

    httpClient.prepareGet(url) {
      if (accessToken != null) {
        header("Authorization", "Bearer $accessToken")
      }
      if (existingBytes > 0) {
        header("Range", "bytes=$existingBytes-")
        header("Accept-Encoding", "identity")
      }
    }.execute { response ->
      val status = response.status
      if (status != HttpStatusCode.OK && status != HttpStatusCode.PartialContent) {
        throw Exception("HTTP error ${status.value}: ${status.description}")
      }

      val channel = response.bodyAsChannel()

      // Open file handle for writing (append mode for resume).
      val fileHandle = NSFileHandle.fileHandleForWritingAtPath(tmpPath)
        ?: throw Exception("Cannot open file for writing: $tmpPath")
      if (existingBytes > 0) {
        fileHandle.seekToEndOfFile()
      }

      try {
        val buffer = ByteArray(BUFFER_SIZE)
        var downloadedThisFile = existingBytes
        var lastProgressTime = currentTimeMs()

        // Rolling average for speed calculation (5 samples).
        val bytesBuffer = LongArray(5)
        val timeBuffer = LongArray(5)
        var sampleIndex = 0
        var sampleCount = 0
        var intervalBytes = 0L
        var intervalStart = currentTimeMs()

        // Report initial progress.
        onProgress(previouslyDownloaded, 0L, 0L)

        while (!channel.isClosedForRead) {
          val bytesRead = channel.readAvailable(buffer)
          if (bytesRead <= 0) continue

          // Write to file.
          buffer.usePinned { pinned ->
            val nsData = NSData.create(
              bytes = pinned.addressOf(0),
              length = bytesRead.toULong(),
            )
            fileHandle.writeData(nsData)
          }

          downloadedThisFile += bytesRead
          intervalBytes += bytesRead

          // Report progress every PROGRESS_UPDATE_INTERVAL_MS.
          val now = currentTimeMs()
          if (now - lastProgressTime >= PROGRESS_UPDATE_INTERVAL_MS) {
            val elapsed = now - intervalStart
            if (elapsed > 0) {
              bytesBuffer[sampleIndex] = intervalBytes
              timeBuffer[sampleIndex] = elapsed
              sampleIndex = (sampleIndex + 1) % 5
              sampleCount = minOf(sampleCount + 1, 5)

              val totalSampleBytes = bytesBuffer.take(sampleCount).sum()
              val totalSampleTime = timeBuffer.take(sampleCount).sum()
              val bytesPerMs = if (totalSampleTime > 0) totalSampleBytes / totalSampleTime else 0
              val bytesPerSecond = bytesPerMs * 1000

              val totalDownloaded = previouslyDownloaded - existingBytes + downloadedThisFile
              val remaining = totalBytesAllFiles - totalDownloaded
              val remainingMs = if (bytesPerMs > 0) remaining / bytesPerMs else 0

              onProgress(totalDownloaded, bytesPerSecond, remainingMs)
            }

            intervalBytes = 0
            intervalStart = now
            lastProgressTime = now
          }
        }
      } finally {
        fileHandle.closeFile()
      }
    }

    // Rename temp file to final path.
    if (fileManager.fileExistsAtPath(finalPath)) {
      fileManager.removeItemAtPath(finalPath, error = null)
    }
    val renamed = fileManager.moveItemAtPath(tmpPath, toPath = finalPath, error = null)
    if (!renamed) {
      throw Exception("Failed to rename downloaded file from $tmpPath to $finalPath")
    }

    Napier.d(tag = TAG) { "Download complete: $finalPath" }
  }

  override fun cancelDownloadModel(model: Model) {
    activeJobs[model.name]?.cancel()
    activeJobs.remove(model.name)
  }

  override fun cancelAll(onComplete: () -> Unit) {
    activeJobs.values.forEach { it.cancel() }
    activeJobs.clear()
    onComplete()
  }
}

/** Returns the current time in milliseconds using platform clock. */
private fun currentTimeMs(): Long {
  return (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()
}
