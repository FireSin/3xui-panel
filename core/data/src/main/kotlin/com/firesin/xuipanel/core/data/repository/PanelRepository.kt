package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.PanelDraft
import kotlinx.coroutines.flow.Flow

interface PanelRepository {

    fun observeAll(): Flow<List<Panel>>

    fun observeActive(): Flow<Panel?>

    suspend fun get(id: String): Panel?

    suspend fun add(draft: PanelDraft): Result<Panel, DomainError>

    suspend fun update(id: String, draft: PanelDraft): Result<Panel, DomainError>

    /**
     * Re-probes the panel with a null pin (ignoring any stored pin) and overwrites the stored
     * SPKI with the newly observed one. Use when the user explicitly confirms a pin-mismatch
     * dialog. Always invalidates the client and session caches for [id].
     */
    suspend fun rePin(id: String, draft: PanelDraft): Result<Panel, DomainError>

    suspend fun delete(id: String): Result<Unit, DomainError>

    suspend fun setActive(id: String): Result<Unit, DomainError>
}
