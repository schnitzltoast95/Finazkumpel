package de.quartalsmappe.app

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.export.QuarterExporter
import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.Quarter
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Integration tests for the local persistence, real test documents and the quarter export.
 * These tests run on an emulator/device but bypass Android's system document picker so they
 * remain deterministic and unattended.
 */
@RunWith(AndroidJUnit4::class)
class AppStoreIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Before
    fun clearStateBeforeTest() = clearAppState()

    @After
    fun clearStateAfterTest() = clearAppState()

    @Test
    fun dailyCloseAndInvoice_surviveStoreReload() {
        val store = AppStore(context)
        val close = DailyClose(
            id = "close-persistence-test",
            businessDate = LocalDate.of(2026, 10, 12),
            zAmountCents = 74_200,
            note = "Testtag mit realistischem Studio-Umsatz",
        )
        val invoice = InvoiceCase(
            id = "invoice-persistence-test",
            supplier = "WaxPro Testlieferant",
            amountCents = 18_445,
            invoiceDate = LocalDate.of(2026, 10, 8),
            invoiceNumber = "WP-2026-1042",
            dueDate = LocalDate.of(2026, 10, 20),
        )

        store.saveDailyClose(close)
        store.saveInvoice(invoice)

        val reloaded = AppStore(context)
        assertEquals(close.businessDate, reloaded.dailyCloses.single().businessDate)
        assertEquals(74_200, reloaded.dailyCloses.single().zAmountCents)
        assertEquals(invoice.supplier, reloaded.invoices.single().supplier)
        assertEquals("WP-2026-1042", reloaded.invoices.single().invoiceNumber)
    }

    @Test
    fun unreadableLocalDataIsProtectedUntilAValidBackupIsRestored() {
        val store = AppStore(context)
        val close = DailyClose(id = "recovery-close", businessDate = LocalDate.of(2026, 10, 12), zAmountCents = 1234)
        store.saveDailyClose(close)
        val preferences = context.getSharedPreferences(DATA_PREFERENCES, Context.MODE_PRIVATE)
        val validData = preferences.getString("data", null)!!
        val backup = File(context.cacheDir, "recovery-test.zip")
        ZipOutputStream(backup.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(validData.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val unreadableData = "{broken"
        check(preferences.edit().putString("data", unreadableData).commit())

        val damaged = AppStore(context)
        assertTrue(damaged.loadError != null)
        assertTrue(runCatching { damaged.saveDailyClose(close) }.isFailure)
        assertEquals(unreadableData, preferences.getString("data", null))

        damaged.restoreBackup(Uri.fromFile(backup))
        assertEquals(null, damaged.loadError)
        assertEquals(close.businessDate, damaged.dailyCloses.single().businessDate)
        assertEquals(1234L, damaged.dailyCloses.single().zAmountCents)
    }

    @Test
    fun restoreRecoversRevisionFilesWithoutReplacingCurrentRecords() {
        val store = AppStore(context)
        val date = LocalDate.of(2026, 10, 12)
        store.saveDailyClose(DailyClose(id = "history-close", businessDate = date, zAmountCents = 1000))
        store.saveDailyClose(DailyClose(id = "history-close", businessDate = date, zAmountCents = 2000))
        val preferences = context.getSharedPreferences(DATA_PREFERENCES, Context.MODE_PRIVATE)
        val data = preferences.getString("data", null)!!
        val revisionDirectory = File(context.filesDir, "revisions")
        val revision = revisionDirectory.listFiles()!!.single()
        val backup = File(context.cacheDir, "revision-recovery-test.zip")
        ZipOutputStream(backup.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(data.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("revisions/${revision.name}"))
            zip.write(revision.readBytes())
            zip.closeEntry()
        }
        check(revision.delete())

        store.restoreBackup(Uri.fromFile(backup))

        assertEquals(1, store.revisionCount("tagesabschluss", "history-close"))
        assertEquals(2000L, store.dailyCloses.single().zAmountCents)
    }

    @Test
    fun realPdfImport_isCopiedHashedAttachedAndReloaded() {
        val store = AppStore(context)
        val close = DailyClose(
            id = "close-document-test",
            businessDate = LocalDate.of(2026, 10, 12),
            zAmountCents = 74_200,
        )
        store.saveDailyClose(close)
        val source = copyAssetToCache("F01_Z-Abschlag_2026-10-12_TEST.pdf")

        val document = store.importDocument(
            source = Uri.fromFile(source),
            type = DocumentType.Z_REPORT,
            owner = DocumentOwner.DAILY_CLOSE,
            ownerId = close.id,
            periodYear = 2026,
            periodQuarter = 4,
            note = "Automatisierter Testimport",
        )

        val storedFile = store.fileFor(document)
        assertTrue(storedFile.exists())
        assertArrayEquals(source.readBytes(), storedFile.readBytes())
        assertEquals(source.sha256(), document.sha256)
        assertTrue(store.dailyCloses.single().documentIds.contains(document.id))

        val reloaded = AppStore(context)
        assertEquals(document.id, reloaded.documents.single().id)
        assertTrue(reloaded.dailyCloses.single().documentIds.contains(document.id))
    }

    @Test
    fun quarterPackage_containsOverviewCsvAndRelevantDocuments() {
        val store = AppStore(context)
        val close = DailyClose(
            id = "close-export-test",
            businessDate = LocalDate.of(2026, 10, 12),
            zAmountCents = 74_200,
            note = "Waxingstudio Testtag",
        )
        store.saveDailyClose(close)
        val zReport = copyAssetToCache("F01_Z-Abschlag_2026-10-12_TEST.pdf")
        store.importDocument(
            source = Uri.fromFile(zReport),
            type = DocumentType.Z_REPORT,
            owner = DocumentOwner.DAILY_CLOSE,
            ownerId = close.id,
            periodYear = 2026,
            periodQuarter = 4,
        )
        val invoicePdf = copyAssetToCache("F08_Rechnung_Hygienebedarf_bald-faellig_TEST.pdf")
        store.importDocument(
            source = Uri.fromFile(invoicePdf),
            type = DocumentType.OTHER,
            owner = DocumentOwner.GENERAL,
            ownerId = null,
            periodYear = 2026,
            periodQuarter = 4,
        )

        val packageUri = QuarterExporter.createPackage(
            context = context,
            store = store,
            quarter = Quarter(2026, 4),
            includeInvoices = true,
        )
        val entries = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(packageUri).use { input ->
            requireNotNull(input) { "Export package could not be opened." }
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        }

        assertTrue(entries.containsKey("Uebersicht.pdf"))
        assertTrue(entries.containsKey("Tagesumsaetze.csv"))
        assertTrue(entries.keys.any { it.startsWith("Z-Abschlaege/") })
        assertTrue(entries.keys.any { it.startsWith("Weitere_Unterlagen/") })
        val csv = entries.getValue("Tagesumsaetze.csv").toString(Charsets.UTF_8)
        assertTrue(csv.contains("12.10.2026;742,00"))
        assertTrue(csv.contains("Waxingstudio Testtag"))
        assertFalse(entries.values.any(ByteArray::isEmpty))
    }

    private fun copyAssetToCache(fileName: String): File {
        val destination = File(context.cacheDir, "instrumentation-assets/$fileName")
        destination.parentFile?.mkdirs()
        instrumentation.context.assets.open("testdaten/$fileName").use { input ->
            destination.outputStream().use(input::copyTo)
        }
        return destination
    }

    private fun clearAppState() {
        check(context.packageName.endsWith(".uitest")) { "Integration tests require the isolated test app." }
        check(
            context.getSharedPreferences(DATA_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        )
        check(
            context.getSharedPreferences(BACKUP_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        )
        listOf("documents", "revisions").forEach { name ->
            val directory = File(context.filesDir, name)
            check(!directory.exists() || directory.deleteRecursively())
        }
        listOf("exports", "instrumentation-assets").forEach { name ->
            val directory = File(context.cacheDir, name)
            check(!directory.exists() || directory.deleteRecursively())
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DATA_PREFERENCES = "quartalsmappe_data"
        const val BACKUP_PREFERENCES = "quartalsmappe_backup"
    }
}
