package com.firesin.xuipanel.core.crypto

interface DbPassphraseProvider {
    /**
     * Returns the DB passphrase as a CharArray.
     * On first call: generates a random passphrase, wraps it with Keystore, saves to file.
     * On subsequent calls: reads the file and unwraps with Keystore.
     */
    suspend fun obtain(): CharArray

    /**
     * Backlog: re-encrypts the DB with a new passphrase. Not implemented in MVP.
     */
    suspend fun rotate()
}
