package com.firesin.xuipanel.core.sampler

import android.net.Uri

/**
 * Abstraction over SAF folder operations used by [BackupWorkerCore].
 * Extracted to allow JVM unit tests without Robolectric.
 */
interface BackupSafWriter {

    /**
     * Returns true iff the folder at [treeUri] exists and is writable.
     */
    fun isFolderWritable(treeUri: Uri): Boolean

    /**
     * Creates a file in the folder at [treeUri] with the given [fileName].
     * Writes the content by calling [write] with an open OutputStream.
     * Returns true on success, false if the file could not be created or write failed.
     */
    suspend fun writeFile(treeUri: Uri, fileName: String, write: suspend (java.io.OutputStream) -> Unit): Boolean

    /**
     * Lists file names inside [treeUri] whose name starts with [prefix] and ends with [suffix].
     * The list is NOT guaranteed to be ordered — callers must sort it.
     * Returns an empty list if the folder is not accessible.
     */
    fun listFiles(treeUri: Uri, prefix: String, suffix: String): List<String>

    /**
     * Deletes the file with the given [name] from the folder at [treeUri].
     * Silently ignores if the file does not exist.
     */
    fun deleteFile(treeUri: Uri, name: String)
}
