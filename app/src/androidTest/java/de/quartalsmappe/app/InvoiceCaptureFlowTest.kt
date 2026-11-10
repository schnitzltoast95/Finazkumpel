package de.quartalsmappe.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.time.AppClock
import de.quartalsmappe.app.ui.InvoiceDetailScreen
import de.quartalsmappe.app.ui.InvoiceEditorScreen
import de.quartalsmappe.app.ui.InvoiceSelectionScreen
import de.quartalsmappe.app.ui.InvoiceStartMode
import de.quartalsmappe.app.ui.theme.QuartalsmappeTheme
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Every store and file is namespaced; no real financial records or external viewer are modified. */
@RunWith(AndroidJUnit4::class)
class InvoiceCaptureFlowTest {
    @get:Rule val composeRule = createComposeRule()
    private val today = LocalDate.of(2026, 10, 14)
    private val clock = AppClock { today }
    private lateinit var context: InvoiceFlowTestContext
    private lateinit var store: AppStore

    @Before fun setUp() {
        context = InvoiceFlowTestContext(InstrumentationRegistry.getInstrumentation().targetContext)
        store = AppStore(context)
    }

    @After fun cleanUp() { context.clearTestFiles() }

    @Test fun selectingExistingCaseDoesNotCreateOrChangeAnEntry() {
        store.saveInvoice(InvoiceCase(id = "order-1", supplier = "Papierbedarf Nord", orderDate = today.minusDays(2)))
        store.saveInvoice(InvoiceCase(id = "invoice-2", supplier = "Farbwerk", invoiceDate = today, invoiceNumber = "AB-123"))
        val before = store.invoices
        var selected: String? = null
        composeRule.setContent {
            QuartalsmappeTheme {
                InvoiceSelectionScreen(store, clock, onSelect = { selected = it }, onNewInvoice = {})
            }
        }
        composeRule.onNodeWithText("Lieferant oder Rechnungsnummer suchen").performTextReplacement("AB-123")
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Farbwerk").performClick()
        composeRule.runOnIdle {
            assertEquals("invoice-2", selected)
            assertEquals(before, store.invoices)
        }
    }

