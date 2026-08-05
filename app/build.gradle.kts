import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val iptvBaseUrl = localProperties.getProperty("walactv.iptvBaseUrl", "https://example.invalid")
val appUpdateUrl = localProperties.getProperty("walactv.updateUrl", "")
val appVersionName = "1.40"
val appVersionCode = appVersionName.split(".").let { parts ->
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    major * 100 + minor
}
val releaseStoreFile = localProperties.getProperty("walactv.release.storeFile", "")
val releaseStorePassword = localProperties.getProperty("walactv.release.storePassword", "")
val releaseKeyAlias = localProperties.getProperty("walactv.release.keyAlias", "")
val releaseKeyPassword = localProperties.getProperty("walactv.release.keyPassword", "")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isNotBlank() }
val requiresReleaseSigning = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true)
}

if (requiresReleaseSigning && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is required. Configure walactv.release.storeFile, walactv.release.storePassword, walactv.release.keyAlias and walactv.release.keyPassword in local.properties.",
    )
}

android {
    namespace = "com.example.walactv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.walactv"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "IPTV_BASE_URL", "\"$iptvBaseUrl\"")
        buildConfigField("String", "APP_UPDATE_URL", "\"$appUpdateUrl\"")

    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.leanback)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    // Glide para cargar imágenes de logos
    implementation(libs.glide)

    // Coil para imágenes en Compose
    implementation(libs.coil.compose)

    // OkHttp para actualizaciones
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Retrofit + Gson para API
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // Hilt/Dagger DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.security.crypto)
}
