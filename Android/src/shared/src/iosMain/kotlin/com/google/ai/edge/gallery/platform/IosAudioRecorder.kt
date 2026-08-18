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

package com.google.ai.edge.gallery.platform

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.setActive
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

private const val TAG = "IosAudioRecorder"
private const val SAMPLE_RATE = 16000.0
private const val RECORDING_FILE = "gallery_recording.wav"

/**
 * iOS audio recorder using AVAudioRecorder.
 *
 * Records PCM audio at 16kHz mono (matching the Android app's format)
 * and returns the raw audio bytes.
 */
@OptIn(ExperimentalForeignApi::class)
object IosAudioRecorder {

  private var recorder: AVAudioRecorder? = null
  private var recordingUrl: NSURL? = null

  /**
   * Starts recording audio.
   *
   * @return true if recording started successfully, false otherwise.
   */
  fun startRecording(): Boolean {
    val session = AVAudioSession.sharedInstance()
    try {
      session.setCategory(AVAudioSessionCategoryRecord, error = null)
      session.setActive(true, error = null)
    } catch (e: Exception) {
      Napier.e(tag = TAG) { "Failed to configure audio session: ${e.message}" }
      return false
    }

    val documentsDir = NSFileManager.defaultManager
      .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
      .firstOrNull() as? NSURL ?: return false

    val fileUrl = NSURL(string = "$RECORDING_FILE", relativeToURL = documentsDir)
    recordingUrl = fileUrl

    @Suppress("UNCHECKED_CAST")
    val settings = mapOf<Any?, Any?>(
      AVFormatIDKey to kAudioFormatLinearPCM,
      AVSampleRateKey to SAMPLE_RATE,
      AVNumberOfChannelsKey to 1,
      AVLinearPCMBitDepthKey to 16,
      AVLinearPCMIsFloatKey to false,
      AVEncoderAudioQualityKey to 0, // min quality for PCM
    )

    val audioRecorder = AVAudioRecorder(uRL = fileUrl, settings = settings, error = null)
    if (audioRecorder == null) {
      Napier.e(tag = TAG) { "Failed to create AVAudioRecorder" }
      return false
    }

    recorder = audioRecorder
    audioRecorder.prepareToRecord()
    audioRecorder.record()

    Napier.d(tag = TAG) { "Recording started" }
    return true
  }

  /**
   * Stops recording and returns the recorded audio bytes.
   *
   * @return The WAV file bytes, or null if recording failed.
   */
  fun stopRecording(): ByteArray? {
    recorder?.stop()
    recorder = null

    val session = AVAudioSession.sharedInstance()
    session.setActive(false, error = null)

    val url = recordingUrl ?: return null
    val path = url.path ?: return null

    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) {
      bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
      }
    }

    // Clean up the temp file.
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    recordingUrl = null

    Napier.d(tag = TAG) { "Recording stopped, ${bytes.size} bytes" }
    return bytes
  }

  /** Returns true if currently recording. */
  fun isRecording(): Boolean = recorder?.recording == true

  /** Cancels the current recording without returning data. */
  fun cancelRecording() {
    recorder?.stop()
    recorder = null
    val path = recordingUrl?.path
    if (path != null) {
      NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
    recordingUrl = null
  }
}
