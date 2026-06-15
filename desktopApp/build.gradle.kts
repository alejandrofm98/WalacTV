import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use(::load)
}
val iptvBaseUrl = localProperties.getProperty("walactv.iptvBaseUrl", "http://localhost:3010")

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(project(":composeApp"))
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.koin.core)
            implementation(libs.multiplatform.settings)
        }
    }
}

val generatedDir = file("${layout.buildDirectory.get()}/generated/kotlin")

tasks.register("generateBuildConfig") {
    outputs.dir(generatedDir)
    doLast {
        val pkgDir = file("$generatedDir/com/example/walactv/desktop")
        pkgDir.mkdirs()
        file("$pkgDir/BuildConfig.kt").writeText("""
            package com.example.walactv.desktop
            object BuildConfig {
                const val IPTV_BASE_URL = "$iptvBaseUrl"
            }
        """.trimIndent())
    }
}

kotlin.sourceSets.getByName("desktopMain").apply {
    kotlin.srcDir(tasks.named("generateBuildConfig").map { generatedDir })
}

compose.desktop {
    application {
        mainClass = "com.example.walactv.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "WalacTV"
            packageVersion = "1.21.0"
            description = "WalacTV - IPTV Player"

            javaHome = System.getenv("JAVA_HOME")
                ?: "/usr/lib/jvm/java-17-openjdk-amd64"

            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
            windows {
                menuGroup = "WalacTV"
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }

        run {
            jvmArgs("-Diptv.base.url=$iptvBaseUrl")
        }
    }
}
