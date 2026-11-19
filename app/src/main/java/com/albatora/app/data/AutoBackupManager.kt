package com.albatora.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AutoBackupManager {

    private const val BACKUP_PREFIX = "albatora_auto_backup_"
    private const val MAX_KEEP_FILES = 7

    fun persistTreePermission(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
    }

    fun writeBackupToTree(
        context: Context,
        treeUriString: String,
        rawJson: String
    ): Result<String> = runCatching {
        val resolver = context.contentResolver
        val treeUri = Uri.parse(treeUriString)
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = "$BACKUP_PREFIX$stamp.json"

        val createdUri = DocumentsContract.createDocument(
            resolver,
            parentDocUri,
            "application/json",
            fileName
        ) ?: error("백업 파일을 생성할 수 없습니다.")

        resolver.openOutputStream(createdUri)?.use { stream ->
            stream.write(rawJson.toByteArray(Charsets.UTF_8))
        } ?: error("백업 파일에 쓸 수 없습니다.")

        pruneOldBackups(context, treeUri, treeDocId)
        fileName
    }

    private fun pruneOldBackups(context: Context, treeUri: Uri, treeDocId: String) {
        runCatching {
            val resolver = context.contentResolver
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val found = mutableListOf<Pair<String, String>>() // (docId, displayName)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    if (name.startsWith(BACKUP_PREFIX) && name.endsWith(".json")) {
                        found += docId to name
                    }
                }
            }

            if (found.size > MAX_KEEP_FILES) {
                val toDelete = found.sortedBy { it.second }.dropLast(MAX_KEEP_FILES)
                for ((docId, _) in toDelete) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    DocumentsContract.deleteDocument(resolver, docUri)
                }
            }
        }
    }
}
