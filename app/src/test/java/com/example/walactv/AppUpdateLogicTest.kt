package com.example.walactv

import com.example.walactv.data.model.AppUpdateAvailability
import com.example.walactv.data.model.AppUpdateInfo
import com.example.walactv.data.model.evaluateAppUpdate
import com.example.walactv.data.model.InstalledAppVersion
import com.example.walactv.data.model.parseAppUpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateLogicTest {

    @Test
    fun `parses valid GitHub release payload`() {
        val info = parseAppUpdateInfo(
            """
            {
              "tag_name": "v1.1.0",
              "body": "Mejoras",
              "assets": [
                { "browser_download_url": "https://example.com/WalacTV-1.1.0.apk" }
              ]
            }
            """.trimIndent(),
            fetchedAtMillis = 123L,
        )

        requireNotNull(info)
        assertEquals("1.1.0", info.latestVersionName)
        assertEquals(101, info.latestVersionCode)
        assertEquals(101, info.minSupportedCode)
        assertEquals("https://example.com/WalacTV-1.1.0.apk", info.apkUrl)
        assertEquals("Mejoras", info.changelog)
        assertEquals(123L, info.fetchedAtMillis)
    }

    @Test
    fun `rejects payload without tag name or apk`() {
        val info = parseAppUpdateInfo(
            """
            {
              "body": "Mejoras",
              "assets": [
                { "browser_download_url": "https://example.com/WalacTV-1.1.0.zip" }
              ]
            }
            """.trimIndent(),
        )

        assertNull(info)
    }

    @Test
    fun `rejects payload with malformed version tag`() {
        val info = parseAppUpdateInfo(
            """
            {
              "tag_name": "v0",
              "assets": [
                { "browser_download_url": "https://example.com/WalacTV-0.apk" }
              ]
            }
            """.trimIndent(),
        )

        assertNull(info)
    }

    @Test
    fun `returns required when installed version is below min supported`() {
        val installed = InstalledAppVersion(versionName = "1.0.0", versionCode = 3)
        val remote = AppUpdateInfo(
            latestVersionName = "1.1.0",
            latestVersionCode = 5,
            minSupportedCode = 4,
            apkUrl = "https://example.com/app.apk",
            changelog = "",
            fetchedAtMillis = 1L,
        )

        assertEquals(AppUpdateAvailability.REQUIRED, evaluateAppUpdate(installed, remote))
    }

    @Test
    fun `returns optional when newer version exists but is above min supported`() {
        val installed = InstalledAppVersion(versionName = "1.0.0", versionCode = 4)
        val remote = AppUpdateInfo(
            latestVersionName = "1.1.0",
            latestVersionCode = 5,
            minSupportedCode = 4,
            apkUrl = "https://example.com/app.apk",
            changelog = "",
            fetchedAtMillis = 1L,
        )

        assertEquals(AppUpdateAvailability.OPTIONAL, evaluateAppUpdate(installed, remote))
    }

    @Test
    fun `parses min supported marker from changelog`() {
        val info = parseAppUpdateInfo(
            """
            {
              "tag_name": "v1.40",
              "body": "Novedades\nMIN_SUPPORTED: 1.20",
              "assets": [
                { "browser_download_url": "https://example.com/WalacTV-1.40.apk" }
              ]
            }
            """.trimIndent(),
            fetchedAtMillis = 1L,
        )

        requireNotNull(info)
        assertEquals(140, info.latestVersionCode)
        assertEquals(120, info.minSupportedCode)
        assertEquals(AppUpdateAvailability.REQUIRED, evaluateAppUpdate(InstalledAppVersion("1.10", 110), info))
        assertEquals(AppUpdateAvailability.OPTIONAL, evaluateAppUpdate(InstalledAppVersion("1.30", 130), info))
    }

    @Test
    fun `returns up to date when installed version matches latest`() {
        val installed = InstalledAppVersion(versionName = "1.1.0", versionCode = 5)
        val remote = AppUpdateInfo(
            latestVersionName = "1.1.0",
            latestVersionCode = 5,
            minSupportedCode = 4,
            apkUrl = "https://example.com/app.apk",
            changelog = "",
            fetchedAtMillis = 1L,
        )

        assertEquals(AppUpdateAvailability.UP_TO_DATE, evaluateAppUpdate(installed, remote))
    }
}
