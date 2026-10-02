import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val googleWebClientId = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}.getProperty("GOOGLE_WEB_CLIENT_ID").orEmpty()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "workout.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "workout.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

val androidSdkDir = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}.getProperty("sdk.dir")

tasks.register("installOnPhone") {
    group = "install"
    description = "Install the debug app on each connected phone. Emulators are skipped."
    notCompatibleWithConfigurationCache("Reads connected devices at execution time.")
    doLast {
        val sdk = androidSdkDir
        if (sdk.isNullOrBlank()) {
            logger.lifecycle("No Android SDK in local.properties. Skipped phone install.")
            return@doLast
        }
        val adb = File(sdk, "platform-tools/adb.exe").takeIf { it.exists() }
            ?: File(sdk, "platform-tools/adb")
        if (!adb.exists()) {
            logger.lifecycle("adb was not found. Skipped phone install.")
            return@doLast
        }
        val listed = ProcessBuilder(adb.absolutePath, "devices")
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText()
        val phones = listed.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.endsWith("device") && !line.startsWith("emulator-") && !line.startsWith("List of")
            }
            .map { it.substringBefore('\t').substringBefore(' ') }
            .filter { it.isNotEmpty() }
            .toList()
        if (phones.isEmpty()) {
            logger.lifecycle("No phone connected. Plug it in with USB debugging and the next build will install Workout.")
            return@doLast
        }
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        if (!apk.exists()) {
            throw GradleException("Debug APK is missing: ${apk.absolutePath}")
        }
        phones.forEach { serial ->
            logger.lifecycle("Installing Workout on $serial")
            val install = ProcessBuilder(
                adb.absolutePath, "-s", serial, "install", "-r", "-t", apk.absolutePath,
            ).redirectErrorStream(true).start()
            val output = install.inputStream.bufferedReader().readText()
            val code = install.waitFor()
            if (output.isNotBlank()) logger.lifecycle(output.trim())
            if (code != 0) {
                throw GradleException("Could not install Workout on $serial.")
            }
        }
    }
}

afterEvaluate {
    tasks.named("assembleDebug").configure {
        finalizedBy("installOnPhone")
    }
}
