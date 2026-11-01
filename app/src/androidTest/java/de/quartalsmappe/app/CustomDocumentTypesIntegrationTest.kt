package de.quartalsmappe.app

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.export.QuarterExportManifest
import de.quartalsmappe.app.export.QuarterExporter
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.StoredDocument
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class CustomDocumentTypesIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val quarter = Quarter(2026, 4)

    @Before @After fun clearTestData() {
        check(context.packageName.endsWith(".uitest")) { "Custom type tests require the isolated test app." }
        clearPersistentData()
        listOf(File(context.cacheDir, "exports"), File(context.cacheDir, "custom-type-tests")).forEach {
            check(!it.exists() || it.deleteRecursively())
        }
    }

    @Test fun customTypeAndDocumentSurviveReloadAndMetadataChanges() {
        val store = AppStore(context)
        val custom = store.addCustomDocumentType("  Versicherung  ", true)
        val document = importDocument(store, custom.id, null)
        val bytes = store.fileFor(document).readBytes()
        val reloaded = AppStore(context)
        assertEquals(listOf(custom), reloaded.customDocumentTypes)
        assertEquals(document, reloaded.documents.single())
        assertEquals("Versicherung", reloaded.documentTypeLabel(document))
        assertEquals(custom, reloaded.customType(custom.id))
        assertNull(reloaded.customType(null))

        reloaded.updateDocumentMetadata(document.id, DocumentType.OTHER, 2027, null, "Jahresvertrag", custom.id)
        val updated = reloaded.documents.single()
        assertEquals(custom.id, updated.customTypeId)
        assertEquals(document.sha256, updated.sha256)
        assertEquals(document.storedFileName, updated.storedFileName)
        assertArrayEquals(bytes, reloaded.fileFor(updated).readBytes())
        assertEquals(1, reloaded.revisionCount("unterlage", document.id))
        assertEquals(updated, AppStore(context).documents.single())
        assertTrue(manifest(reloaded).files.isEmpty())
    }

    @Test fun legacyStateWithoutCustomFieldsLoadsUnchangedAndCanBeExtended() {
        val store = AppStore(context)
        val legacy = importDocument(store)
        val data = persistedJson()
        data.put("schemaVersion", 1).remove("customDocumentTypes")
        data.getJSONArray("documents").getJSONObject(0).remove("customTypeId")
        writePersistentJson(data)

        val reloaded = AppStore(context)
        assertEquals(listOf(legacy), reloaded.documents)
        assertTrue(reloaded.customDocumentTypes.isEmpty())
        assertEquals("Sonstiges", reloaded.documentTypeLabel(legacy))
        reloaded.addCustomDocumentType("Weiterbildung")
        assertEquals(listOf(legacy), AppStore(context).documents)
        assertEquals("Weiterbildung", AppStore(context).customDocumentTypes.single().name)
    }

    @Test fun customValidationRejectsBadNamesMissingKindsAndWrongPeriodsWithoutImportingFiles() {
        val store = AppStore(context)
        val custom = store.addCustomDocumentType("Versicherung", true)
        val originalFiles = File(context.filesDir, "documents").listFiles().orEmpty().size
        listOf("", "Kontoauszug", " VERSICHERUNG ", "x".repeat(81)).forEach { name ->
            assertTrue(runCatching { store.addCustomDocumentType(name) }.isFailure)
        }
        assertTrue(runCatching { importDocument(store, custom.id, 4) }.isFailure)
        assertTrue(runCatching { importDocument(store, "missing", null) }.isFailure)
        assertTrue(runCatching {
            store.importDocument(Uri.fromFile(sourcePdf()), DocumentType.BANK_STATEMENT,
                DocumentOwner.GENERAL, null, 2026, 4, customTypeId = custom.id)
        }.isFailure)
        assertTrue(runCatching {
            store.importDocument(Uri.fromFile(sourcePdf()), DocumentType.OTHER,
                DocumentOwner.INVOICE, "invoice", 2026, null, customTypeId = custom.id)
        }.isFailure)
        assertEquals(listOf(custom), store.customDocumentTypes)
        assertTrue(store.documents.isEmpty())
        assertEquals(originalFiles, File(context.filesDir, "documents").listFiles().orEmpty().size)
    }

    @Test fun backupRestoresCustomDefinitionsReferencesAndOriginalsAndIsIdempotent() {
        val sourceStore = AppStore(context)
        val custom = sourceStore.addCustomDocumentType("Versicherung", true)
        val document = importDocument(sourceStore, custom.id, null)
        val originalBytes = sourceStore.fileFor(document).readBytes()
        val backup = backupFixture(sourceStore)
        clearPersistentData()
        val restored = AppStore(context)

        val result = restored.restoreBackup(Uri.fromFile(backup))
        assertEquals(1, result.customTypesAdded)
        assertEquals(1, result.documentsAdded)
        assertEquals(listOf(custom), restored.customDocumentTypes)
        assertEquals(listOf(document), restored.documents)
        assertArrayEquals(originalBytes, restored.fileFor(document).readBytes())
        val second = restored.restoreBackup(Uri.fromFile(backup))
        assertEquals(0, second.customTypesAdded)
        assertEquals(0, second.documentsAdded)
        assertEquals(listOf(document), AppStore(context).documents)
    }

    @Test fun restoreRemapsConflictingCategoryIdWithoutChangingExistingCategoryOrDocument() {
        val store = AppStore(context)
        val local = store.addCustomDocumentType("Versicherung", true)
        val localDocument = importDocument(store, local.id, null)
        val localBytes = store.fileFor(localDocument).readBytes()
        val backup = backupFixture(store, mutate = { data ->
            data.getJSONArray("customDocumentTypes").getJSONObject(0).put("name", "Weiterbildung")
            data.getJSONArray("documents").getJSONObject(0)
                .put("id", "restored-custom-document")
                .put("storedFileName", "restored-original.pdf")
        })

        val result = store.restoreBackup(Uri.fromFile(backup))
        assertEquals(1, result.customTypesAdded)
        assertEquals(1, result.documentsAdded)
        assertEquals(local, store.customType(local.id))
        assertEquals(localDocument, store.documents.first { it.id == localDocument.id })
        assertArrayEquals(localBytes, store.fileFor(localDocument).readBytes())
        val restored = store.documents.first { it.id == "restored-custom-document" }
        assertNotEquals(local.id, restored.customTypeId)
        assertEquals("Weiterbildung", store.documentTypeLabel(restored))
        assertArrayEquals(localBytes, store.fileFor(restored).readBytes())
        store.restoreBackup(Uri.fromFile(backup))
        assertEquals(2, store.customDocumentTypes.size)
        assertEquals(2, store.documents.size)
    }

    @Test fun customAnnualAndQuarterlyOriginalsAreIncludedInTheActualZipWithSafePaths() {
        val store = AppStore(context)
        val annual = store.addCustomDocumentType("Versicherung", true)
        val quarterly = store.addCustomDocumentType("../Weiterbildung")
        importDocument(store, annual.id, null)
        importDocument(store, quarterly.id, 4)
        val review = manifest(store)
        assertEquals(2, review.files.size)
        assertTrue(review.generalDocumentOverviewLines(store::documentTypeLabel).contains("Versicherung"))
        assertTrue(review.generalDocumentOverviewLines(store::documentTypeLabel).contains("../Weiterbildung"))
        val uri = QuarterExporter.createPackage(context, store, quarter, false)
        val entries = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri).use { input ->
            ZipInputStream(requireNotNull(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        }
        assertEquals((review.generatedFiles + review.files.map { it.archivePath }).toSet(), entries.keys)
        assertTrue(review.files.all { it.archivePath.startsWith("Weitere_Unterlagen/") })
        assertFalse(entries.keys.any { it.contains("../") || it.contains("Weiterbildung") })
        assertTrue(entries.values.all { it.isNotEmpty() })
    }

    @Test fun restoreRejectsNewOriginalWithChangedChecksum() {
        val sourceStore = AppStore(context)
        val custom = sourceStore.addCustomDocumentType("Versicherung", true)
        importDocument(sourceStore, custom.id, null)
        val backup = backupFixture(sourceStore) { data ->
            data.getJSONArray("documents").getJSONObject(0).put("sha256", "wrong-checksum")
        }
        clearPersistentData()
        val restored = AppStore(context)
        val result = restored.restoreBackup(Uri.fromFile(backup))
        assertEquals(0, result.documentsAdded)
        assertEquals(1, result.skippedConflicts)
        assertTrue(restored.documents.isEmpty())
        assertTrue(File(context.filesDir, "documents").listFiles().orEmpty().isEmpty())
        assertEquals(listOf(custom), restored.customDocumentTypes)
    }

    @Test fun restoreRejectsNewOriginalOutsideTheDocumentsFolder() {
        val sourceStore = AppStore(context)
        importDocument(sourceStore)
        val backup = backupFixture(sourceStore) { data ->
            data.getJSONArray("documents").getJSONObject(0).put("storedFileName", "../outside.pdf")
        }
        clearPersistentData()
        val restored = AppStore(context)
        val protectedFile = File(context.filesDir, "outside.pdf")
        val protectedBytes = "Existing file must not be overwritten".toByteArray()
        protectedFile.writeBytes(protectedBytes)
        try {
            val outcome = runCatching { restored.restoreBackup(Uri.fromFile(backup)) }
            if (outcome.isSuccess) {
                // Older Android versions allow ZIP traversal components; AppStore still rejects them.
                assertEquals(0, outcome.getOrThrow().documentsAdded)
                assertEquals(1, outcome.getOrThrow().skippedConflicts)
            } else {
                // Android 14+ rejects the malicious entry before AppStore sees the extracted metadata.
                val failure = outcome.exceptionOrNull()
                assertTrue(failure is ZipException)
                assertTrue(failure?.message.orEmpty().contains("Invalid zip entry path"))
            }
            assertTrue(restored.documents.isEmpty())
            assertTrue(AppStore(context).documents.isEmpty())
            assertTrue(File(context.filesDir, "documents").listFiles().orEmpty().isEmpty())
            assertArrayEquals(protectedBytes, protectedFile.readBytes())
        } finally {
            check(protectedFile.delete())
        }
    }

    private fun importDocument(store: AppStore, customId: String? = null, number: Int? = 4): StoredDocument =
        store.importDocument(Uri.fromFile(sourcePdf()), DocumentType.OTHER, DocumentOwner.GENERAL,
            null, 2026, number, customTypeId = customId)

    private fun manifest(store: AppStore) = QuarterExportManifest.create(
        quarter, false, store.dailyCloses, store.invoices, store.documents,
        isAvailable = { store.fileFor(it).isFile },
    )

    private fun sourcePdf(): File {
        val destination = File(context.cacheDir, "custom-type-tests/Original.pdf")
        destination.parentFile?.mkdirs()
        instrumentation.context.assets.open("testdaten/F01_Z-Abschlag_2026-10-12_TEST.pdf").use { input ->
            destination.outputStream().use(input::copyTo)
        }
        return destination
    }

    private fun persistedJson() = JSONObject(context.getSharedPreferences("quartalsmappe_data", Context.MODE_PRIVATE)
        .getString("data", null)!!)

    private fun writePersistentJson(data: JSONObject) {
        check(context.getSharedPreferences("quartalsmappe_data", Context.MODE_PRIVATE)
            .edit().putString("data", data.toString()).commit())
    }

    private fun backupFixture(store: AppStore, mutate: (JSONObject) -> Unit = {}): File {
        val data = persistedJson()
        mutate(data)
        val destination = File(context.cacheDir, "custom-type-tests/backup.zip")
        ZipOutputStream(destination.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(data.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            store.documents.forEachIndexed { index, document ->
                val storedName = data.getJSONArray("documents").getJSONObject(index).getString("storedFileName")
                zip.putNextEntry(ZipEntry("documents/$storedName"))
                zip.write(store.fileFor(document).readBytes())
                zip.closeEntry()
            }
        }
        return destination
    }

    private fun clearPersistentData() {
        check(context.packageName.endsWith(".uitest"))
        listOf("quartalsmappe_data", "quartalsmappe_backup").forEach {
            check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
        }
        listOf("documents", "revisions").forEach { name ->
            val directory = File(context.filesDir, name)
            check(!directory.exists() || directory.deleteRecursively())
        }
    }
}
