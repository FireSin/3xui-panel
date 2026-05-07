package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.crypto.BackupError
import com.firesin.xuipanel.core.data.model.Panel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BackupRepositoryImplTest {

    private val panelRepository: PanelRepository = mockk()
    private val repo = BackupRepositoryImpl(panelRepository)

    private fun makePanel(id: String = "p1", active: Boolean = true) = Panel(
        id = id,
        name = "Test Panel",
        baseUrl = "https://panel.example.com",
        login = "admin",
        password = "secret",
        tlsMode = TlsMode.SYSTEM,
        pinnedSpkiSha256 = null,
        pinnedAt = null,
        isActive = active,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastLoginAt = null,
    )

    @Test
    fun `round-trip export then import restores panels`() = runTest {
        val panels = listOf(makePanel("id1"), makePanel("id2", active = false))
        coEvery { panelRepository.observeAll() } returns flowOf(panels)
        coEvery { panelRepository.replaceAll(any()) } answers {
            Result.Success(firstArg<List<Panel>>().size)
        }

        val exportResult = repo.exportPanels("passphrase1".toCharArray())
        assertTrue(exportResult is Result.Success)
        val envelopeJson = (exportResult as Result.Success).data

        val importResult = repo.importPanels(envelopeJson, "passphrase1".toCharArray())
        assertTrue(importResult is Result.Success)
        assertEquals(2, (importResult as Result.Success).data)
    }

    @Test
    fun `import with wrong passphrase returns WrongPassphrase`() = runTest {
        val panels = listOf(makePanel())
        coEvery { panelRepository.observeAll() } returns flowOf(panels)

        val exportResult = repo.exportPanels("correct-pass".toCharArray())
        val envelopeJson = (exportResult as Result.Success).data

        val importResult = repo.importPanels(envelopeJson, "wrong-pass".toCharArray())
        assertEquals(Result.Failure(BackupError.WrongPassphrase), importResult)
    }

    @Test
    fun `import malformed json returns MalformedBundle`() = runTest {
        val importResult = repo.importPanels("{not valid json", "anypass".toCharArray())
        assertEquals(Result.Failure(BackupError.MalformedBundle), importResult)
    }

    @Test
    fun `import with kdfIterations too low returns MalformedBundle`() = runTest {
        val badEnvelope = """
            {
              "version": 1,
              "createdAt": "2026-01-01T00:00:00Z",
              "kdf": "PBKDF2-HMAC-SHA256",
              "kdfIterations": 1,
              "salt": "AAAAAAAAAAAAAAAAAAAAAA==",
              "nonce": "AAAAAAAAAAAAAAAA",
              "ciphertext": "AA=="
            }
        """.trimIndent()

        val importResult = repo.importPanels(badEnvelope, "anypass".toCharArray())
        assertEquals(Result.Failure(BackupError.MalformedBundle), importResult)
    }

    @Test
    fun `import with unsupported kdf algorithm returns MalformedBundle`() = runTest {
        val badEnvelope = """
            {
              "version": 1,
              "createdAt": "2026-01-01T00:00:00Z",
              "kdf": "PBKDF2-HMAC-MD5",
              "kdfIterations": 100000,
              "salt": "AAAAAAAAAAAAAAAAAAAAAA==",
              "nonce": "AAAAAAAAAAAAAAAA",
              "ciphertext": "AA=="
            }
        """.trimIndent()

        val importResult = repo.importPanels(badEnvelope, "anypass".toCharArray())
        assertEquals(Result.Failure(BackupError.MalformedBundle), importResult)
    }

    @Test
    fun `import with kdfIterations too high returns MalformedBundle`() = runTest {
        val badEnvelope = """
            {
              "version": 1,
              "createdAt": "2026-01-01T00:00:00Z",
              "kdf": "PBKDF2-HMAC-SHA256",
              "kdfIterations": 100000000,
              "salt": "AAAAAAAAAAAAAAAAAAAAAA==",
              "nonce": "AAAAAAAAAAAAAAAA",
              "ciphertext": "AA=="
            }
        """.trimIndent()

        val importResult = repo.importPanels(badEnvelope, "anypass".toCharArray())
        assertEquals(Result.Failure(BackupError.MalformedBundle), importResult)
    }

    @Test
    fun `import unsupported version returns Unexpected`() = runTest {
        // Manually craft an envelope with version = 999
        val badEnvelope = """
            {
              "version": 999,
              "createdAt": "2026-01-01T00:00:00Z",
              "kdf": "PBKDF2-HMAC-SHA256",
              "kdfIterations": 100000,
              "salt": "AAAAAAAAAAAAAAAAAAAAAA==",
              "nonce": "AAAAAAAAAAAAAAAA",
              "ciphertext": "AA=="
            }
        """.trimIndent()

        val importResult = repo.importPanels(badEnvelope, "anypass".toCharArray())
        assertTrue(importResult is Result.Failure)
        val error = (importResult as Result.Failure).error
        assertTrue(error is BackupError.Unexpected)
        val msg = (error as BackupError.Unexpected).cause.message ?: ""
        assertTrue(msg.contains("999"), "Expected version number in message, got: $msg")
    }
}
