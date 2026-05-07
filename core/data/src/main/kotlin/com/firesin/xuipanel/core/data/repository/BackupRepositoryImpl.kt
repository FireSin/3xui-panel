package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.crypto.BackupCrypto
import com.firesin.xuipanel.core.crypto.BackupError
import com.firesin.xuipanel.core.crypto.EncryptedBundle
import com.firesin.xuipanel.core.data.model.Panel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Arrays
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private const val SUPPORTED_VERSION = 1

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val panelRepository: PanelRepository,
) : BackupRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exportPanels(passphrase: CharArray): Result<String, BackupError> =
        runCatching {
            val panels = panelRepository.observeAll().first()
            val dtos = panels.map { it.toExportDto() }
            val plaintextJson = json.encodeToString(dtos)
            val plainBytes = plaintextJson.toByteArray(Charsets.UTF_8)

            val bundle = BackupCrypto.encrypt(plainBytes, passphrase)
            // passphrase zeroed inside encrypt

            val envelope = BackupEnvelope(
                version = SUPPORTED_VERSION,
                createdAt = Instant.now().toString(),
                kdfIterations = bundle.kdfIterations,
                salt = Base64.getEncoder().encodeToString(bundle.salt),
                nonce = Base64.getEncoder().encodeToString(bundle.nonce),
                ciphertext = Base64.getEncoder().encodeToString(bundle.ciphertext),
            )
            json.encodeToString(envelope)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(BackupError.Unexpected(it)) },
        )

    override suspend fun importPanels(envelopeJson: String, passphrase: CharArray): Result<Int, BackupError> {
        val envelope = runCatching { json.decodeFromString<BackupEnvelope>(envelopeJson) }
            .getOrElse {
                Arrays.fill(passphrase, ' ')
                return Result.Failure(BackupError.MalformedBundle)
            }

        if (envelope.version != SUPPORTED_VERSION) {
            Arrays.fill(passphrase, ' ')
            return Result.Failure(
                BackupError.Unexpected(
                    IllegalArgumentException("Unsupported backup version: ${envelope.version}"),
                ),
            )
        }
        if (envelope.kdf != "PBKDF2-HMAC-SHA256") {
            Arrays.fill(passphrase, ' ')
            return Result.Failure(BackupError.MalformedBundle)
        }
        if (envelope.kdfIterations !in 50_000..2_000_000) {
            Arrays.fill(passphrase, ' ')
            return Result.Failure(BackupError.MalformedBundle)
        }

        val bundle = runCatching {
            EncryptedBundle(
                salt = Base64.getDecoder().decode(envelope.salt),
                nonce = Base64.getDecoder().decode(envelope.nonce),
                ciphertext = Base64.getDecoder().decode(envelope.ciphertext),
                kdfIterations = envelope.kdfIterations,
            )
        }.getOrElse {
            Arrays.fill(passphrase, ' ')
            return Result.Failure(BackupError.MalformedBundle)
        }

        val decryptResult = BackupCrypto.decrypt(bundle, passphrase)
        // passphrase zeroed inside decrypt
        val plainBytes = when (decryptResult) {
            is Result.Success -> decryptResult.data
            is Result.Failure -> return decryptResult
        }

        val dtos = runCatching {
            json.decodeFromString<List<PanelExportDto>>(plainBytes.toString(Charsets.UTF_8))
        }.getOrElse {
            return Result.Failure(BackupError.MalformedBundle)
        }

        val panels = dtos.map { it.toPanel() }

        val replaceResult = panelRepository.replaceAll(panels)
        return when (replaceResult) {
            is Result.Success -> Result.Success(replaceResult.data)
            is Result.Failure -> Result.Failure(BackupError.Unexpected(Exception("replaceAll failed: ${replaceResult.error}")))
        }
    }

    private fun Panel.toExportDto() = PanelExportDto(
        id = id,
        name = name,
        baseUrl = baseUrl,
        login = login,
        password = password,
        tlsMode = tlsMode.name,
        pinnedSpkiSha256 = pinnedSpkiSha256,
        pinnedAt = pinnedAt?.toString(),
        isActive = isActive,
        createdAt = createdAt.toString(),
        lastLoginAt = lastLoginAt?.toString(),
    )

    private fun PanelExportDto.toPanel(): Panel {
        val tlsMode = runCatching {
            com.firesin.xuipanel.core.common.TlsMode.valueOf(tlsMode)
        }.getOrDefault(com.firesin.xuipanel.core.common.TlsMode.SYSTEM)

        return Panel(
            id = id,
            name = name,
            baseUrl = baseUrl,
            login = login,
            password = password,
            tlsMode = tlsMode,
            pinnedSpkiSha256 = pinnedSpkiSha256,
            pinnedAt = pinnedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            isActive = isActive,
            createdAt = runCatching { Instant.parse(createdAt) }.getOrDefault(Instant.now()),
            lastLoginAt = lastLoginAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }
}
