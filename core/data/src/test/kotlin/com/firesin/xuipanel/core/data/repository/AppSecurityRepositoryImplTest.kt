package com.firesin.xuipanel.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import com.firesin.xuipanel.core.data.prefs.AppSecurityPrefs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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
}
