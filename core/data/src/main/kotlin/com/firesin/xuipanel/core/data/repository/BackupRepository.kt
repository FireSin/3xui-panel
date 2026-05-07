package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.crypto.BackupError
import com.firesin.xuipanel.core.common.Result

interface BackupRepository {

    /**
     * Encrypts all panels with [passphrase] and returns a JSON envelope string.
     * Clears [passphrase] after use.
     */
    suspend fun exportPanels(passphrase: CharArray): Result<String, BackupError>

    /**
     * Decrypts and imports panels from a JSON [envelopeJson] using [passphrase].
     * Replaces ALL existing panels atomically.
     * Returns the count of restored panels on success.
     * Clears [passphrase] after use.
     */
    suspend fun importPanels(envelopeJson: String, passphrase: CharArray): Result<Int, BackupError>
}
