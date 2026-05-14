package com.firesin.xuipanel.core.data.repository

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import com.firesin.xuipanel.core.data.prefs.BackupSchedulePrefs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AutoBackupPreferencesImplTest {

    /**
     * Builds an [AutoBackupPreferencesImpl] backed by a fake DataStore.
     *
     * [updateData] captures the transform lambda; we apply it to the current flow value
     * so both read and write paths are exercised end-to-end.
     */
    private fun build(prefs: Preferences = emptyPreferences()): Pair<MutableStateFlow<Preferences>, AutoBackupPreferencesImpl> {
        val flow = MutableStateFlow(prefs)
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flow
            val transformSlot = slot<suspend (Preferences) -> Preferences>()
            coEvery { updateData(capture(transformSlot)) } coAnswers {
                val next = transformSlot.captured(flow.value)
                flow.value = next
                next
            }
        }
        return flow to AutoBackupPreferencesImpl(dataStore)
    }

    // ── targetUri ─────────────────────────────────────────────────────────────

    @Test
    fun `targetUri emits null when key absent`() = runTest {
        val (_, repo) = build()

        repo.targetUri.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTargetUri stores uri toString string in DataStore`() = runTest {
        val (flow, repo) = build()
        val uriString = "content://com.example/tree/123"
        // Uri.toString() is what the impl stores; mock with relaxed = true and stub toString.
        val uri = mockk<Uri>(relaxed = true)
        io.mockk.every { uri.toString() } returns uriString

        repo.setTargetUri(uri)

        val stored = flow.value[BackupSchedulePrefs.BACKUP_TARGET_URI]
        assertEquals(uriString, stored)
    }

    @Test
    fun `setTargetUri(null) removes the key`() = runTest {
        val initial = mutablePreferencesOf(
            BackupSchedulePrefs.BACKUP_TARGET_URI to "content://old",
        )
        val (_, repo) = build(initial)

        repo.setTargetUri(null)

        repo.targetUri.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── schedule ──────────────────────────────────────────────────────────────

    @Test
    fun `schedule defaults to OFF when key absent`() = runTest {
        val (_, repo) = build()

        repo.schedule.test {
            assertEquals(AutoBackupSchedule.OFF, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `schedule round-trips DAILY`() = runTest {
        val (_, repo) = build()

        repo.setSchedule(AutoBackupSchedule.DAILY)

        repo.schedule.test {
            assertEquals(AutoBackupSchedule.DAILY, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `schedule round-trips WEEKLY`() = runTest {
        val (_, repo) = build()

        repo.setSchedule(AutoBackupSchedule.WEEKLY)

        repo.schedule.test {
            assertEquals(AutoBackupSchedule.WEEKLY, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `schedule falls back to OFF on unknown stored value`() = runTest {
        val initial = mutablePreferencesOf(
            BackupSchedulePrefs.BACKUP_SCHEDULE to "MONTHLY",
        )
        val (_, repo) = build(initial)

        repo.schedule.test {
            assertEquals(AutoBackupSchedule.OFF, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── lastRunAt + lastResult ────────────────────────────────────────────────

    @Test
    fun `lastRunAt and lastResult are null when never set`() = runTest {
        val (_, repo) = build()

        repo.lastRunAt.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repo.lastResult.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLastRun persists timestamp and result`() = runTest {
        val (_, repo) = build()

        repo.setLastRun(1_700_000_000_000L, "Success(3)")

        repo.lastRunAt.test {
            assertEquals(1_700_000_000_000L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repo.lastResult.test {
            assertEquals("Success(3)", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
