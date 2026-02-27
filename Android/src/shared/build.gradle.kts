plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  androidTarget {
    compilations.all {
      kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += "-Xexpect-actual-classes"
      }
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
      implementation(libs.napier)
      implementation(libs.koin.core)
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.json)
      implementation(libs.richtext)
      implementation(libs.commonmark)
    }

    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines.android)
      implementation(libs.androidx.core.ktx)
      implementation(libs.ktor.client.okhttp)
      implementation(libs.litertlm)
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
