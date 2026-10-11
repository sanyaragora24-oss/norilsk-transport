package com.example.data.local

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Чистый Application: тесту не нужны Firebase и MapKit из NorilskApp.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class SafePreferenceStoresTest {

    @Test
    fun corruptFileIsBackedUpBeforeBeingReplaced() = runBlocking {
        val dir = Files.createTempDirectory("prefs").toFile()
        val file = File(dir, "favorites.preferences_pb")
        val garbage = byteArrayOf(0x7F, 0x01, 0x02, 0x03, 0x55, 0x66)
        file.writeBytes(garbage)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = createSafePreferencesStore(scope) { file }

            val prefs = store.safeData().first()

            assertTrue("corrupt store reads as empty instead of crashing", prefs.asMap().isEmpty())
            val backups = dir.listFiles { f -> f.name.startsWith("favorites.preferences_pb.corrupt-") }.orEmpty()
            assertEquals(1, backups.size)
            assertArrayEquals("original bytes are preserved", garbage, backups.single().readBytes())
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun unreadableJsonListIsPreservedInBackupKeyBeforeOverwrite() {
        val key = stringPreferencesKey("groups_json")
        val prefs = mutablePreferencesOf(key to "{not json")

        val decoded = prefs.decodeListPreservingUnreadable(key) { error("broken") as List<String> }

        assertTrue(decoded.isEmpty())
        assertEquals("{not json", prefs[stringPreferencesKey("groups_json_unreadable_backup")])
    }

    @Test
    fun firstBackupIsNotOverwrittenByLaterFailures() {
        val key = stringPreferencesKey("saved_journeys_json")
        val backupKey = stringPreferencesKey("saved_journeys_json_unreadable_backup")
        val prefs = mutablePreferencesOf(key to "second broken", backupKey to "original user data")

        prefs.decodeListPreservingUnreadable(key) { error("broken") as List<String> }

        assertEquals("original user data", prefs[backupKey])
    }
}
