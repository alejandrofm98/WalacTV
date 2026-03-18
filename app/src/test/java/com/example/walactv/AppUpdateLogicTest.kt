package com.example.walactv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateLogicTest {

    @Test
    fun `parses valid remote update payload`() {
        val info = parseAppUpdateInfo(
            """
            {
              "latestVersionName": "1.1.0",
              "latestVersionCode": 5,
              "minSupportedCode": 4,
              "apkUrl": "https://example.com/WalacTV-1.1.0.apk",
              "changelog": "Mejoras"
            }
            """.trimIndent(),
            fetchedAtMillis = 123L,
        )

        requireNotNull(info)
        assertEquals("1.1.0", info.latestVersionName)
        assertEquals(5, info.latestVersionCode)
        assertEquals(4, info.minSupportedCode)
        assertEquals(123L, info.fetchedAtMillis)
    }

    @Test
    fun `rejects payload with invalid version ranges`() {
        val info = parseAppUpdateInfo(
            """
            {
              "latestVersionName": "1.1.0",
              "latestVersionCode": 4,
              "minSupportedCode": 5,
              "apkUrl": "https://example.com/WalacTV-1.1.0.apk"
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
    fun `returns optional when newer version exists but current is still supported`() {
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

    @Test
    fun `falls back to default github update url when custom url is blank`() {
        assertEquals(
            DEFAULT_APP_UPDATE_URL,
            resolveAppUpdateUrl("   "),
        )
    }
}
