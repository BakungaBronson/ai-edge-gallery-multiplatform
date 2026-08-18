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

import java.net.URL
import java.security.MessageDigest

plugins {
  alias(libs.plugins.android.application)
  // Note: set apply to true to enable google-services (requires google-services.json).
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.oss.licenses)
}

android {
  namespace = "com.google.ai.edge.gallery"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.google.aiedge.gallery"
    minSdk = 31
    targetSdk = 35
    versionCode = 19
    versionName = "1.0.10"

    // Needed for HuggingFace auth workflows.
    // Use the scheme of the "Redirect URLs" in HuggingFace app.
    manifestPlaceholders["appAuthRedirectScheme"] =
        "REPLACE_WITH_YOUR_REDIRECT_SCHEME_IN_HUGGINGFACE_APP"
    manifestPlaceholders["applicationName"] = "com.google.ai.edge.gallery.GalleryApplication"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
    freeCompilerArgs += "-Xcontext-receivers"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }

  sourceSets {
    getByName("main") {
      // libcrane_llm_jni.so (small, ~14 KB) is committed under src/main/jniLibs/arm64-v8a/
      // directly. liblitert-lm.so (~39 MB) is NOT committed (see downloadLiteRtLmCApi below);
      // it lands in this build-generated directory instead.
      jniLibs.srcDir(layout.buildDirectory.dir("craneNativeLibs/jniLibs"))
    }
  }
}

// -----------------------------------------------------------------------------------------
// Crane: LiteRT-LM v0.16 C-API native library (liblitert-lm.so).
//
// This is a ~39 MB prebuilt shared library. Committing it as a plain git blob would bloat a
// public repo's history permanently, so instead it's downloaded from the pinned upstream
// GitHub release and sha256-verified at build time. Both hashes below were verified against
// the actual release asset before pinning:
//   curl -sL <url> | sha256sum   ->  matches liteRtLmCApiAssetSha256
//   unzip -p <zip> lib/android_arm64/liblitert-lm.so | sha256sum  ->  matches liteRtLmSoSha256
val liteRtLmCApiVersion = "v0.16.0"
val liteRtLmCApiUrl =
  "https://github.com/google-ai-edge/LiteRT-LM/releases/download/$liteRtLmCApiVersion/litert_lm_c_api-0.1.0.zip"
val liteRtLmCApiAssetSha256 = "f0f3ae7b5730af783d1f018f7ad9a8de20c25fedf01af4e35fc11d4382246f7d"
val liteRtLmSoSha256 = "e9cbdddb0f1c693c549e1cde40bf90ad8aaa124d15944d0dd18faaf016dd6938"

fun sha256(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
    val buf = ByteArray(1 shl 16)
    while (true) {
      val n = input.read(buf)
      if (n < 0) break
      digest.update(buf, 0, n)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadLiteRtLmCApi by tasks.registering {
  description =
    "Downloads + sha256-verifies the pinned LiteRT-LM $liteRtLmCApiVersion C-API release and " +
      "extracts liblitert-lm.so (android_arm64) into jniLibs."
  val zipFile = layout.buildDirectory.file("craneNativeLibs/litert_lm_c_api-0.1.0.zip")
  val outSo = layout.buildDirectory.file("craneNativeLibs/jniLibs/arm64-v8a/liblitert-lm.so")
  outputs.file(outSo)

  doLast {
    val so = outSo.get().asFile
    if (so.exists() && sha256(so) == liteRtLmSoSha256) {
      logger.lifecycle("Crane: liblitert-lm.so already present and sha256-verified, skipping download")
      return@doLast
    }

    val zip = zipFile.get().asFile
    zip.parentFile.mkdirs()
    logger.lifecycle("Crane: downloading LiteRT-LM C-API $liteRtLmCApiVersion from $liteRtLmCApiUrl")
    URL(liteRtLmCApiUrl).openStream().use { input -> zip.outputStream().use { output -> input.copyTo(output) } }

    val actualZipSha = sha256(zip)
    check(actualZipSha == liteRtLmCApiAssetSha256) {
      "litert_lm_c_api-0.1.0.zip sha256 mismatch: expected $liteRtLmCApiAssetSha256, got " +
        "$actualZipSha. Refusing to use a native library that doesn't match the pinned release."
    }

    so.parentFile.mkdirs()
    copy {
      from(zipTree(zip)) { include("lib/android_arm64/liblitert-lm.so") }
      into(layout.buildDirectory.dir("craneNativeLibs/_extract"))
    }
    layout.buildDirectory
      .file("craneNativeLibs/_extract/lib/android_arm64/liblitert-lm.so")
      .get()
      .asFile
      .copyTo(so, overwrite = true)

    val actualSoSha = sha256(so)
    check(actualSoSha == liteRtLmSoSha256) {
      "liblitert-lm.so sha256 mismatch after extraction: expected $liteRtLmSoSha256, got $actualSoSha."
    }
    logger.lifecycle("Crane: liblitert-lm.so verified (sha256=$actualSoSha)")
  }
}

tasks.named("preBuild") { dependsOn(downloadLiteRtLmCApi) }

// Belt-and-suspenders: the merge*JniLibFolders tasks are what actually read jniLibs.srcDirs,
// so make sure they explicitly wait on the download too (not just preBuild ordering).
tasks.matching { it.name.contains("JniLibFolders") }.configureEach { dependsOn(downloadLiteRtLmCApi) }

dependencies {
  implementation(project(":shared"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.compose.navigation)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlin.reflect)
  implementation(libs.material.icon.extended)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.webkit)
  implementation(libs.litertlm)
  implementation(libs.commonmark)
  implementation(libs.richtext)
  implementation(libs.tflite)
  implementation(libs.tflite.gpu)
  implementation(libs.tflite.support)
  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)
  implementation(libs.openid.appauth)
  implementation(libs.androidx.splashscreen)
  implementation(libs.koin.android)
  implementation(libs.koin.compose)
  implementation(libs.koin.compose.viewmodel)
  implementation(libs.play.services.oss.licenses)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.analytics)
  implementation(libs.androidx.exifinterface)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}
