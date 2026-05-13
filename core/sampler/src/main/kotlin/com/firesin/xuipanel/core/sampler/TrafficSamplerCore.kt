package com.firesin.xuipanel.core.sampler

import com.firesin.xuipanel.core.common.PanelTls
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.data.repository.TrafficHistoryRepository
import com.firesin.xuipanel.core.xui.XuiClient
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Pure orchestration logic for one sampler tick. Extracted from [TrafficSamplerWorker] so that
 * it is testable on the JVM without Robolectric.
 *
 * Iterates panels **sequentially** (§3 design doc: XuiSessionCache is not re-entrant per-panel).
 * Returns [Outcome] that the Worker uses to decide [androidx.work.ListenableWorker.Result].
 */
class TrafficSamplerCore @Inject constructor(
    private val panelRepository: PanelRepository,
    private val xuiClient: XuiClient,
    private val historyRepository: TrafficHistoryRepository,
    private val clock: SamplerClock,
) {

    sealed class Outcome {
        /** At least one panel was sampled successfully. */
        data object PartialOrFullSuccess : Outcome()

        /** All N panels failed. Caller should schedule a retry. */
        data object AllFailed : Outcome()

        /** No panels configured — nothing to do. */
        data object NoPanels : Outcome()
    }

    /** Runs one sampling tick. */
    suspend fun runOnce(): Outcome {
        val panels: List<Panel> = panelRepository.observeAll().first()
        if (panels.isEmpty()) return Outcome.NoPanels

        val now = clock.nowMillis()
        var successCount = 0

        for (panel in panels) {
            val tls = PanelTls(
                mode = panel.tlsMode,
                pinnedSpkiSha256 = panel.pinnedSpkiSha256,
            )

            val fetchResult = xuiClient.fetchInbounds(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = tls,
            )

            when (fetchResult) {
                is Result.Failure -> {
                    // Per-panel failure is logged by XuiClient already; continue to next panel.
                    continue
                }
                is Result.Success -> {
                    val commitResult = historyRepository.commitSample(
                        panelId = panel.id,
                        inbounds = fetchResult.data,
                        sampledAt = now,
                    )
                    if (commitResult is Result.Success) {
                        successCount++
                    }
                    // On DB failure we still count the panel as attempted — don't retry globally.
                }
            }
        }

        return if (successCount > 0) Outcome.PartialOrFullSuccess else Outcome.AllFailed
    }

}
