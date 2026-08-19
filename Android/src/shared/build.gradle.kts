import java.net.URL
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
      freeCompilerArgs.add("-Xexpect-actual-classes")
    }
  }

  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64(),
  ).forEach {
    it.binaries.framework {
      baseName = "shared"
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.materialIconsExtended)
      implementation(compose.components.resources)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.datetime)
      implementation(libs.napier)
      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.jetbrains.navigation.compose)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.json)
    }

    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines.android)
      implementation(libs.androidx.core.ktx)
      implementation(libs.androidx.activity.compose)
      implementation(libs.ktor.client.okhttp)
      implementation(libs.litertlm)
      implementation(libs.richtext)
      implementation(libs.commonmark)
      implementation(project.dependencies.platform(libs.firebase.bom))
      implementation(libs.firebase.analytics)
    }

    iosMain.dependencies {
      implementation(libs.ktor.client.darwin)
    }
  }
}

compose.resources {
  publicResClass = true
  packageOfResClass = "com.google.ai.edge.gallery.shared.resources"
  generateResClass = always
}

// -----------------------------------------------------------------------------------------
// Crane: LiteRT-LM v0.16 C-API for Apple platforms (CLiteRTLM.xcframework).
//
// The iOS counterpart of `:app`'s downloadLiteRtLmCApi. Same upstream release, same reason for
// fetching instead of committing: the xcframework is ~88 MB zipped (~130 MB unpacked across the
// ios-arm64 + ios-arm64-simulator slices), which would permanently bloat a public repo's
// history. Downloaded from the pinned release and sha256-verified at build time.
//
// Verified against the actual release asset before pinning:
//   gh release download v0.16.0 -R google-ai-edge/LiteRT-LM -p CLiteRTLM.xcframework.zip
//   shasum -a 256 CLiteRTLM.xcframework.zip  ->  matches cLiteRtLmXcframeworkSha256
//
// The asset ships arm64 slices only (device + simulator); there is no x86_64 simulator slice,
// which is why Swift — not Kotlin/Native cinterop — links it. `iosX64()` stays buildable
// because the Kotlin side never references the library; the guards cross into Swift through the
// IosLlmConversationDelegate seam. See Android/src/iosApp/iosApp/CraneLlmBridge.swift.
val cLiteRtLmVersion = "v0.16.0"
val cLiteRtLmXcframeworkUrl =
  "https://github.com/google-ai-edge/LiteRT-LM/releases/download/$cLiteRtLmVersion/CLiteRTLM.xcframework.zip"
val cLiteRtLmXcframeworkSha256 = "4e0f683da07566ee79c143d2d58d387f77052b0e6a41562c969e5d2728fc9f4b"

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

val downloadCLiteRtLmXcframework by tasks.registering {
  description =
    "Downloads + sha256-verifies the pinned LiteRT-LM $cLiteRtLmVersion Apple C-API release and " +
      "unpacks CLiteRTLM.xcframework for the Xcode build to embed."
  val zipFile = layout.buildDirectory.file("craneNativeLibs/CLiteRTLM.xcframework.zip")
  val outDir = layout.buildDirectory.dir("craneNativeLibs/CLiteRTLM.xcframework")
  // The unpacked framework binaries are the real output; Info.plist is the cheap sentinel that
  // tells Gradle the unpack completed.
  outputs.file(layout.buildDirectory.file("craneNativeLibs/CLiteRTLM.xcframework/Info.plist"))

  doLast {
    val zip = zipFile.get().asFile
    val marker = outDir.get().asFile.resolve("Info.plist")

    if (!(zip.exists() && sha256(zip) == cLiteRtLmXcframeworkSha256)) {
      zip.parentFile.mkdirs()
      logger.lifecycle("Crane: downloading CLiteRTLM.xcframework $cLiteRtLmVersion from $cLiteRtLmXcframeworkUrl")
      URL(cLiteRtLmXcframeworkUrl).openStream().use { input ->
        zip.outputStream().use { output -> input.copyTo(output) }
      }
      val actualSha = sha256(zip)
      check(actualSha == cLiteRtLmXcframeworkSha256) {
        "CLiteRTLM.xcframework.zip sha256 mismatch: expected $cLiteRtLmXcframeworkSha256, got " +
          "$actualSha. Refusing to use a native library that doesn't match the pinned release."
      }
      // Force a re-unpack when the archive changed underneath us.
      outDir.get().asFile.deleteRecursively()
    }

    if (marker.exists()) {
      logger.lifecycle("Crane: CLiteRTLM.xcframework already unpacked, skipping")
      return@doLast
    }

    // `copy { from(zipTree(..)) }` loses the executable bit and the framework symlink layout that
    // codesign needs, so shell out to ditto (the Apple-blessed archive tool, always present on a
    // machine that can build for iOS at all).
    val extractDir = layout.buildDirectory.dir("craneNativeLibs/_extract").get().asFile
    extractDir.deleteRecursively()
    extractDir.mkdirs()
    providers
      .exec {
        commandLine("ditto", "-x", "-k", zip.absolutePath, extractDir.absolutePath)
      }
      .result
      .get()
      .assertNormalExitValue()

    val unpacked = extractDir.resolve("CLiteRTLM.xcframework")
    check(unpacked.isDirectory) {
      "CLiteRTLM.xcframework.zip did not contain CLiteRTLM.xcframework (found: " +
        "${extractDir.list()?.joinToString()})"
    }
    unpacked.renameTo(outDir.get().asFile)
    check(marker.exists()) { "CLiteRTLM.xcframework unpacked without an Info.plist" }
    logger.lifecycle("Crane: CLiteRTLM.xcframework ready at ${outDir.get().asFile}")
  }
}

// The Xcode build reaches the framework through the iosApp preBuild script, which runs
// :shared:embedAndSignAppleFrameworkForXcode. Hang the download off every Apple framework task so
// the xcframework is on disk before Xcode's "Embed Frameworks" phase looks for it.
tasks.matching { it.name.contains("AppleFrameworkForXcode") }.configureEach {
  dependsOn(downloadCLiteRtLmXcframework)
}

android {
  namespace = "com.google.ai.edge.gallery.shared"
  compileSdk = 35

  defaultConfig {
    minSdk = 31
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}
