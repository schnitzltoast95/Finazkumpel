package de.quartalsmappe.app

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.export.QuarterExportManifest
import de.quartalsmappe.app.export.QuarterExporter
import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.StoredDocument
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DocumentExportIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val quarter = Quarter(2026, 4)

    @Before @After fun clearTestData() {
        check(context.packageName.endsWith(".uitest")) { "Document tests require the isolated test app." }
        listOf("quartalsmappe_data", "quartalsmappe_backup").forEach {
            check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
        }
        listOf(File(context.filesDir, "documents"), File(context.filesDir, "revisions"),
            File(context.cacheDir, "exports"), File(context.cacheDir, "document-export-tests")).forEach {
            check(!it.exists() || it.deleteRecursively())
        }
    }

    @Test fun changingMetadataPreservesOriginalAndRevisionAndMovesExportPeriod() {
        val store = AppStore(context)
        val original = importGeneral(store)
        val bytes = store.fileFor(original).readBytes()

        store.updateDocumentMetadata(original.id, DocumentType.BANK_STATEMENT, 2026, 3, "Neue Zuordnung")

        val updated = store.documents.single()
        assertEquals(original.originalFileName, updated.originalFileName)
        assertEquals(original.storedFileName, updated.storedFileName)
        assertEquals(original.sha256, updated.sha256)
        assertArrayEquals(bytes, store.fileFor(updated).readBytes())
        assertEquals(1, store.revisionCount("unterlage", original.id))
        assertEquals(updated, AppStore(context).documents.single())
        assertTrue(manifest(store, quarter).files.isEmpty())
        assertEquals(original.id, manifest(store, Quarter(2026, 3)).files.single().document.id)
        store.updateDocumentMetadata(original.id, DocumentType.BANK_STATEMENT, 2026, 3, "Neue Zuordnung")
        assertEquals(1, store.revisionCount("unterlage", original.id))
    }

    @Test fun sourceOwnedDocumentsCannotBeRecategorizedAsGeneralRecords() {
        val store = AppStore(context)
        val close = DailyClose(id = "owned-close", businessDate = quarter.start, zAmountCents = 1200)
        store.saveDailyClose(close)
        val document = store.importDocument(Uri.fromFile(sourcePdf()), DocumentType.Z_REPORT, DocumentOwner.DAILY_CLOSE, close.id, 2026, 4)

        assertTrue(runCatching {
            store.updateDocumentMetadata(document.id, DocumentType.OTHER, 2026, 3, "")
        }.isFailure)
        assertEquals(document, store.documents.single())
        assertEquals(0, store.revisionCount("unterlage", document.id))
    }

    @Test fun zipEntriesExactlyMatchReviewedManifestForEitherInvoiceChoice() {
        val store = AppStore(context)
        importGeneral(store)
        val close = DailyClose(id = "export-close", businessDate = quarter.start, zAmountCents = 1200)
        store.saveDailyClose(close)
        store.importDocument(Uri.fromFile(sourcePdf()), DocumentType.Z_REPORT, DocumentOwner.DAILY_CLOSE, close.id, 2026, 4)
        val invoice = InvoiceCase(id = "export-invoice", supplier = "Test", invoiceDate = quarter.start, amountCents = 3400)
        store.saveInvoice(invoice)
        store.importDocument(Uri.fromFile(sourcePdf()), DocumentType.INVOICE_PAPER, DocumentOwner.INVOICE, invoice.id, 2026, 4)

        listOf(false, true).forEach { includeInvoices ->
            val manifest = manifest(store, quarter, includeInvoices)
            val uri = QuarterExporter.createPackage(context, store, quarter, includeInvoices)
            val entries = readZip(uri)
            assertEquals((manifest.generatedFiles + manifest.files.map { it.archivePath }).toSet(), entries.keys)
            assertTrue(entries.getValue("Tagesumsaetze.csv").toString(Charsets.UTF_8).contains("01.10.2026;12,00;\"\";Ja"))
            assertFalse(entries.values.any { it.isEmpty() })
        }
    }

    @Test fun unavailableOriginalBlocksExportInsteadOfSilentlyDroppingIt() {
        val store = AppStore(context)
        val document = importGeneral(store)
        check(store.fileFor(document).delete())

        assertEquals(document.id, manifest(store, quarter).missingFiles.single().document.id)
        val failure = runCatching { QuarterExporter.createPackage(context, store, quarter, false) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains(document.originalFileName))
        assertTrue(File(context.cacheDir, "exports").listFiles().orEmpty().none { it.extension == "zip" })
    }

    @Test fun matchingBackupRepairsOnlyMissingOriginalWithoutDuplicatingMetadata() {
        val store = AppStore(context)
        val document = importGeneral(store)
        val originalBytes = store.fileFor(document).readBytes()
        val backup = backupFixture(store, document)
        check(store.fileFor(document).delete())

        val result = store.restoreBackup(Uri.fromFile(backup))

        assertEquals(1, result.originalFilesRestored)
        assertEquals(0, result.documentsAdded)
        assertEquals(listOf(document), store.documents)
        assertArrayEquals(originalBytes, store.fileFor(document).readBytes())
        assertTrue(manifest(store, quarter).missingFiles.isEmpty())
        assertEquals(listOf(document), AppStore(context).documents)
    }

    @Test fun backupWithConflictingMetadataCannotRepairOrOverwriteCurrentRecord() {
        val store = AppStore(context)
        val document = importGeneral(store)
        val backup = backupFixture(store, document) { data ->
            data.getJSONArray("documents").getJSONObject(0).put("note", "Konflikt")
        }
        check(store.fileFor(document).delete())

        val result = store.restoreBackup(Uri.fromFile(backup))

        assertEquals(0, result.originalFilesRestored)
        assertFalse(store.fileFor(document).exists())
        assertEquals(listOf(document), store.documents)
        assertTrue(result.skippedConflicts > 0)
    }

    @Test fun backupWithMismatchedChecksumCannotRepairAnOriginal() {
        val store = AppStore(context)
        val document = importGeneral(store)
        val backup = backupFixture(store, document, replacementBytes = "wrong bytes".toByteArray())
        check(store.fileFor(document).delete())

        val result = store.restoreBackup(Uri.fromFile(backup))

        assertEquals(0, result.originalFilesRestored)
        assertFalse(store.fileFor(document).exists())
        assertEquals(listOf(document), store.documents)
    }

    @Test fun backupNeverOverwritesAnExistingOriginal() {
        val store = AppStore(context)
        val document = importGeneral(store)
        val backup = backupFixture(store, document)
        val protectedBytes = "existing original must remain".toByteArray()
        store.fileFor(document).writeBytes(protectedBytes)

        val result = store.restoreBackup(Uri.fromFile(backup))

        assertEquals(0, result.originalFilesRestored)
        assertArrayEquals(protectedBytes, store.fileFor(document).readBytes())
        assertEquals(listOf(document), store.documents)
    }

    private fun importGeneral(store: AppStore): StoredDocument = store.importDocument(
        Uri.fromFile(sourcePdf()), DocumentType.OTHER, DocumentOwner.GENERAL, null, 2026, 4,
    )

    private fun sourcePdf(): File {
        val destination = File(context.cacheDir, "document-export-tests/Original.pdf")
        destination.parentFile?.mkdirs()
        instrumentation.context.assets.open("testdaten/F01_Z-Abschlag_2026-10-12_TEST.pdf").use { input ->
            destination.outputStream().use { input.copyTo(it) }
        }
        return destination
    }

    private fun manifest(store: AppStore, selected: Quarter, includeInvoices: Boolean = false) = QuarterExportManifest.create(
        selected, includeInvoices, store.dailyCloses, store.invoices, store.documents,
        isAvailable = { store.fileFor(it).let { file -> file.isFile && file.canRead() } },
    )

    private fun backupFixture(
        store: AppStore,
        document: StoredDocument,
        replacementBytes: ByteArray? = null,
        mutateData: (JSONObject) -> Unit = {},
    ): File {
        val data = JSONObject(context.getSharedPreferences("quartalsmappe_data", Context.MODE_PRIVATE).getString("data", null)!!)
        mutateData(data)
        val destination = File(context.cacheDir, "document-export-tests/backup.zip")
        ZipOutputStream(destination.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(data.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("documents/${document.storedFileName}"))
            zip.write(replacementBytes ?: store.fileFor(document).readBytes())
            zip.closeEntry()
        }
        return destination
    }

    private fun readZip(uri: Uri): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().also { entries ->
        context.contentResolver.openInputStream(uri).use { input ->
            ZipInputStream(requireNotNull(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        }
    }
}
