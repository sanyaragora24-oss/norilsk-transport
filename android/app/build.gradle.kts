import java.util.Properties
import java.io.FileInputStream
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.gradle.api.GradleException

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  kotlin("plugin.serialization") version "2.2.10"
  id("com.google.gms.google-services")
  id("com.google.firebase.crashlytics")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
  FileInputStream(localPropertiesFile).use { localProperties.load(it) }
}

fun configuredProperty(name: String): String =
  providers.gradleProperty(name).orNull ?: localProperties.getProperty(name) ?: ""

fun String.asBuildConfigString(): String =
  "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val openWeatherKey = configuredProperty("OPEN_WEATHER_KEY")
val yandexApiKey = configuredProperty("YANDEX_MAPKIT_API_KEY")
// VEHICLE_FEED_URL is a generic, explicitly configured HTTPS CSV endpoint.
// Keep the old property only as a migration fallback; Bustime no longer serves Norilsk.
val vehicleFeedUrl = configuredProperty("VEHICLE_FEED_URL")
  .ifBlank { configuredProperty("BUSTIME_FEED_URL") }

if (gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true) ||
      it.substringAfterLast(':') in setOf("assemble", "build", "bundle")
  } &&
  yandexApiKey.isBlank()
) {
  throw GradleException("YANDEX_MAPKIT_API_KEY must be configured before building a release with Yandex MapKit.")
}

android {
  namespace = "com.example"
  compileSdk = 37

  defaultConfig {
    applicationId = "ru.norilsk.transit"
    minSdk = 26
    targetSdk = 36
    versionCode = 12
    versionName = "1.2.10"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "OPEN_WEATHER_KEY", openWeatherKey.asBuildConfigString())
    buildConfigField("String", "YANDEX_MAPKIT_API_KEY", yandexApiKey.asBuildConfigString())
    buildConfigField("String", "VEHICLE_FEED_URL", vehicleFeedUrl.asBuildConfigString())
    
    manifestPlaceholders["YANDEX_MAPKIT_API_KEY"] = yandexApiKey
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
      // Production: the R8 mapping must reach Crashlytics, otherwise every stack
      // trace arrives obfuscated and is unusable.
      configure<CrashlyticsExtension> {
        mappingFileUploadEnabled = true
      }
    }
    debug {
      // Use default debug signing
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  androidResources {
    // Keep local recovery copies out of APK/AAB assets.
    ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:!*.bak*"
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        // The test worker connects back to the Gradle daemon over localhost, which
        // on this machine resolves to ::1 first while the daemon listens on IPv4
        // only. Without this the executor dies with
        // "BindException: Cannot assign requested address" before any test runs.
        it.jvmArgs("-Djava.net.preferIPv4Stack=true")
      }
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.yandex.mapkit)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation(libs.retrofit)
  implementation(libs.converter.gson)
  implementation(libs.logging.interceptor)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  
  // Firebase
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  
  // Location & Permissions
  implementation(libs.play.services.location)
  implementation(libs.accompanist.permissions)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

val buildDirFile = layout.buildDirectory.asFile.get()
val rootDirFile = rootProject.rootDir

tasks.register("exportDebugApkToRoot") {
  dependsOn("assembleDebug")
  val apkFile = File(buildDirFile, "outputs/apk/debug/app-debug.apk")
  val targetFile = File(rootDirFile, "_app-debug.apk")
  doLast {
    if (apkFile.exists()) {
      apkFile.copyTo(targetFile, overwrite = true)
      println("Successfully copied debug APK to: ${targetFile.absolutePath}")
    } else {
      println("Debug APK not found at: ${apkFile.absolutePath}")
    }
  }
}
