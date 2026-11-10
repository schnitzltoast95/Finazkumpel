package de.quartalsmappe.app.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.quartalsmappe.app.model.AppearanceSettings
import de.quartalsmappe.app.model.CustomDocumentType
import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.ReminderSettings
import de.quartalsmappe.app.model.StoredDocument
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class AppStore(private val context: Context, scheduleBackups: Boolean = true) {
    private val preferences = context.getSharedPreferences("quartalsmappe_data", Context.MODE_PRIVATE)
    private val backupPreferences = context.getSharedPreferences("quartalsmappe_backup", Context.MODE_PRIVATE)
    private val backupListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("last_backup_at", "last_backup_error", "changed_generation", "backed_up_generation")) {
            refreshBackupStatus()
        }
    }
    private val appearancePreferences = context.getSharedPreferences("quartalsmappe_appearance", Context.MODE_PRIVATE)
    private val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val revisionsDir = File(context.filesDir, "revisions").apply { mkdirs() }

    var dailyCloses by mutableStateOf<List<DailyClose>>(emptyList())
        private set

    var invoices by mutableStateOf<List<InvoiceCase>>(emptyList())
        private set

    var documents by mutableStateOf<List<StoredDocument>>(emptyList())
        private set

    var customDocumentTypes by mutableStateOf<List<CustomDocumentType>>(emptyList())
        private set

    var reminderSettings by mutableStateOf(ReminderSettings())
        private set

    var appearanceSettings by mutableStateOf(
        AppearanceSettings.fromStableIds(
            paletteId = appearancePreferences.getString("palette", null),
            companionId = appearancePreferences.getString("companion", null),
            themeModeId = appearancePreferences.getString("theme_mode", null),
        )
    )
        private set

    var backupFolderUri by mutableStateOf(backupPreferences.getString("folder_uri", null))
        private set

    var backupIntervalDays by mutableStateOf(backupPreferences.getInt("interval_days", 1).takeIf { it in listOf(1, 3, 7) } ?: 1)
        private set

    var hasPendingBackup by mutableStateOf(
        backupPreferences.getLong("changed_generation", 0) > backupPreferences.getLong("backed_up_generation", -1)
    )
        private set

    var lastBackupAt by mutableStateOf(
        backupPreferences.getString("last_backup_at", null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    )
        private set

    var lastBackupError by mutableStateOf(backupPreferences.getString("last_backup_error", null))
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    init {
        load()
        if (scheduleBackups) {
            backupPreferences.registerOnSharedPreferenceChangeListener(backupListener)
            BackupSchedule.update(context, backupFolderUri != null, backupIntervalDays)
        }
    }

    fun customType(id: String?): CustomDocumentType? =
        id?.let { selected -> customDocumentTypes.firstOrNull { it.id == selected } }

    fun documentTypeLabel(document: StoredDocument): String =
        customType(document.customTypeId)?.name ?: document.type.label

    fun addCustomDocumentType(name: String, usesAnnualPeriod: Boolean = false): CustomDocumentType {
        val type = CustomDocumentType(
            name = validateNewCustomTypeName(name, customDocumentTypes),
            usesAnnualPeriod = usesAnnualPeriod,
        )
        val previous = customDocumentTypes
        customDocumentTypes = customDocumentTypes + type
        runCatching { persist() }.onFailure { customDocumentTypes = previous }.getOrThrow()
        return type
    }

    fun saveDailyClose(close: DailyClose) {
        val duplicateForDate = dailyCloses.firstOrNull {
            it.businessDate == close.businessDate && it.id != close.id
        }
        require(duplicateForDate == null) {
            "Für den ${close.businessDate} ist bereits ein Tagesabschluss vorhanden. Der vorhandene Eintrag wurde nicht verändert."
        }
        val existing = dailyCloses.firstOrNull { it.id == close.id }
        if (existing != null) writeRevision("tagesabschluss", existing.id, existing.toJson())
        val updated = close.copy(
            createdAt = existing?.createdAt ?: close.createdAt,
            documentIds = (existing?.documentIds.orEmpty() + close.documentIds).distinct(),
            updatedAt = LocalDateTime.now(),
        )
        val previousList = dailyCloses
        dailyCloses = (dailyCloses.filterNot { it.id == updated.id } + updated)
            .sortedByDescending { it.businessDate }
        runCatching { persist() }.onFailure { dailyCloses = previousList }.getOrThrow()
    }

    fun saveInvoice(invoice: InvoiceCase) {
        val existing = invoices.firstOrNull { it.id == invoice.id }
        if (existing != null) writeRevision("rechnung", existing.id, existing.toJson())
        val updated = invoice.copy(
            createdAt = existing?.createdAt ?: invoice.createdAt,
            documentIds = (existing?.documentIds.orEmpty() + invoice.documentIds).distinct(),
            updatedAt = LocalDateTime.now(),
        )
        val previousList = invoices
        invoices = (invoices.filterNot { it.id == updated.id } + updated)
            .sortedByDescending { it.updatedAt }
        runCatching { persist() }.onFailure { invoices = previousList }.getOrThrow()
    }

    fun saveReminderSettings(settings: ReminderSettings) {
        val previous = reminderSettings
        reminderSettings = settings
        runCatching { persist() }.onFailure { reminderSettings = previous }.getOrThrow()
    }

    fun saveAppearanceSettings(settings: AppearanceSettings) {
        check(
            appearancePreferences.edit()
                .putString("palette", settings.palette.stableId)
                .putString("companion", settings.companion.stableId)
                .putString("theme_mode", settings.themeMode.stableId)
                .commit()
        ) { "Das Aussehen konnte nicht gespeichert werden. Bitte versuche es noch einmal." }
        appearanceSettings = settings
    }

    fun revisionCount(entityType: String, entityId: String): Int =
        revisionsDir.listFiles()?.count { it.name.startsWith("${entityType}_${entityId}_") } ?: 0

    fun setBackupFolder(uri: Uri): BackupResult {
        requireReadableState()
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        require(
            backupPreferences.edit().putString("folder_uri", uri.toString()).commit()
        ) { "Der Sicherungsordner konnte nicht gespeichert werden." }
        backupFolderUri = uri.toString()
        return try {
            updateBackupNow()
        } finally {
            BackupSchedule.update(context, true, backupIntervalDays)
        }
    }

    fun changeBackupIntervalDays(days: Int) {
        require(days in listOf(1, 3, 7)) { "Bitte einen gültigen Sicherungsabstand wählen." }
        check(backupPreferences.edit().putInt("interval_days", days).commit()) {
            "Der Sicherungsabstand konnte nicht gespeichert werden."
        }
        backupIntervalDays = days
        BackupSchedule.update(context, backupFolderUri != null, days)
    }

    fun refreshBackupStatus() {
        lastBackupAt = backupPreferences.getString("last_backup_at", null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        lastBackupError = backupPreferences.getString("last_backup_error", null)
        hasPendingBackup = backupPreferences.getLong("changed_generation", 0) >
            backupPreferences.getLong("backed_up_generation", -1)
    }

    fun updateBackupNow(): BackupResult = synchronized(BackupSchedule.backupLock) {
        performBackup()
    }

    internal fun updateBackupIfPending(): BackupResult? = synchronized(BackupSchedule.backupLock) {
        refreshBackupStatus()
        if (hasPendingBackup) performBackup() else null
    }

    private fun performBackup(): BackupResult {
        requireReadableState()
        val uri = backupFolderUri?.let(Uri::parse)
            ?: error("Bitte zuerst einen Sicherungsordner auswählen.")
        val generation = backupPreferences.getLong("changed_generation", 0)
        return runCatching {
            BackupArchive.updateCurrent(
                context = context,
                treeUri = uri,
                dataJson = serializeState(),
                documents = documents.map { it to fileFor(it) },
                revisions = revisionFiles(),
            )
        }.onSuccess { result ->
            recordBackupSuccess(result.createdAt, generation)
        }.onFailure { failure ->
            recordBackupFailure(failure.message ?: "Sicherung fehlgeschlagen")
        }.getOrThrow()
    }

    fun createBackupSnapshot(): BackupResult {
        requireReadableState()
        val uri = backupFolderUri?.let(Uri::parse)
            ?: error("Bitte zuerst einen Sicherungsordner auswählen.")
        return BackupArchive.createSnapshot(
            context = context,
            treeUri = uri,
            dataJson = serializeState(),
            documents = documents.map { it to fileFor(it) },
            revisions = revisionFiles(),
        )
    }

    fun restoreBackup(source: Uri): RestoreResult {
        val extracted = BackupArchive.extract(context, source)
        return try {
            val root = JSONObject(extracted.dataFile.readText(Charsets.UTF_8))
            validateDataRoot(root)
            val importedCloses = root.optJSONArray("dailyCloses").toObjects(::dailyCloseFromJson)
            val importedInvoices = root.optJSONArray("invoices").toObjects(::invoiceFromJson)
            val importedTypes = root.optJSONArray("customDocumentTypes").toObjects(::customDocumentTypeFromJson)
            val typeMerge = mergeCustomDocumentTypes(customDocumentTypes, importedTypes)
            val importedDocuments = root.optJSONArray("documents").toObjects(::documentFromJson).map { document ->
                val customId = document.customTypeId
                if (customId == null) document else {
                    require(document.owner == DocumentOwner.GENERAL && document.type == DocumentType.OTHER) {
                        "Die Sicherung enthält eine ungültige Zuordnung zu einer eigenen Art."
                    }
                    val mappedId = requireNotNull(typeMerge.importedIdMapping[customId]) {
                        "In der Sicherung fehlt die eigene Art zu einem Eintrag."
                    }
                    document.copy(customTypeId = mappedId)
                }
            }

            val existingCloseIds = dailyCloses.mapTo(mutableSetOf()) { it.id }
            val existingDates = dailyCloses.mapTo(mutableSetOf()) { it.businessDate }
            val newCloses = importedCloses.filter { it.id !in existingCloseIds && it.businessDate !in existingDates }

            val existingInvoiceIds = invoices.mapTo(mutableSetOf()) { it.id }
            val newInvoices = importedInvoices.filter { it.id !in existingInvoiceIds }

            val validDailyIds = (dailyCloses + newCloses).mapTo(mutableSetOf()) { it.id }
            val validInvoiceIds = (invoices + newInvoices).mapTo(mutableSetOf()) { it.id }
            val existingDocumentIds = documents.mapTo(mutableSetOf()) { it.id }
            val newDocuments = importedDocuments.filter { document ->
                val ownerExists = when (document.owner) {
                    DocumentOwner.DAILY_CLOSE -> document.ownerId in validDailyIds
                    DocumentOwner.INVOICE -> document.ownerId in validInvoiceIds
                    DocumentOwner.GENERAL -> true
                }
                val sourceFile = File(extracted.documentsDirectory, document.storedFileName)
                val destination = fileFor(document)
                document.id !in existingDocumentIds && ownerExists && sourceFile.isFile && !destination.exists() &&
                    sourceFile.parentFile?.canonicalFile == extracted.documentsDirectory.canonicalFile &&
                    destination.parentFile?.canonicalFile == documentsDir.canonicalFile &&
                    document.sha256.isNotBlank() && sourceFile.sha256().equals(document.sha256, ignoreCase = true)
            }
            val documentsById = documents.associateBy { it.id }
            val restorableOriginals = importedDocuments.filter { imported ->
                val existing = documentsById[imported.id]
                val sourceFile = File(extracted.documentsDirectory, imported.storedFileName)
                val destination = fileFor(imported)
                existing != null && existing == imported && existing.sha256.isNotBlank() &&
                    !destination.exists() && sourceFile.isFile &&
                    sourceFile.parentFile?.canonicalFile == extracted.documentsDirectory.canonicalFile &&
                    destination.parentFile?.canonicalFile == documentsDir.canonicalFile &&
                    sourceFile.sha256().equals(existing.sha256, ignoreCase = true)
            }.distinctBy { it.id }
            val eligibleRevisionPrefixes = buildList {
                importedCloses.filter { it in newCloses || dailyCloses.any { current -> current == it } }
                    .forEach { add("tagesabschluss_${it.id}_") }
                importedInvoices.filter { it in newInvoices || invoices.any { current -> current == it } }
                    .forEach { add("rechnung_${it.id}_") }
                importedDocuments.filter { it in newDocuments || documents.any { current -> current == it } }
                    .forEach { add("unterlage_${it.id}_") }
            }
            val newRevisions = extracted.revisionsDirectory.listFiles()?.filter { source ->
                source.isFile && source.name.matches(Regex("[A-Za-z0-9_.-]+\\.json")) &&
                    eligibleRevisionPrefixes.any { source.name.startsWith(it) } &&
                    source.parentFile?.canonicalFile == extracted.revisionsDirectory.canonicalFile &&
                    !File(revisionsDir, source.name).exists()
            }.orEmpty()
            val copiedFiles = mutableListOf<File>()
            try {
                (newDocuments + restorableOriginals).forEach { document ->
                    val sourceFile = File(extracted.documentsDirectory, document.storedFileName)
                    val temporary = File.createTempFile(".restore_", ".tmp", documentsDir)
                    try {
                        sourceFile.copyTo(temporary, overwrite = true)
                        val destination = fileFor(document)
                        Files.move(temporary.toPath(), destination.toPath())
                        copiedFiles += destination
                    } finally {
                        temporary.delete()
                    }
                }
                newRevisions.forEach { source ->
                    JSONObject(source.readText(Charsets.UTF_8))
                    val destination = File(revisionsDir, source.name)
                    val temporary = File.createTempFile(".restore_", ".tmp", revisionsDir)
                    try {
                        source.copyTo(temporary, overwrite = true)
                        Files.move(temporary.toPath(), destination.toPath())
                        copiedFiles += destination
                    } finally {
                        temporary.delete()
                    }
                }
            } catch (error: Exception) {
                copiedFiles.forEach(File::delete)
                throw error
            }

            val previousDailyCloses = dailyCloses
            val previousInvoices = invoices
            val previousDocuments = documents
            val previousTypes = customDocumentTypes
            dailyCloses = (dailyCloses + newCloses).sortedByDescending { it.businessDate }
            invoices = (invoices + newInvoices).sortedByDescending { it.updatedAt }
            documents = (documents + newDocuments).sortedByDescending { it.createdAt }
            customDocumentTypes = typeMerge.types
            runCatching { persist(allowRecovery = true) }.onFailure {
                dailyCloses = previousDailyCloses
                invoices = previousInvoices
                documents = previousDocuments
                customDocumentTypes = previousTypes
                copiedFiles.forEach(File::delete)
            }.getOrThrow()

            loadError = null

            RestoreResult(
                dailyClosesAdded = newCloses.size,
                invoicesAdded = newInvoices.size,
                documentsAdded = newDocuments.size,
                skippedConflicts = (importedCloses.size - newCloses.size) +
                    (importedInvoices.size - newInvoices.size) +
                    (importedDocuments.size - newDocuments.size - restorableOriginals.size),
                originalFilesRestored = restorableOriginals.size,
                customTypesAdded = typeMerge.types.size - previousTypes.size,
            )
        } finally {
            extracted.directory.deleteRecursively()
        }
    }

    fun possibleDuplicate(supplier: String, invoiceNumber: String, excludingId: String? = null): InvoiceCase? {
        if (supplier.isBlank() || invoiceNumber.isBlank()) return null
        return invoices.firstOrNull {
            it.id != excludingId &&
                it.supplier.trim().equals(supplier.trim(), ignoreCase = true) &&
                it.invoiceNumber.trim().equals(invoiceNumber.trim(), ignoreCase = true)
        }
    }

    fun importDocument(
        source: Uri,
        type: DocumentType,
        owner: DocumentOwner,
        ownerId: String?,
        periodYear: Int?,
        periodQuarter: Int?,
        note: String = "",
        customTypeId: String? = null,
    ): StoredDocument {
        val custom = validateDocumentCustomType(type, owner, customTypeId)
        if (owner == DocumentOwner.GENERAL) {
            require(periodYear != null && periodYear in 1..9999) { "Bitte ein gültiges Ablagejahr auswählen." }
            if (custom?.usesAnnualPeriod ?: type.usesAnnualPeriod) {
                require(periodQuarter == null) { "Jahresunterlagen dürfen keinem Quartal zugeordnet sein." }
            } else {
                require(periodQuarter != null && periodQuarter in 1..4) {
                    "Bitte ein gültiges Quartal auswählen."
                }
            }
        }
        val originalName = queryFileName(source) ?: "Dokument"
        val extension = originalName.substringAfterLast('.', "bin").take(10)
        val storedName = "${UUID.randomUUID()}.$extension"
        val destination = File(documentsDir, storedName)
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Die Datei konnte nicht geöffnet werden." }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        val document = StoredDocument(
            type = type,
            owner = owner,
            ownerId = ownerId,
            periodYear = periodYear,
            periodQuarter = periodQuarter,
            originalFileName = originalName,
            storedFileName = storedName,
            mimeType = context.contentResolver.getType(source) ?: "application/octet-stream",
            sha256 = destination.sha256(),
            note = note,
            customTypeId = customTypeId,
        )
        val previousDocuments = documents
        val previousDailyCloses = dailyCloses
        val previousInvoices = invoices
        documents = (documents + document).sortedByDescending { it.createdAt }
        attachDocument(document)
        runCatching { persist() }.onFailure {
            documents = previousDocuments
            dailyCloses = previousDailyCloses
            invoices = previousInvoices
            destination.delete()
        }.getOrThrow()
        return document
    }

    fun fileFor(document: StoredDocument): File = File(documentsDir, document.storedFileName)

    fun updateDocumentMetadata(
        documentId: String,
        type: DocumentType,
        periodYear: Int,
        periodQuarter: Int?,
        note: String,
        customTypeId: String? = null,
    ) {
        val existing = documents.firstOrNull { it.id == documentId }
            ?: error("Diese Unterlage wurde nicht gefunden.")
        require(existing.owner == DocumentOwner.GENERAL) {
            "Bitte ändere diesen Beleg über den zugehörigen Tagesabschluss oder die Ausgabe."
        }
        require(type !in setOf(DocumentType.Z_REPORT, DocumentType.INVOICE_EMAIL, DocumentType.INVOICE_PAPER, DocumentType.DELIVERY_NOTE)) {
            "Bitte wähle eine Kategorie für allgemeine Unterlagen."
        }
        require(periodYear in 1..9999) { "Bitte ein gültiges Jahr auswählen." }
        val custom = validateDocumentCustomType(type, existing.owner, customTypeId)
        require(if (custom?.usesAnnualPeriod ?: type.usesAnnualPeriod) periodQuarter == null else periodQuarter != null && periodQuarter in 1..4) {
            "Bitte einen passenden Ablagezeitraum auswählen."
        }
        val updated = existing.copy(type = type, periodYear = periodYear, periodQuarter = periodQuarter, note = note.trim(), customTypeId = customTypeId)
        if (updated == existing) return
        writeRevision("unterlage", existing.id, existing.toJson())
        val previousDocuments = documents
        documents = documents.map { if (it.id == documentId) updated else it }
        runCatching { persist() }.onFailure { documents = previousDocuments }.getOrThrow()
    }

    private fun validateDocumentCustomType(type: DocumentType, owner: DocumentOwner, customTypeId: String?): CustomDocumentType? {
        if (customTypeId == null) return null
        require(owner == DocumentOwner.GENERAL && type == DocumentType.OTHER) {
            "Eigene Arten gehören zu allgemeinen Einträgen."
        }
        return requireNotNull(customType(customTypeId)) { "Diese eigene Art wurde nicht gefunden." }
    }

    private fun attachDocument(document: StoredDocument) {
        when (document.owner) {
            DocumentOwner.DAILY_CLOSE -> {
                dailyCloses = dailyCloses.map {
                    if (it.id == document.ownerId) it.copy(
                        documentIds = (it.documentIds + document.id).distinct(),
                        updatedAt = LocalDateTime.now(),
                    ) else it
                }
            }
            DocumentOwner.INVOICE -> {
                invoices = invoices.map {
                    if (it.id == document.ownerId) it.copy(
                        documentIds = (it.documentIds + document.id).distinct(),
                        updatedAt = LocalDateTime.now(),
                    ) else it
                }
            }
            DocumentOwner.GENERAL -> Unit
        }
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun serializeState(): String {
        val root = JSONObject()
            .put("schemaVersion", 2)
            .put("dailyCloses", JSONArray().apply { dailyCloses.forEach { put(it.toJson()) } })
            .put("invoices", JSONArray().apply { invoices.forEach { put(it.toJson()) } })
            .put("documents", JSONArray().apply { documents.forEach { put(it.toJson()) } })
            .put("customDocumentTypes", JSONArray().apply { customDocumentTypes.forEach { put(it.toJson()) } })
            .put("reminders", reminderSettings.toJson())
        return root.toString()
    }

    private fun requireReadableState() {
        check(loadError == null) {
            "Die lokalen Daten sind nicht lesbar. Bitte lies erst eine passende Sicherung ein; ein neues Backup könnte sonst Daten überschreiben."
        }
    }

    private fun validateDataRoot(root: JSONObject) {
        root.getJSONArray("dailyCloses")
        root.getJSONArray("invoices")
        root.getJSONArray("documents")
    }

    private fun persist(allowRecovery: Boolean = false) {
        check(allowRecovery || loadError == null) {
            "Gespeicherte Daten konnten nicht gelesen werden. Bitte lies zuerst eine passende Sicherung ein. Die vorhandenen Daten wurden nicht überschrieben."
        }
        val data = serializeState()
        require(preferences.edit().putString("data", data).commit()) {
            "Die Daten konnten nicht dauerhaft gespeichert werden. Es wurde nichts als erfolgreich bestätigt."
        }
        synchronized(BackupSchedule.generationLock) {
            val next = backupPreferences.getLong("changed_generation", 0) + 1
            check(backupPreferences.edit().putLong("changed_generation", next).commit()) {
                "Die Änderung wurde gespeichert, aber der Sicherungsstatus konnte nicht aktualisiert werden."
            }
        }
        hasPendingBackup = true
    }

    private fun writeRevision(entityType: String, entityId: String, previous: JSONObject) {
        val safeType = entityType.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val safeId = entityId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val timestamp = LocalDateTime.now().toString().replace(':', '-')
        val destination = File(
            revisionsDir,
            "${safeType}_${safeId}_${timestamp}_${UUID.randomUUID().toString().take(8)}.json",
        )
        val temporary = File(revisionsDir, ".${destination.name}.tmp")
        temporary.writeText(previous.toString(), Charsets.UTF_8)
        require(temporary.renameTo(destination)) {
            temporary.delete()
            "Die vorherige Version konnte nicht gesichert werden. Die Änderung wurde abgebrochen."
        }
    }

    private fun revisionFiles(): List<File> = revisionsDir.listFiles()?.filter { it.isFile && it.extension == "json" }.orEmpty()

    private fun recordBackupSuccess(createdAt: LocalDateTime, generation: Long) {
        synchronized(BackupSchedule.generationLock) {
            val backedUp = maxOf(generation, backupPreferences.getLong("backed_up_generation", -1))
            check(backupPreferences.edit()
                .putString("last_backup_at", createdAt.toString())
                .putLong("backed_up_generation", backedUp)
                .remove("last_backup_error")
                .commit()) { "Der Sicherungsstatus konnte nicht gespeichert werden." }
        }
        refreshBackupStatus()
    }

    private fun recordBackupFailure(message: String) {
        backupPreferences.edit().putString("last_backup_error", message).commit()
        lastBackupError = message
    }

    private fun load() {
        val raw = preferences.getString("data", null) ?: return
        runCatching {
            val root = JSONObject(raw)
            validateDataRoot(root)
            val loadedCloses = root.optJSONArray("dailyCloses").toObjects(::dailyCloseFromJson)
                .sortedByDescending { it.businessDate }
            val loadedInvoices = root.optJSONArray("invoices").toObjects(::invoiceFromJson)
                .sortedByDescending { it.updatedAt }
            val loadedDocuments = root.optJSONArray("documents").toObjects(::documentFromJson)
                .sortedByDescending { it.createdAt }
            val loadedTypes = root.optJSONArray("customDocumentTypes").toObjects(::customDocumentTypeFromJson)
            val loadedReminders = root.optJSONObject("reminders")?.let(::remindersFromJson)
                ?: ReminderSettings()
            dailyCloses = loadedCloses
            invoices = loadedInvoices
            documents = loadedDocuments
            customDocumentTypes = loadedTypes
            reminderSettings = loadedReminders
        }.onFailure {
            loadError = "Gespeicherte Daten konnten nicht gelesen werden. Bitte lies unter Einstellungen eine passende Sicherung ein. Bis dahin werden die vorhandenen Daten nicht überschrieben."
        }
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun <T> JSONArray?.toObjects(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) add(mapper(getJSONObject(index)))
    }
}

private fun String?.toDate(): LocalDate? = this?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
private fun String?.toDateTime(): LocalDateTime? = this?.takeIf { it.isNotBlank() }?.let(LocalDateTime::parse)
private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)

