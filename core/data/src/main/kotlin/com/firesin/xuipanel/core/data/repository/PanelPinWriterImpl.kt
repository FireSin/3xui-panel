package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.common.PanelPinWriter
import com.firesin.xuipanel.core.data.db.dao.PanelDao
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanelPinWriterImpl @Inject constructor(
    private val dao: PanelDao,
) : PanelPinWriter {

    override suspend fun writePin(panelId: String, spkiBase64: String) {
        dao.updatePin(
            id = panelId,
            spkiBase64 = spkiBase64,
            pinnedAt = Instant.now().toEpochMilli(),
        )
    }
}
