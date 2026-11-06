package com.kafkasl.phonewhisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackupTest {
    @Test fun everyTypeSurvivesExportAndImport() {
        val values = mapOf(
            "api_key" to "sk-or-v1-test",
            "verbatim" to true,
            "shortcut_hold_ms" to 500L,
            "acc_setup_phase" to 2,
            "some_float" to 1.5f,
            "known_placeholders" to setOf("a|Message", "b|Search"),
        )
        assertEquals(values, SettingsBackup.parse(SettingsBackup.export(values)))
    }

    @Test fun otherFilesAreRejected() {
        assertNull(SettingsBackup.parse("""{"settings":{}}"""))
        assertNull(SettingsBackup.parse("not json"))
    }
}
