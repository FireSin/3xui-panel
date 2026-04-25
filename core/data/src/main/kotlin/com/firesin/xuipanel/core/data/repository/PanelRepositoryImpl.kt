package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.db.dao.PanelDao
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.PanelDraft
import com.firesin.xuipanel.core.data.model.toEntity
import com.firesin.xuipanel.core.data.model.toPanel
import com.firesin.xuipanel.core.xui.ProbeCredentials
import com.firesin.xuipanel.core.xui.XuiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanelRepositoryImpl @Inject constructor(
    private val dao: PanelDao,
    private val xuiClient: XuiClient,
) : PanelRepository {

    override fun observeAll(): Flow<List<Panel>> =
        dao.observeAll().map { list -> list.map { it.toPanel() } }

    override fun observeActive(): Flow<Panel?> =
        dao.observeActive().map { it?.toPanel() }

    override suspend fun get(id: String): Panel? =
        dao.getById(id)?.toPanel()

    override suspend fun add(draft: PanelDraft): Result<Panel, DomainError> {
        val probeResult = xuiClient.probeLogin(draft.toProbeCredentials())
        if (probeResult is Result.Failure) return probeResult

        val existing = dao.getAll()
        val isFirst = existing.isEmpty()
        val now = Instant.now()
        val panel = Panel(
            id = UUID.randomUUID().toString(),
            name = draft.name,
            baseUrl = draft.baseUrl,
            login = draft.login,
            password = draft.password,
            trustSelfSigned = draft.trustSelfSigned,
            isActive = isFirst,
            createdAt = now,
            lastLoginAt = now,
        )
        dao.insert(panel.toEntity())
        return Result.Success(panel)
    }

    override suspend fun update(id: String, draft: PanelDraft): Result<Panel, DomainError> {
        val existing = dao.getById(id)
            ?: return Result.Failure(DomainError.Unexpected(NoSuchElementException("Panel $id not found")))

        val probeResult = xuiClient.probeLogin(draft.toProbeCredentials())
        if (probeResult is Result.Failure) return probeResult

        val updated = Panel(
            id = id,
            name = draft.name,
            baseUrl = draft.baseUrl,
            login = draft.login,
            password = draft.password,
            trustSelfSigned = draft.trustSelfSigned,
            isActive = existing.isActive != 0,
            createdAt = Instant.ofEpochMilli(existing.createdAt),
            lastLoginAt = Instant.now(),
        )
        dao.insert(updated.toEntity())
        return Result.Success(updated)
    }

    override suspend fun delete(id: String): Result<Unit, DomainError> {
        val target = dao.getById(id)
            ?: return Result.Failure(DomainError.Unexpected(NoSuchElementException("Panel $id not found")))

        dao.deleteById(id)

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

    private fun PanelDraft.toProbeCredentials() = ProbeCredentials(
        baseUrl = baseUrl,
        login = login,
        password = password,
        trustSelfSigned = trustSelfSigned,
    )
}
