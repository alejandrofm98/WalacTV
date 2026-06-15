import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use(::load)
}

sqldelight {
    databases {
        create("ContentDatabase") {
            packageName.set("com.example.walactv.shared.local")
        }
    }
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    android {
        namespace = "com.example.walactv.shared"
        compileSdk = 36
        minSdk = 24
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)

            implementation(libs.serialization.json)

            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            implementation(libs.coil.compose.kmp)
            implementation(libs.coil.network.ktor)

            implementation(libs.multiplatform.settings)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            implementation("com.russhwolf:multiplatform-settings-test:1.3.0")
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.kotlinx.coroutines.android)
            implementation("androidx.sqlite:sqlite-framework:2.4.0")
            implementation(libs.androidx.security.crypto)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(libs.ktor.client.java)
            implementation(libs.sqldelight.sqlite.driver)
            implementation("org.jetbrains.skiko:skiko:0.144.6")
            implementation("uk.co.caprica:vlcj:4.9.0")
        }
    }
}