private fun DailyClose.toJson() = JSONObject()
    .put("id", id)
    .put("businessDate", businessDate.toString())
    .put("zAmountCents", zAmountCents)
    .put("note", note)
    .put("documentIds", JSONArray(documentIds))
    .put("createdAt", createdAt.toString())
    .put("updatedAt", updatedAt.toString())

private fun dailyCloseFromJson(json: JSONObject) = DailyClose(
    id = json.getString("id"),
    businessDate = LocalDate.parse(json.getString("businessDate")),
    zAmountCents = json.getLong("zAmountCents"),
    note = json.optString("note"),
    documentIds = json.optJSONArray("documentIds").strings(),
    createdAt = json.nullableString("createdAt").toDateTime() ?: LocalDateTime.now(),
    updatedAt = json.nullableString("updatedAt").toDateTime() ?: LocalDateTime.now(),
)

private fun InvoiceCase.toJson() = JSONObject()
    .put("id", id)
    .put("supplier", supplier)
    .put("amountCents", amountCents ?: JSONObject.NULL)
    .put("orderDate", orderDate?.toString() ?: JSONObject.NULL)
    .put("invoiceDate", invoiceDate?.toString() ?: JSONObject.NULL)
    .put("invoiceNumber", invoiceNumber)
    .put("deliveryDate", deliveryDate?.toString() ?: JSONObject.NULL)
    .put("dueDate", dueDate?.toString() ?: JSONObject.NULL)
    .put("paidDate", paidDate?.toString() ?: JSONObject.NULL)
    .put("note", note)
    .put("documentIds", JSONArray(documentIds))
    .put("createdAt", createdAt.toString())
    .put("updatedAt", updatedAt.toString())

