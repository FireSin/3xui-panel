package com.firesin.xuipanel.core.crypto

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DbPassphraseProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreWrapper: KeystoreWrapper,
) : DbPassphraseProvider {

    private val passphraseFile: File
        get() = File(context.filesDir, PASSPHRASE_FILE_NAME)

    override suspend fun obtain(): CharArray = withContext(Dispatchers.IO) {
        val file = passphraseFile
        val rawPassphrase: ByteArray = if (file.exists()) {
            val wrapped = file.readBytes()
            keystoreWrapper.unwrap(wrapped)
        } else {
            val passphrase = ByteArray(PASSPHRASE_LENGTH).also { SecureRandom.getInstanceStrong().nextBytes(it) }
            val wrapped = keystoreWrapper.wrap(passphrase)
            file.writeBytes(wrapped)
            passphrase
        }
        // Convert bytes to chars for SQLCipher; zero out the byte array afterwards.
        val chars = CharArray(rawPassphrase.size) { rawPassphrase[it].toInt().toChar() }
        rawPassphrase.fill(0)
        chars
    }

    override suspend fun rotate() {
        // Backlog: delete old file, generate new passphrase, re-key the DB.
        error("rotate() is not implemented in MVP")
    }

    private companion object {
        const val PASSPHRASE_FILE_NAME = "passphrase.bin"
        const val PASSPHRASE_LENGTH = 32
    }
}
