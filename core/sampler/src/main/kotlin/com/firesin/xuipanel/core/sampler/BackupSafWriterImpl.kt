package com.firesin.xuipanel.core.sampler

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSafWriterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupSafWriter {

    override fun isFolderWritable(treeUri: Uri): Boolean {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        return dir.canWrite()
    }

    override suspend fun writeFile(
        treeUri: Uri,
        fileName: String,
        write: suspend (java.io.OutputStream) -> Unit,
    ): Boolean {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val file = dir.createFile("application/octet-stream", fileName) ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                write(out)
            }
            true
        }.getOrElse {
            runCatching { file.delete() }
            false
        }
    }

    override fun listFiles(treeUri: Uri, prefix: String, suffix: String): List<String> {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return dir.listFiles()
            .mapNotNull { it.name }
            .filter { it.startsWith(prefix) && it.endsWith(suffix) }
    }

    override fun deleteFile(treeUri: Uri, name: String) {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return
        dir.findFile(name)?.delete()
    }
}
