package com.firesin.xuipanel.core.sampler

import android.net.Uri
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.AutoBackupPreferences
import com.firesin.xuipanel.core.data.repository.AutoBackupSchedule
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BackupWorkerCoreTest {

    private val panelRepository = mockk<PanelRepository>()
    private val xuiClient = mockk<XuiClient>()
    private val autoBackupPreferences = mockk<AutoBackupPreferences>(relaxUnitFun = true)
    private val safWriter = mockk<BackupSafWriter>()

    private val targetUri: Uri = mockk(relaxed = true)

    private val core = BackupWorkerCore(
        panelRepository = panelRepository,
        xuiClient = xuiClient,
        autoBackupPreferences = autoBackupPreferences,
        safWriter = safWriter,
    )

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private fun panel(
        id: String = "p1",
        name: String = "Panel $id",
        twoFactor: Boolean = false,
    ) = Panel(
        id = id,
        name = name,
        baseUrl = "https://panel.example.com/",
        login = "admin",
        password = "secret",
        tlsMode = TlsMode.SYSTEM,
        pinnedSpkiSha256 = null,
        pinnedAt = null,
        isActive = true,
        createdAt = Instant.EPOCH,
        lastLoginAt = null,
        twoFactorEnabled = twoFactor,
    )

    @BeforeEach
    fun setUp() {
        every { autoBackupPreferences.targetUri } returns flowOf(targetUri)
        every { autoBackupPreferences.schedule } returns flowOf(AutoBackupSchedule.DAILY)
        every { autoBackupPreferences.lastRunAt } returns flowOf(null)
        every { autoBackupPreferences.lastResult } returns flowOf(null)
        every { safWriter.isFolderWritable(targetUri) } returns true
        // By default, no pre-existing files for retention
        every { safWriter.listFiles(any(), any(), any()) } returns emptyList()
        every { safWriter.deleteFile(any(), any()) } returns Unit
    }

    // Helper: make safWriter.writeFile capture and invoke the lambda, returning true
    private fun safWriterSucceeds() {
        coEvery { safWriter.writeFile(any(), any(), any()) } coAnswers {
            val writeFn = thirdArg<suspend (java.io.OutputStream) -> Unit>()
            writeFn(java.io.ByteArrayOutputStream()) // invoke the write lambda
            true
        }
    }

    // ── Happy path: 2 panels → 2 files → Success(2) ───────────────────────────

    @Test
    fun `happy path - 2 panels - writes 2 files - Success(2)`() = runTest {
        val panels = listOf(panel("p1", "Panel One"), panel("p2", "Panel Two"))
        coEvery { panelRepository.observeAll() } returns flowOf(panels)
        safWriterSucceeds()

        // XuiClient succeeds for both panels
        coEvery {
            xuiClient.fetchDbInto(any(), any(), any(), any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val sink = it.invocation.args[4] as suspend (java.io.InputStream) -> Unit
            sink(java.io.ByteArrayInputStream(ByteArray(10)))
            Result.Success(Unit)
        }

        val outcome = core.runOnce()

        assertInstanceOf(BackupWorkerCore.Outcome.Success::class.java, outcome)
        assertEquals(2, (outcome as BackupWorkerCore.Outcome.Success).count)
        // Both files were written
        coVerify(exactly = 2) { safWriter.writeFile(eq(targetUri), any(), any()) }
        // Result persisted
        coVerify(exactly = 1) { autoBackupPreferences.setLastRun(any(), eq("Success(2)")) }
    }

    // ── 1 panel responds 401 → PartialFailure(1, 1) ───────────────────────────

    @Test
    fun `one panel fails with 401 - other succeeds - PartialFailure(1,1)`() = runTest {
        val panels = listOf(panel("p1"), panel("p2"))
        coEvery { panelRepository.observeAll() } returns flowOf(panels)
        safWriterSucceeds()

        // p1 fails (XuiClient returns InvalidCredentials)
        coEvery {
            xuiClient.fetchDbInto(eq("p1"), any(), any(), any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val sink = it.invocation.args[4] as suspend (java.io.InputStream) -> Unit
            sink(java.io.ByteArrayInputStream(ByteArray(0)))
            Result.Failure(DomainError.InvalidCredentials)
        }

        // p2 succeeds
        coEvery {
            xuiClient.fetchDbInto(eq("p2"), any(), any(), any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val sink = it.invocation.args[4] as suspend (java.io.InputStream) -> Unit
            sink(java.io.ByteArrayInputStream(ByteArray(10)))
            Result.Success(Unit)
        }

        val outcome = core.runOnce()

        assertInstanceOf(BackupWorkerCore.Outcome.PartialFailure::class.java, outcome)
        val pf = outcome as BackupWorkerCore.Outcome.PartialFailure
        assertEquals(1, pf.succeeded)
        assertEquals(1, pf.failed)
        coVerify(exactly = 1) {
            autoBackupPreferences.setLastRun(any(), eq("PartialFailure(1, 1)"))
        }
    }

    // ── Retention: 8 pre-existing files → 7 remain after new write ────────────

    @Test
    fun `retention - 8 old files for panel - deletes oldest 2 keeping 7 (6 old + 1 new)`() = runTest {
        val panel = panel("p1", "Panel")
        coEvery { panelRepository.observeAll() } returns flowOf(listOf(panel))
        safWriterSucceeds()

        coEvery {
            xuiClient.fetchDbInto(any(), any(), any(), any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val sink = it.invocation.args[4] as suspend (java.io.InputStream) -> Unit
            sink(java.io.ByteArrayInputStream(ByteArray(10)))
            Result.Success(Unit)
        }

        // 8 pre-existing files (the new one will be #9, then we trim to 7)
        val existingFiles = (1..8).map { i ->
            "Panel_2025010${i}_120000.db"
        }
        every { safWriter.listFiles(targetUri, "Panel_", ".db") } returns existingFiles

        core.runOnce()

        // sorted descending: newest at index 0; drop(7) = 2 oldest deleted
        val deletedNames = existingFiles.sortedDescending().drop(7)
        deletedNames.forEach { name ->
            coVerify(exactly = 1) { safWriter.deleteFile(targetUri, name) }
        }
        // 6 files kept = no extra deletes
        coVerify(exactly = deletedNames.size) { safWriter.deleteFile(any(), any()) }
    }

    // ── No folder selected → Failure ──────────────────────────────────────────

    @Test
    fun `no target folder - returns Failure without touching panels`() = runTest {
        every { autoBackupPreferences.targetUri } returns flowOf(null)

        val outcome = core.runOnce()

        assertInstanceOf(BackupWorkerCore.Outcome.Failure::class.java, outcome)
        coVerify(exactly = 0) { panelRepository.observeAll() }
        coVerify(exactly = 0) { safWriter.writeFile(any(), any(), any()) }
    }

    // ── 2FA panels skipped → counted as failures ──────────────────────────────

    @Test
    fun `panel with 2FA is skipped and counted as failure`() = runTest {
        val panels = listOf(panel("p1", twoFactor = true))
        coEvery { panelRepository.observeAll() } returns flowOf(panels)

        val outcome = core.runOnce()

        assertInstanceOf(BackupWorkerCore.Outcome.Failure::class.java, outcome)
        coVerify(exactly = 0) { xuiClient.fetchDbInto(any(), any(), any(), any(), any()) }
    }
}
