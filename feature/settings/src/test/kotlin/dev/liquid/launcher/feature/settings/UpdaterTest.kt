package dev.liquid.launcher.feature.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdaterTest {
    private fun parse(text: String) = Updater.parse(Json.parseToJsonElement(text).jsonObject)

    @Test
    fun readsVersionBuildAndAsset() {
        val release = parse(
            """{"tag_name":"v0.5.0","html_url":"https://example/r","assets":[
                {"name":"notes.txt","browser_download_url":"https://example/n"},
                {"name":"LiquidLauncher-0.5.0-build31.apk","browser_download_url":"https://example/a.apk","size":3340659}]}"""
        )!!
        assertEquals("0.5.0", release.version)
        assertEquals(31, release.build)
        assertEquals("https://example/a.apk", release.url)
        assertEquals(3340659L, release.size)
    }

    @Test
    fun noApkOrNoBuildNumber() {
        assertNull(parse("""{"tag_name":"v0.5.0","assets":[]}"""))
        assertNull(parse("""{"tag_name":"v0.5.0","assets":[{"name":"LiquidLauncher.apk","browser_download_url":"x"}]}"""))
    }

    @Test
    fun keepsTheReleaseNotes() {
        val release = parse(
            """{"tag_name":"v0.10.0","body":"### Added\n- A changelog.\n","assets":[
                {"name":"LiquidLauncher-0.10.0-build80.apk","browser_download_url":"https://example/a.apk"}]}"""
        )!!
        assertEquals("### Added\n- A changelog.\n", release.notes)
        val noBody = parse("""{"tag_name":"v0.10.0","assets":[{"name":"LiquidLauncher-0.10.0-build80.apk","browser_download_url":"x"}]}""")!!
        assertEquals("", noBody.notes)
    }
}