    @Test fun newInvoiceProminentlyOffersAnExistingEntryWithoutSavingDraft() {
        var existingRequests = 0
        composeRule.setContent {
            QuartalsmappeTheme {
                InvoiceEditorScreen(
                    store, null, InvoiceStartMode.INVOICE, clock,
                    onChooseExisting = { existingRequests++ }, onDone = {},
                )
            }
        }
        composeRule.onNodeWithText("Zu vorhandener Bestellung hinzufügen").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, existingRequests)
            assertTrue(store.invoices.isEmpty())
        }
    }

    @Test fun orderInvoiceCompletionSurvivesRecreationAndPreservesItsOriginals() {
        store.saveInvoice(
            InvoiceCase(id = "existing-order", supplier = "Testlieferant", orderDate = today.minusDays(8), amountCents = 12_300, note = "Bestellnotiz")
        )
        val attachment = store.importDocument(
            Uri.fromFile(sourcePdf("lieferschein.pdf")), DocumentType.DELIVERY_NOTE,
            DocumentOwner.INVOICE, "existing-order", 2026, 4,
        )
        val before = store.invoices.single()
        val original = store.fileFor(attachment).readBytes()
        var savedId: String? = null
        var dirty = false
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            QuartalsmappeTheme {
                InvoiceEditorScreen(
                    store, before.id, InvoiceStartMode.INVOICE, clock,
                    onDirtyChange = { dirty = it }, onDone = { savedId = it },
                )
            }
        }
        composeRule.onNodeWithTag("invoice_editor").performScrollToNode(hasText("Rechnungsnummer (optional)"))
        composeRule.onNodeWithText("Rechnungsnummer (optional)").performTextReplacement("NEU-42")
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertTrue(dirty)
            assertEquals(before, store.invoices.single())
        }
        composeRule.onNodeWithTag("invoice_editor").performScrollToNode(hasText("Änderungen speichern"))
        composeRule.onNodeWithText("Änderungen speichern").performClick()
        composeRule.onNodeWithText("Änderung speichern").performClick()
        composeRule.runOnIdle {
            val saved = store.invoices.single()
            assertEquals(before.id, savedId)
            assertEquals(before.id, saved.id)
            assertEquals(before.orderDate, saved.orderDate)
            assertEquals(today, saved.invoiceDate)
            assertEquals("NEU-42", saved.invoiceNumber)
            assertEquals(before.createdAt, saved.createdAt)
            assertEquals(before.note, saved.note)
            assertEquals(before.documentIds, saved.documentIds)
            assertEquals(1, store.revisionCount("rechnung", before.id))
            assertArrayEquals(original, store.fileFor(attachment).readBytes())
            assertEquals(saved, AppStore(context).invoices.single())
        }
    }

    @Test fun ordinaryOrderEditDoesNotInventAnInvoiceDate() {
        store.saveInvoice(InvoiceCase(id = "order-only", supplier = "Nur bestellt", orderDate = today.minusDays(1)))
        var dirty = true
        composeRule.setContent {
            QuartalsmappeTheme {
                InvoiceEditorScreen(store, "order-only", InvoiceStartMode.ORDER, clock, onDirtyChange = { dirty = it }, onDone = {})
            }
        }
        composeRule.runOnIdle { assertEquals(false, dirty) }
        composeRule.onNodeWithTag("invoice_editor").performScrollToNode(hasText("Änderungen speichern"))
        composeRule.onNodeWithText("Änderungen speichern").performClick()
        composeRule.onNodeWithText("Änderung speichern").performClick()
        composeRule.runOnIdle { assertNull(store.invoices.single().invoiceDate) }
    }

    @Test fun allInvoiceOriginalsAreGroupedAndOpenWithReadPermission() {
        store.saveInvoice(InvoiceCase(id = "order-with-files", supplier = "Dateien Test", orderDate = today))
        val attachments = listOf(DocumentType.INVOICE_EMAIL, DocumentType.INVOICE_PAPER, DocumentType.DELIVERY_NOTE)
            .mapIndexed { index, type ->
                store.importDocument(Uri.fromFile(sourcePdf("beleg-$index.pdf")), type, DocumentOwner.INVOICE, "order-with-files", 2026, 4)
            }
        var addInvoiceRequests = 0
        composeRule.setContent {
            // Keep the real Compose host's picker registry while intercepting only viewer intents.
            val registryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides registryOwner,
            ) {
                QuartalsmappeTheme {
                    InvoiceDetailScreen(store, "order-with-files", clock, onEdit = {}, onAddInvoice = { addInvoiceRequests++ }, onBack = {})
                }
            }
        }
        composeRule.onNodeWithText("Rechnung zur Bestellung ergänzen").performClick()
        attachments.forEach { document ->
            val tag = "invoice_attachment_${document.id}"
            composeRule.onNodeWithTag("invoice_detail").performScrollToNode(hasTestTag(tag))
            composeRule.runOnIdle { context.lastStartedIntent = null }
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.runOnIdle {
                val chooser = context.lastStartedIntent
                assertNotNull(chooser)
                assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)
                @Suppress("DEPRECATION")
                val view = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_VIEW, view.action)
                assertEquals(document.mimeType, view.type)
                assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertArrayEquals(store.fileFor(document).readBytes(), context.contentResolver.openInputStream(view.data!!)!!.use { it.readBytes() })
            }
        }
        composeRule.runOnIdle {
            assertEquals(1, addInvoiceRequests)
            assertEquals(1, store.invoices.size)
            assertEquals(attachments.map { it.id }.toSet(), store.invoices.single().documentIds.toSet())
        }
    }

    private fun sourcePdf(name: String): File = File(context.filesDir, name).apply {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("testdaten/F08_Rechnung_Hygienebedarf_bald-faellig_TEST.pdf").use { source ->
                outputStream().use { target -> source.copyTo(target) }
            }
    }
}

private class InvoiceFlowTestContext(base: Context) : ContextWrapper(base) {
    private val prefix = "invoice_flow_${UUID.randomUUID()}_"
    private val preferenceNames = mutableSetOf<String>()
    private val testFiles = File(base.cacheDir, "exports/$prefix").apply { mkdirs() }
    var lastStartedIntent: Intent? = null

    override fun getFilesDir(): File = testFiles
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val scoped = prefix + name
        preferenceNames += scoped
        return super.getSharedPreferences(scoped, mode)
    }
    override fun startActivity(intent: Intent) { lastStartedIntent = intent }

    fun clearTestFiles() {
        preferenceNames.forEach { super.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        check(testFiles.deleteRecursively()) { "Isolated invoice test files could not be removed." }
    }
}
