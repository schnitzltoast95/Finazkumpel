package de.quartalsmappe.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import de.quartalsmappe.app.model.StoredDocument
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupResult(
    val fileName: String,
    val createdAt: LocalDateTime,
)

data class RestoreResult(
    val dailyClosesAdded: Int,
    val invoicesAdded: Int,
    val documentsAdded: Int,
    val skippedConflicts: Int,
    val originalFilesRestored: Int = 0,
    val customTypesAdded: Int = 0,
)

data class ExtractedBackup(
    val directory: File,
    val dataFile: File,
    val documentsDirectory: File,
    val revisionsDirectory: File,
)

object BackupArchive {
    private const val MAX_RESTORE_BYTES = 500L * 1024 * 1024
    private const val CURRENT_BACKUP_NAME = "Quartalsmappe_Aktuell.zip"

    fun updateCurrent(
        context: Context,
        treeUri: Uri,
        dataJson: String,
        documents: List<Pair<StoredDocument, File>>,
        revisions: List<File> = emptyList(),
    ): BackupResult = create(
        context = context,
        treeUri = treeUri,
        dataJson = dataJson,
        documents = documents,
        revisions = revisions,
        fileName = CURRENT_BACKUP_NAME,
        replaceExisting = true,
    )

    fun createSnapshot(
        context: Context,
        treeUri: Uri,
        dataJson: String,
        documents: List<Pair<StoredDocument, File>>,
        revisions: List<File> = emptyList(),
    ): BackupResult {
        val createdAt = LocalDateTime.now()
        val timestamp = createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS"))
        val fileName = "Quartalsmappe_Backup_${timestamp}_${UUID.randomUUID().toString().take(8)}.zip"
        return create(context, treeUri, dataJson, documents, revisions, fileName, replaceExisting = false, createdAt)
    }

    private fun create(
        context: Context,
        treeUri: Uri,
        dataJson: String,
        documents: List<Pair<StoredDocument, File>>,
        revisions: List<File>,
        fileName: String,
        replaceExisting: Boolean,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): BackupResult {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocument = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val existing = if (replaceExisting) findChild(context, treeUri, rootId, fileName) else null
        val localArchive = File.createTempFile("quartalsmappe_backup_", ".zip", context.cacheDir)
        try {
            localArchive.outputStream().buffered().use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("data.json"))
                    zip.write(dataJson.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("README.txt"))
                    zip.write(
                        "Quartalsmappe-Datensicherung\nErstellt: $createdAt\n".toByteArray(Charsets.UTF_8)
                    )
                    zip.closeEntry()

                    documents.forEach { (document, file) ->
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry("documents/${document.storedFileName}"))
                            file.inputStream().buffered().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    revisions.forEach { file ->
                        if (file.isFile && file.extension == "json") {
                            zip.putNextEntry(ZipEntry("revisions/${file.name}"))
                            file.inputStream().buffered().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            require(localArchive.length() > 0L) { "Die Sicherung konnte nicht vollständig vorbereitet werden." }

            val stagingName = "Quartalsmappe_${UUID.randomUUID()}.tmp"
            val staging = DocumentsContract.createDocument(
                context.contentResolver,
                rootDocument,
                "application/zip",
                if (replaceExisting) stagingName else fileName,
            ) ?: error("Im ausgewählten Ordner konnte keine Sicherung angelegt werden.")
            try {
                context.contentResolver.openOutputStream(staging, "w").use { output ->
                    requireNotNull(output) { "Die Sicherungsdatei konnte nicht geöffnet werden." }
                    localArchive.inputStream().buffered().use { input -> input.copyTo(output) }
                }
                if (replaceExisting) {
                    val previousName = "Quartalsmappe_Vorher_${UUID.randomUUID()}.zip"
                    val previous = existing?.let {
                        DocumentsContract.renameDocument(context.contentResolver, it, previousName)
                            ?: error("Die bisherige Sicherung konnte nicht geschützt werden.")
                    }
                    try {
                        requireNotNull(DocumentsContract.renameDocument(context.contentResolver, staging, fileName)) {
                            "Der aktuelle Sicherungsstand konnte nicht benannt werden. Die vorige Sicherung bleibt erhalten."
                        }
                        previous?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
                    } catch (error: Exception) {
                        previous?.let { old ->
                            runCatching { DocumentsContract.renameDocument(context.contentResolver, old, fileName) }
                        }
                        throw error
                    }
                }
            } catch (error: Exception) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, staging) }
                throw error
            }
        } finally {
            localArchive.delete()
        }
        return BackupResult(fileName, createdAt)
    }

    private fun findChild(context: Context, treeUri: Uri, rootId: String, fileName: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == fileName) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                }
            }
        }
        return null
    }

    fun extract(context: Context, source: Uri): ExtractedBackup {
        val root = File(context.cacheDir, "restore/${UUID.randomUUID()}").apply { mkdirs() }
        val rootPath = root.canonicalPath + File.separator
        var totalBytes = 0L
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Die Sicherungsdatei konnte nicht geöffnet werden." }
                ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val destination = File(root, entry.name)
                    require(destination.canonicalPath.startsWith(rootPath)) { "Ungültige Sicherungsdatei." }
                    destination.parentFile?.mkdirs()
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count <= 0) break
                            totalBytes += count
                            require(totalBytes <= MAX_RESTORE_BYTES) { "Die Sicherung ist ungewöhnlich groß." }
                            output.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
                }
            }
            val data = File(root, "data.json")
            require(data.exists()) { "Die Datei ist keine gültige Quartalsmappe-Sicherung." }
            return ExtractedBackup(root, data, File(root, "documents"), File(root, "revisions"))
        } catch (error: Exception) {
            root.deleteRecursively()
            throw error
        }
    }
}

