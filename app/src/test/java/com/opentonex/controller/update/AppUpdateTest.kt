package com.opentonex.controller.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val body = """
        # TUSB v1.0.9

        ## Português Brasil

        - Novidade em português.

        ## English US

        - New in English.

        ## Español

        - Novedad en español.

        ## Build

        - APK: `TUSB-v1.0.9.apk`
    """.trimIndent()

    private fun release(
        tag: String = "v1.0.9",
        htmlUrl: String = "https://github.com/acf1210/TUSB/releases/tag/v1.0.9",
        assets: String = """[{"browser_download_url":"https://github.com/acf1210/TUSB/releases/download/v1.0.9/VIRUSTOTAL.md"},
            {"browser_download_url":"https://github.com/acf1210/TUSB/releases/download/v1.0.9/TUSB-v1.0.9.apk"}]"""
    ): String = """{"tag_name":"$tag","html_url":"$htmlUrl","body":${org.json.JSONObject.quote(body)},"assets":$assets}"""

    @Test
    fun `newer versions compare part by part`() {
        assertTrue(AppVersion.isNewer("1.0.9", "1.0.8"))
        assertTrue(AppVersion.isNewer("v1.1", "1.0.8"))
        assertTrue(AppVersion.isNewer("1.0.10", "1.0.9"))
        assertFalse(AppVersion.isNewer("1.0.8", "1.0.8"))
        assertFalse(AppVersion.isNewer("1.0.7", "1.0.8"))
        assertFalse(AppVersion.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun `non numeric tags are never offered`() {
        assertFalse(AppVersion.isNewer("nightly", "1.0.8"))
        assertNull(AppVersion.parts("v1.x"))
    }

    @Test
    fun `parse picks the apk asset and the notes in the device language`() {
        val update = ReleaseParser.parse(release(), language = "pt")
        assertEquals("1.0.9", update.version)
        assertEquals("https://github.com/acf1210/TUSB/releases/download/v1.0.9/TUSB-v1.0.9.apk", update.apkUrl)
        assertEquals("- Novidade em português.", update.notes)
        assertEquals("- Novedad en español.", ReleaseParser.parse(release(), "es").notes)
        assertEquals("- New in English.", ReleaseParser.parse(release(), "de").notes)
    }

    @Test
    fun `apk links outside the TUSB repository are ignored`() {
        val update = ReleaseParser.parse(
            release(assets = """[{"browser_download_url":"https://evil.example/TUSB-v1.0.9.apk"}]"""),
            language = "en"
        )
        assertNull(update.apkUrl)
        assertEquals("https://github.com/acf1210/TUSB/releases/tag/v1.0.9", update.releaseUrl)
    }

    @Test(expected = ReleaseParseException::class)
    fun `release pages outside the TUSB repository are rejected`() {
        ReleaseParser.parse(release(htmlUrl = "https://github.com/someone/else/releases/tag/v9"), "en")
    }

    @Test(expected = ReleaseParseException::class)
    fun `non json responses are rejected`() {
        ReleaseParser.parse("<html>rate limited</html>", "en")
    }

    @Test
    fun `checker queries the latest release endpoint`() = runTest {
        var requested: String? = null
        val update = UpdateChecker { url -> requested = url; release() }.latest("en")
        assertEquals(UpdateChecker.LATEST_RELEASE_API, requested)
        assertEquals("1.0.9", update.version)
    }

    @Test
    fun `automatic check runs at most every twelve hours`() {
        val now = 100L * AUTO_CHECK_INTERVAL_MS
        assertTrue(isAutoCheckDue(lastCheckAt = 0L, now = now))
        assertFalse(isAutoCheckDue(lastCheckAt = now - 1000, now = now))
        assertTrue(isAutoCheckDue(lastCheckAt = now - AUTO_CHECK_INTERVAL_MS, now = now))
        // Relogio voltou no tempo: checa em vez de ficar travado ate la.
        assertTrue(isAutoCheckDue(lastCheckAt = now + 1000, now = now))
    }
}
