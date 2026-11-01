package de.quartalsmappe.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import de.quartalsmappe.app.BuildConfig
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.StoredDocument
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun createCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val file = File(directory, "Foto_$timestamp.jpg")
    file.createNewFile()
    return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
}

fun Context.displayName(uri: Uri): String? =
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment

fun openStoredDocument(context: Context, store: AppStore, document: StoredDocument): Boolean {
    val file = store.fileFor(document)
    if (!file.exists()) return false
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, document.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(Intent.createChooser(intent, "Dokument öffnen"))
        true
    }.getOrDefault(false)
}