private fun invoiceFromJson(json: JSONObject) = InvoiceCase(
    id = json.getString("id"),
    supplier = json.getString("supplier"),
    amountCents = json.nullableLong("amountCents"),
    orderDate = json.nullableString("orderDate").toDate(),
    invoiceDate = json.nullableString("invoiceDate").toDate(),
    invoiceNumber = json.optString("invoiceNumber"),
    deliveryDate = json.nullableString("deliveryDate").toDate(),
    dueDate = json.nullableString("dueDate").toDate(),
    paidDate = json.nullableString("paidDate").toDate(),
    note = json.optString("note"),
    documentIds = json.optJSONArray("documentIds").strings(),
    createdAt = json.nullableString("createdAt").toDateTime() ?: LocalDateTime.now(),
    updatedAt = json.nullableString("updatedAt").toDateTime() ?: LocalDateTime.now(),
)

private fun StoredDocument.toJson() = JSONObject()
    .put("id", id)
    .put("type", type.name)
    .put("owner", owner.name)
    .put("ownerId", ownerId ?: JSONObject.NULL)
    .put("periodYear", periodYear ?: JSONObject.NULL)
    .put("periodQuarter", periodQuarter ?: JSONObject.NULL)
    .put("originalFileName", originalFileName)
    .put("storedFileName", storedFileName)
    .put("mimeType", mimeType)
    .put("sha256", sha256)
    .put("note", note)
    .put("createdAt", createdAt.toString())
    .put("customTypeId", customTypeId ?: JSONObject.NULL)

