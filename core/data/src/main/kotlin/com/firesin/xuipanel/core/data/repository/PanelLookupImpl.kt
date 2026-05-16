package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.common.PanelLookup
import com.firesin.xuipanel.core.common.PanelMetadata
import com.firesin.xuipanel.core.data.db.dao.PanelDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads panel metadata directly from [PanelDao] to avoid a dependency cycle:
 * PanelRepository → XuiClient → PanelLookup → PanelRepository.
 */
@Singleton
class PanelLookupImpl @Inject constructor(
    private val panelDao: PanelDao,
) : PanelLookup {
    override suspend fun lookup(panelId: String): PanelMetadata? =
        panelDao.getById(panelId)?.let {
            PanelMetadata(name = it.name, twoFactorEnabled = it.twoFactorEnabled)
        }
}
