package com.firesin.xuipanel.core.data.repository

import androidx.room.withTransaction
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.db.AppDatabase
import com.firesin.xuipanel.core.data.db.dao.PanelDao
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.PanelDraft
import com.firesin.xuipanel.core.data.model.toEntity
import com.firesin.xuipanel.core.data.model.toPanel
import com.firesin.xuipanel.core.network.OkHttpClientFactory
import com.firesin.xuipanel.core.xui.ProbeCredentials
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.XuiSessionCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanelRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: PanelDao,
    private val xuiClient: XuiClient,
    private val clientFactory: OkHttpClientFactory,
    private val sessionCache: XuiSessionCache,
) : PanelRepository {

    override fun observeAll(): Flow<List<Panel>> =
        dao.observeAll().map { list -> list.map { it.toPanel() } }

    override fun observeActive(): Flow<Panel?> =
        dao.observeActive().map { it?.toPanel() }

    override suspend fun get(id: String): Panel? =
        dao.getById(id)?.toPanel()

    override suspend fun probeTwoFactor(draft: PanelDraft): Result<Boolean, DomainError> {
        if (!draft.apiToken.isNullOrBlank()) return Result.Success(false)
        return xuiClient.probeTwoFactorEnabled(
            baseUrl = draft.baseUrl,
            tlsMode = draft.tlsMode,
            pinnedSpkiSha256 = null,
        )
    }

    override suspend fun add(draft: PanelDraft): Result<Panel, DomainError> {
        val probeResult = xuiClient.probeLogin(draft.toProbeCredentials())
        if (probeResult is Result.Failure) return probeResult

        val capturedSpki = (probeResult as Result.Success).data.capturedSpkiBase64
        val now = Instant.now()

        val (resolvedTlsMode, resolvedSpki, resolvedPinnedAt) = when (draft.tlsMode) {
            TlsMode.PINNED -> Triple(TlsMode.PINNED, capturedSpki, if (capturedSpki != null) now else null)
            TlsMode.SYSTEM -> Triple(TlsMode.SYSTEM, null, null)
        }

        val existing = dao.getAll()
        val isFirst = existing.isEmpty()
        val panel = Panel(
            id = UUID.randomUUID().toString(),
            name = draft.name,
            baseUrl = draft.baseUrl,
            login = draft.login,
            password = draft.password,
            tlsMode = resolvedTlsMode,
            pinnedSpkiSha256 = resolvedSpki,
            pinnedAt = resolvedPinnedAt,
            isActive = isFirst,
            createdAt = now,
            lastLoginAt = now,
            apiToken = draft.apiToken,
            twoFactorEnabled = draft.twoFactorEnabled,
        )
        dao.insert(panel.toEntity())
        return Result.Success(panel)
    }

    override suspend fun update(id: String, draft: PanelDraft): Result<Panel, DomainError> {
        val existing = dao.getById(id)
            ?: return Result.Failure(DomainError.Unexpected(NoSuchElementException("Panel $id not found")))

        val existingPanel = existing.toPanel()

        val probeResult = xuiClient.probeLogin(draft.toProbeCredentials(existingPanel))
        if (probeResult is Result.Failure) return probeResult

        val capturedSpki = (probeResult as Result.Success).data.capturedSpkiBase64
        val now = Instant.now()

        // Determine new pin state:
        // - SYSTEM: clear any pin
        // - PINNED: if probe captured a new SPKI (user switched to PINNED or re-pinning), use it;
        //           otherwise preserve existing pin (no change to credentials).
        val (resolvedSpki, resolvedPinnedAt) = when (draft.tlsMode) {
            TlsMode.SYSTEM -> null to null
            TlsMode.PINNED -> {
                val newSpki = capturedSpki ?: existingPanel.pinnedSpkiSha256
                val newPinnedAt = if (capturedSpki != null) now else existingPanel.pinnedAt
                newSpki to newPinnedAt
            }
        }

        val credentialsChanged = existing.baseUrl != draft.baseUrl ||
            existing.login != draft.login ||
            existing.password != draft.password ||
            existingPanel.tlsMode != draft.tlsMode

        // Also invalidate when pin itself changed so the old PanelCookieJar entry is evicted.
        val pinChanged = resolvedSpki != existingPanel.pinnedSpkiSha256

        if (credentialsChanged || pinChanged) {
            clientFactory.invalidate(id)
            sessionCache.invalidate(id)
        }

        val updated = Panel(
            id = id,
            name = draft.name,
            baseUrl = draft.baseUrl,
            login = draft.login,
            password = draft.password,
            tlsMode = draft.tlsMode,
            pinnedSpkiSha256 = resolvedSpki,
            pinnedAt = resolvedPinnedAt,
            isActive = existing.isActive != 0,
            createdAt = Instant.ofEpochMilli(existing.createdAt),
            lastLoginAt = now,
            apiToken = draft.apiToken,
            twoFactorEnabled = draft.twoFactorEnabled,
        )
        dao.insert(updated.toEntity())
        return Result.Success(updated)
    }

    override suspend fun rePin(id: String, draft: PanelDraft): Result<Panel, DomainError> {
        val existing = dao.getById(id)
            ?: return Result.Failure(DomainError.Unexpected(NoSuchElementException("Panel $id not found")))

        val existingPanel = existing.toPanel()

        // Force a fresh TOFU probe by passing null pin — CapturingTrustManager accepts anything.
        val probeCredentials = ProbeCredentials(
            baseUrl = draft.baseUrl,
            login = draft.login,
            password = draft.password,
            tlsMode = TlsMode.PINNED,
            pinnedSpkiSha256 = null,
            apiToken = draft.apiToken,
        )
        val probeResult = xuiClient.probeLogin(probeCredentials)
        if (probeResult is Result.Failure) return probeResult

        val capturedSpki = (probeResult as Result.Success).data.capturedSpkiBase64
        val now = Instant.now()

        // Always invalidate — pin has definitely changed (that's why we're re-pinning).
        clientFactory.invalidate(id)
        sessionCache.invalidate(id)

        val updated = Panel(
            id = id,
            name = draft.name,
            baseUrl = draft.baseUrl,
            login = draft.login,
            password = draft.password,
            tlsMode = TlsMode.PINNED,
            pinnedSpkiSha256 = capturedSpki,
            pinnedAt = if (capturedSpki != null) now else existingPanel.pinnedAt,
            isActive = existing.isActive != 0,
            createdAt = Instant.ofEpochMilli(existing.createdAt),
            lastLoginAt = now,
            apiToken = draft.apiToken,
        )
        dao.insert(updated.toEntity())
        return Result.Success(updated)
    }

    override suspend fun delete(id: String): Result<Unit, DomainError> {
        val target = dao.getById(id)
            ?: return Result.Failure(DomainError.Unexpected(NoSuchElementException("Panel $id not found")))

        dao.deleteById(id)
        clientFactory.invalidate(id)
        sessionCache.invalidate(id)

        if (target.isActive != 0) {
            val remaining = dao.getAll()
            if (remaining.isNotEmpty()) {
                dao.setActivePanel(remaining.first().id)
            }
        }

        return Result.Success(Unit)
    }

    override suspend fun setActive(id: String): Result<Unit, DomainError> {
        val exists = dao.getById(id)
            ?: return Result.Failure(DomainError.Unexpected(NoSuchElementException("Panel $id not found")))
        dao.setActivePanel(exists.id)
        return Result.Success(Unit)
    }

    override suspend fun replaceAll(panels: List<Panel>): Result<Int, DomainError> =
        runCatching {
            db.withTransaction {
                dao.deleteAll()
                dao.upsertAll(panels.map { it.toEntity() })
            }
            panels.size
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(DomainError.Unexpected(it)) },
        )

    private fun PanelDraft.toProbeCredentials(existing: Panel? = null) = ProbeCredentials(
        baseUrl = baseUrl,
        login = login,
        password = password,
        tlsMode = tlsMode,
        pinnedSpkiSha256 = if (tlsMode == TlsMode.PINNED) existing?.pinnedSpkiSha256 else null,
        apiToken = apiToken,
        twoFactorCode = twoFactorCode,
    )
}