private fun documentFromJson(json: JSONObject) = StoredDocument(
    id = json.getString("id"),
    type = DocumentType.valueOf(json.getString("type")),
    owner = DocumentOwner.valueOf(json.optString("owner", DocumentOwner.GENERAL.name)),
    ownerId = json.nullableString("ownerId"),
    periodYear = if (json.isNull("periodYear")) null else json.optInt("periodYear"),
    periodQuarter = if (json.isNull("periodQuarter")) null else json.optInt("periodQuarter"),
    originalFileName = json.getString("originalFileName"),
    storedFileName = json.getString("storedFileName"),
    mimeType = json.optString("mimeType", "application/octet-stream"),
    sha256 = json.optString("sha256"),
    note = json.optString("note"),
    createdAt = json.nullableString("createdAt").toDateTime() ?: LocalDateTime.now(),
    customTypeId = json.nullableString("customTypeId"),
)

private fun CustomDocumentType.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("usesAnnualPeriod", usesAnnualPeriod)

private fun customDocumentTypeFromJson(json: JSONObject) = CustomDocumentType(
    id = json.getString("id"),
    name = json.getString("name"),
    usesAnnualPeriod = json.optBoolean("usesAnnualPeriod", false),
)

private fun ReminderSettings.toJson() = JSONObject()
    .put("enabledDays", JSONArray(enabledDays.map { it.name }))
    .put("hour", hour)
    .put("minute", minute)
    .put("dueReminderDays", dueReminderDays)

private fun remindersFromJson(json: JSONObject) = ReminderSettings(
    enabledDays = json.optJSONArray("enabledDays").strings().map(DayOfWeek::valueOf).toSet(),
    hour = json.optInt("hour", 19),
    minute = json.optInt("minute", 0),
    dueReminderDays = json.optInt("dueReminderDays", 5),
)

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return buildList { for (index in 0 until length()) add(getString(index)) }
}

