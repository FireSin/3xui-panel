package com.firesin.xuipanel.core.sampler

import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.toAuth
import com.firesin.xuipanel.core.data.model.toPanelTls
import com.firesin.xuipanel.core.data.repository.AutoBackupPreferences
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Pure orchestration logic for one automatic backup run.
 * Extracted from [BackupWorker] to be unit-testable on the JVM without Robolectric.
 *
 * SAF operations are delegated to [BackupSafWriter] for testability.
 */
class BackupWorkerCore @Inject constructor(
    private val panelRepository: PanelRepository,
    private val xuiClient: XuiClient,
    private val autoBackupPreferences: AutoBackupPreferences,
    private val safWriter: BackupSafWriter,
) {

    sealed class Outcome {
        data class Success(val count: Int) : Outcome()
        data class PartialFailure(val succeeded: Int, val failed: Int) : Outcome()
        data class Failure(val reason: String) : Outcome()

        fun toResultString(): String = when (this) {
            is Success -> "Success($count)"
            is PartialFailure -> "PartialFailure($succeeded, $failed)"
            is Failure -> "Failure($reason)"
        }
    }

    /** Runs one backup tick. Returns [Outcome] that [BackupWorker] uses to decide the WorkManager result. */
    suspend fun runOnce(): Outcome {
        val targetUri = autoBackupPreferences.targetUri.first()
            ?: return Outcome.Failure("No target folder selected")

        if (!safWriter.isFolderWritable(targetUri)) {
            val result = Outcome.Failure("Target folder not accessible")
            autoBackupPreferences.setLastRun(Instant.now().toEpochMilli(), result.toResultString())
            return result
        }

        val panels: List<Panel> = panelRepository.observeAll().first()
        if (panels.isEmpty()) {
            val result = Outcome.Success(0)
            autoBackupPreferences.setLastRun(Instant.now().toEpochMilli(), result.toResultString())
            return result
        }

        val now = Instant.now()
        var succeeded = 0
        var failed = 0

        for (panel in panels) {
            if (panel.twoFactorEnabled) {
                // Worker cannot prompt for OTP interactively — skip and count as failure
                failed++
                continue
            }

            val label = sanitizeLabel(panel.name)
            val timestamp = FILENAME_TIMESTAMP_FMT.format(now.atZone(ZoneId.systemDefault()))
            val fileName = "${label}_${timestamp}.db"

            val writeOk = writeDbToSaf(panel, targetUri, fileName)
            if (writeOk) {
                enforceRetention(targetUri, label)
                succeeded++
            } else {
                failed++
            }
        }

        val outcome = when {
            succeeded == 0 && failed > 0 -> Outcome.Failure("All $failed panel(s) failed")
            failed > 0 -> Outcome.PartialFailure(succeeded, failed)
            else -> Outcome.Success(succeeded)
        }

        autoBackupPreferences.setLastRun(now.toEpochMilli(), outcome.toResultString())
        return outcome
    }

    private suspend fun writeDbToSaf(
        panel: Panel,
        targetUri: android.net.Uri,
        fileName: String,
    ): Boolean {
        var streamResult: Result<Unit, *>? = null

        val writeOk = safWriter.writeFile(targetUri, fileName) { out ->
            streamResult = xuiClient.fetchDbInto(
                panelId = panel.id,
                baseUrl = panel.baseUrl,
                auth = panel.toAuth(),
                tls = panel.toPanelTls(),
            ) { inputStream ->
                inputStream.copyTo(out, bufferSize = COPY_BUFFER_SIZE)
            }
        }

        if (!writeOk) return false
        return streamResult is Result.Success
    }

    /**
     * Keeps at most [MAX_FILES_PER_PANEL] files for a given panel.
     * Files are sorted by name descending (yyyyMMdd_HHmmss suffix → lexicographic = chronological).
     * Oldest files beyond the limit are deleted.
     */
    private fun enforceRetention(targetUri: android.net.Uri, labelPrefix: String) {
        val prefix = "${labelPrefix}_"
        val suffix = ".db"
        val files = safWriter.listFiles(targetUri, prefix, suffix)
            .sortedDescending() // newest first (name sort = time sort for our format)

        if (files.size > MAX_FILES_PER_PANEL) {
            files.drop(MAX_FILES_PER_PANEL).forEach { name ->
                safWriter.deleteFile(targetUri, name)
            }
        }
    }

    companion object {
        private const val MAX_FILES_PER_PANEL = 7
        private const val COPY_BUFFER_SIZE = 8 * 1024

        private val FILENAME_TIMESTAMP_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        /** Replaces any character outside [A-Za-z0-9_-] with underscore. */
        fun sanitizeLabel(raw: String): String =
            raw.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
    }
}
