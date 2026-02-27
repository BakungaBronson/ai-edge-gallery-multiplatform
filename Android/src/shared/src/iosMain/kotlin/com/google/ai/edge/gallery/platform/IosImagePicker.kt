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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Launches the iOS photo library picker and returns the selected image as JPEG bytes.
 *
 * Uses [UIImagePickerController] for broad compatibility. Supports single image
 * selection per invocation.
 */
@OptIn(ExperimentalForeignApi::class)
object IosImagePicker {

  private var currentCallback: ((ByteArray?) -> Unit)? = null

  private val delegate = object : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
      picker: UIImagePickerController,
      didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
      picker.dismissViewControllerAnimated(true, completion = null)

      val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
        ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]) as? UIImage

      if (image != null) {
        val jpegData: NSData? = UIImageJPEGRepresentation(image, 0.85)
        if (jpegData != null) {
          val bytes = ByteArray(jpegData.length.toInt())
          bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), jpegData.bytes, jpegData.length)
          }
          currentCallback?.invoke(bytes)
        } else {
          currentCallback?.invoke(null)
        }
      } else {
        currentCallback?.invoke(null)
      }
      currentCallback = null
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
      picker.dismissViewControllerAnimated(true, completion = null)
      currentCallback?.invoke(null)
      currentCallback = null
    }
  }

  /**
   * Launches the photo library picker.
   *
   * @param onImageSelected Called with JPEG bytes of the selected image, or null if cancelled.
   */
  fun pickImage(onImageSelected: (ByteArray?) -> Unit) {
    currentCallback = onImageSelected

    val picker = UIImagePickerController()
    picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
    picker.delegate = delegate

    val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootVC?.presentViewController(picker, animated = true, completion = null)
  }
}

/**
 * Converts a [UIImage] to JPEG [ByteArray].
 */
@OptIn(ExperimentalForeignApi::class)
fun UIImage.toJpegBytes(quality: Double = 0.85): ByteArray? {
  val jpegData = UIImageJPEGRepresentation(this, quality) ?: return null
  val bytes = ByteArray(jpegData.length.toInt())
  bytes.usePinned { pinned ->
    memcpy(pinned.addressOf(0), jpegData.bytes, jpegData.length)
  }
  return bytes
}
