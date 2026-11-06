package com.kafkasl.phonewhisper

import org.junit.Assert.*
import org.junit.Test

class UpdaterTest {
    @Test fun `compares versions number by number`() {
        assertTrue(Updater.isNewer("0.6.7", "0.6.6"))
        assertTrue(Updater.isNewer("0.6.10", "0.6.9"))
        assertTrue(Updater.isNewer("1.0", "0.9.9"))
        assertFalse(Updater.isNewer("0.6.6", "0.6.6"))
        assertFalse(Updater.isNewer("0.6.5", "0.6.6"))
    }

    @Test fun `reads the version and APK link from a GitHub release`() {
        val json = """{"tag_name":"v0.6.7","assets":[
            {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":1},
            {"name":"WordForWord.apk","browser_download_url":"https://x/WordForWord.apk","size":2857402}]}"""
        assertEquals(Updater.Release("0.6.7", "https://x/WordForWord.apk", 2857402), Updater.parseRelease(json, "WordForWord.apk"))
    }

    @Test fun `ignores a release without the APK`() {
        assertNull(Updater.parseRelease("""{"tag_name":"v0.6.7","assets":[]}"""))
    }
}
