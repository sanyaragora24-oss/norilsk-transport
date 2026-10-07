// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  id("com.google.gms.google-services") version "4.4.4" apply false
  id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

val rootDirFile = rootProject.rootDir

tasks.register("cleanStableApk") {
    val stableFile = File(rootDirFile, "000_DOWNLOAD_THIS_STABLE_APP.apk")
    doLast {
        if (stableFile.exists()) {
            stableFile.delete()
            println("Successfully deleted stable APK: ${stableFile.absolutePath}")
        }
    }
}

tasks.register<org.gradle.api.tasks.bundling.Zip>("zipProject") {
    archiveFileName.set("PROJECT_DOWNLOAD.zip")
    destinationDirectory.set(rootDirFile)
    
    from(rootDirFile) {
        exclude(".gradle")
        exclude("**/build")
        exclude(".build-outputs")
        exclude("PROJECT_DOWNLOAD.zip")
    }
}


