package com.firesin.xuipanel.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import com.firesin.xuipanel.core.common.ThemeMode
import com.firesin.xuipanel.core.data.prefs.AppSecurityPrefs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppSecurityRepositoryImplTest {

    // DataStore.data is a property (gets delegated to getData()), so we need to set it up
    // before constructing the repo to avoid MockKException at init time.
    private fun buildRepo(lockEnabled: Boolean? = null): Pair<DataStore<Preferences>, AppSecurityRepositoryImpl> {
        val prefs: Preferences = if (lockEnabled != null)
            mutablePreferencesOf(AppSecurityPrefs.APP_LOCK_ENABLED to lockEnabled)
        else
            emptyPreferences()
        val flow = MutableStateFlow(prefs)
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flow
        }
        return dataStore to AppSecurityRepositoryImpl(dataStore)
    }

    private fun buildRepoWithTheme(themeRaw: String?): Pair<DataStore<Preferences>, AppSecurityRepositoryImpl> {
        val prefs: Preferences = if (themeRaw != null)
            mutablePreferencesOf(AppSecurityPrefs.THEME_MODE to themeRaw)
        else
            emptyPreferences()
        val flow = MutableStateFlow(prefs)
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flow
        }
        return dataStore to AppSecurityRepositoryImpl(dataStore)
    }

    @Test
    fun `isLockEnabled emits false when key absent`() = runTest {
        val (_, repo) = buildRepo(lockEnabled = null)

        repo.isLockEnabled.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLockEnabled emits true when key is true`() = runTest {
        val (_, repo) = buildRepo(lockEnabled = true)

        repo.isLockEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLockEnabled emits false when key is false`() = runTest {
        val (_, repo) = buildRepo(lockEnabled = false)

        repo.isLockEnabled.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLockEnabled true writes key via updateData`() = runTest {
        val (dataStore, repo) = buildRepo()
        val transformSlot = slot<suspend (Preferences) -> Preferences>()
        coEvery { dataStore.updateData(capture(transformSlot)) } coAnswers {
            transformSlot.captured(emptyPreferences())
        }

        repo.setLockEnabled(true)

        val result = transformSlot.captured(emptyPreferences()) as MutablePreferences
        assertTrue(result[AppSecurityPrefs.APP_LOCK_ENABLED] == true)
    }

    @Test
    fun `setLockEnabled false writes key as false`() = runTest {
        val (dataStore, repo) = buildRepo()
        val transformSlot = slot<suspend (Preferences) -> Preferences>()
        coEvery { dataStore.updateData(capture(transformSlot)) } coAnswers {
            transformSlot.captured(emptyPreferences())
        }

        repo.setLockEnabled(false)

        val result = transformSlot.captured(emptyPreferences()) as MutablePreferences
        assertFalse(result[AppSecurityPrefs.APP_LOCK_ENABLED] == true)
    }

    @Test
    fun `themeMode emits SYSTEM when key absent`() = runTest {
        val (_, repo) = buildRepoWithTheme(null)

        repo.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `themeMode emits DARK when key is dark`() = runTest {
        val (_, repo) = buildRepoWithTheme("dark")

        repo.themeMode.test {
            assertEquals(ThemeMode.DARK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `themeMode emits LIGHT when key is light`() = runTest {
        val (_, repo) = buildRepoWithTheme("light")

        repo.themeMode.test {
            assertEquals(ThemeMode.LIGHT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `themeMode emits SYSTEM when key is unknown value`() = runTest {
        val (_, repo) = buildRepoWithTheme("bogus")

        repo.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setThemeMode writes lowercase name via updateData`() = runTest {
        val (dataStore, repo) = buildRepoWithTheme(null)
        val transformSlot = slot<suspend (Preferences) -> Preferences>()
        coEvery { dataStore.updateData(capture(transformSlot)) } coAnswers {
            transformSlot.captured(emptyPreferences())
        }

        repo.setThemeMode(ThemeMode.DARK)

        val result = transformSlot.captured(emptyPreferences()) as MutablePreferences
        assertEquals("dark", result[AppSecurityPrefs.THEME_MODE])
    }

    @Test
    fun `installId emits stored value when present`() = runTest {
        val existing = "11111111-2222-3333-4444-555555555555"
        val prefs: Preferences = mutablePreferencesOf(AppSecurityPrefs.INSTALL_ID to existing)
        val flow = MutableStateFlow(prefs)
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flow
        }
        val repo = AppSecurityRepositoryImpl(dataStore)

        repo.installId.test {
            assertEquals(existing, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `installId generates and persists UUID when key absent`() = runTest {
        val prefs: Preferences = emptyPreferences()
        val flow = MutableStateFlow(prefs)
        val dataStore: DataStore<Preferences> = mockk {
            every { data } returns flow
        }
        val transformSlot = slot<suspend (Preferences) -> Preferences>()
        coEvery { dataStore.updateData(capture(transformSlot)) } coAnswers {
            transformSlot.captured(emptyPreferences())
        }
        val repo = AppSecurityRepositoryImpl(dataStore)

        repo.installId.test {
            val emitted = awaitItem()
            assertTrue(emitted.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        val stored = transformSlot.captured(emptyPreferences()) as MutablePreferences
        assertTrue(stored[AppSecurityPrefs.INSTALL_ID]?.isNotEmpty() == true)
    }
}
