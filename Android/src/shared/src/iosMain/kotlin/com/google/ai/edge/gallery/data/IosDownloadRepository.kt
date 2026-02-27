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
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionDelegateProtocol
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSUserDomainMask
import platform.Foundation.setValue
import platform.darwin.NSObject

/**
 * iOS implementation of [DownloadRepository] using NSURLSession
 * for downloading model files.
 */
class IosDownloadRepository(
  private val dataStoreRepository: DataStoreRepository,
) : DownloadRepository {

  private val fileManager = NSFileManager.defaultManager
  private val activeTasks = mutableMapOf<String, NSURLSessionDataTask>()

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

    val destPath = model.getPath(basePath = documentsDir)
    val destDir = destPath.substringBeforeLast("/")
    if (!fileManager.fileExistsAtPath(destDir)) {
      fileManager.createDirectoryAtPath(destDir, withIntermediateDirectories = true, attributes = null, error = null)
    }

    // Check for existing partial download.
    var existingBytes: Long = 0
    if (fileManager.fileExistsAtPath(destPath)) {
      val attrs = fileManager.attributesOfItemAtPath(destPath, error = null)
      existingBytes = (attrs?.get("NSFileSize") as? Number)?.toLong() ?: 0
    }

    val nsUrl = NSURL(string = url) ?: run {
      onStatusUpdated(
        model,
        ModelDownloadStatus(
          status = ModelDownloadStatusType.FAILED,
          errorMessage = "Invalid URL: $url",
        ),
      )
      return
    }

    val request = NSMutableURLRequest(URL = nsUrl)
    if (existingBytes > 0) {
      request.setValue("bytes=$existingBytes-", forHTTPHeaderField = "Range")
    }

    onStatusUpdated(
      model,
      ModelDownloadStatus(
        status = ModelDownloadStatusType.IN_PROGRESS,
        totalBytes = model.sizeInBytes,
        receivedBytes = existingBytes,
      ),
    )

    val sessionConfig = NSURLSessionConfiguration.defaultSessionConfiguration
    val session = NSURLSession.sessionWithConfiguration(sessionConfig)
    val dataTask = session.dataTaskWithRequest(request) { data, response, error ->
      if (error != null) {
        Napier.e("Download failed: ${error.localizedDescription}")
        onStatusUpdated(
          model,
          ModelDownloadStatus(
            status = ModelDownloadStatusType.FAILED,
            errorMessage = error.localizedDescription ?: "Unknown error",
          ),
        )
        return@dataTaskWithRequest
      }

      val httpResponse = response as? NSHTTPURLResponse
      if (httpResponse != null && httpResponse.statusCode !in 200..299) {
        onStatusUpdated(
          model,
          ModelDownloadStatus(
            status = ModelDownloadStatusType.FAILED,
            errorMessage = "HTTP ${httpResponse.statusCode}",
          ),
        )
        return@dataTaskWithRequest
      }

      if (data != null) {
        // Write data to file.
        val nsData = data
        val fileUrl = NSURL.fileURLWithPath(destPath)
        nsData.writeToURL(fileUrl, atomically = true)

        onStatusUpdated(
          model,
          ModelDownloadStatus(
            status = ModelDownloadStatusType.SUCCEEDED,
            totalBytes = model.sizeInBytes,
            receivedBytes = model.sizeInBytes,
          ),
        )
      }
    }

    activeTasks[model.name] = dataTask
    dataTask.resume()
  }

  override fun cancelDownloadModel(model: Model) {
    activeTasks[model.name]?.cancel()
    activeTasks.remove(model.name)
  }

  override fun cancelAll(onComplete: () -> Unit) {
    activeTasks.values.forEach { it.cancel() }
    activeTasks.clear()
    onComplete()
  }
}
