package de.quartalsmappe.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.ui.CaptureScreen
import de.quartalsmappe.app.ui.DocumentMetadataEditor
import de.quartalsmappe.app.ui.EntriesScreen
import de.quartalsmappe.app.ui.InvoiceEditorScreen
import de.quartalsmappe.app.ui.InvoiceStartMode
import de.quartalsmappe.app.ui.QuarterScreen
import de.quartalsmappe.app.ui.theme.QuartalsmappeTheme
import de.quartalsmappe.app.time.AppClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CaptureEntriesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val today = LocalDate.of(2026, 10, 14)
    private val clock = AppClock { today }
    private lateinit var store: AppStore

    @Before fun setUp() { clearTestData(); store = AppStore(context) }

    @After fun clearTestData() {
        check(context.packageName.endsWith(".uitest")) { "Capture tests require the isolated test app." }
        listOf("quartalsmappe_data", "quartalsmappe_backup").forEach {
            check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
        }
        listOf(File(context.filesDir, "documents"), File(context.filesDir, "revisions"), File(context.cacheDir, "exports/capture-entry-test")).forEach {
            check(!it.exists() || it.deleteRecursively())
        }
    }

    @Test fun allEntriesIncludesFilelessOrdersAndGroupsInvoiceAttachments() {
        store.saveDailyClose(DailyClose(id = "daily", businessDate = today, zAmountCents = 74000))
        store.saveInvoice(InvoiceCase(id = "order", supplier = "Noch ohne Datei", orderDate = today))
        store.saveInvoice(InvoiceCase(id = "invoice", supplier = "Eine Rechnung", invoiceDate = today, dueDate = today.minusDays(1)))
        importPdf(DocumentType.INVOICE_EMAIL, DocumentOwner.INVOICE, "invoice")
        importPdf(DocumentType.DELIVERY_NOTE, DocumentOwner.INVOICE, "invoice")
        var opened: String? = null
        compose.setContent {
            QuartalsmappeTheme { EntriesScreen(store, clock, onOpenExport = {}, onOpenDaily = {}, onOpenInvoice = { opened = it }, onEditDocument = {}) }
        }
        compose.onNodeWithText("3 Einträge").assertIsDisplayed()
        compose.onNodeWithTag("entries_list").performScrollToNode(hasTestTag("entry_invoice_invoice"))
        compose.onAllNodesWithText("Eine Rechnung").assertCountEquals(1)
        compose.onNodeWithText("Zahlung überfällig").assertIsDisplayed()
        compose.onNodeWithTag("entry_invoice_invoice").performClick()
        compose.runOnIdle { assertEquals("invoice", opened) }
        compose.onNodeWithTag("entries_list").performScrollToNode(hasTestTag("entry_invoice_order"))
        compose.onNodeWithText("Noch ohne Datei").assertIsDisplayed()
    }

    @Test fun savedSuppliersCanBeReusedWithoutCreatingSeparateRecords() {
        store.saveInvoice(InvoiceCase(id = "wax", supplier = "WaxPro GmbH", orderDate = today.minusDays(2)))
        store.saveInvoice(InvoiceCase(id = "beauty", supplier = "Beauty Depot", orderDate = today.minusDays(1)))
        val reloadedStore = AppStore(context)

        compose.setContent {
            QuartalsmappeTheme {
                InvoiceEditorScreen(
                    store = reloadedStore,
                    invoiceId = null,
                    startMode = InvoiceStartMode.ORDER,
                    clock = clock,
                    onDone = {},
                )
            }
        }

        compose.onNodeWithTag("invoice_supplier").performClick()
        compose.onNodeWithText("Beauty Depot").assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithTag("invoice_supplier").assertTextContains("Beauty Depot")
        compose.onNodeWithTag("supplier_suggestion_0").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, reloadedStore.invoices.size)
            assertEquals(2, AppStore(context).invoices.size)
        }
    }

    @Test fun searchFindsCustomTypesAndOpensTheirEntry() {
        val custom = store.addCustomDocumentType("Versicherung", true)
        val document = store.importDocument(sourceUri(), DocumentType.OTHER, DocumentOwner.GENERAL, null, 2026, null, "Haftpflicht", custom.id)
        var opened: String? = null
        compose.setContent {
            QuartalsmappeTheme { EntriesScreen(store, clock, onOpenExport = {}, onOpenDaily = {}, onOpenInvoice = {}, onEditDocument = { opened = it }) }
        }
        compose.onNodeWithTag("entries_search").performTextInput("Versicherung")
        compose.onNodeWithText("1 Eintrag").assertIsDisplayed()
        compose.onNodeWithTag("entries_list").performScrollToNode(hasTestTag("entry_document_${document.id}"))
        compose.onNodeWithTag("entry_document_${document.id}").performClick()
        compose.runOnIdle { assertEquals(document.id, opened) }
    }

    @Test fun captureCreatesOwnTypeAndRoutesToItsForm() {
        var chosenType: DocumentType? = null
        var chosenCustom: String? = null
        compose.setContent {
            QuartalsmappeTheme {
                CaptureScreen(store, onDailyClose = {}, onInvoice = {}, onOrder = {}, onAttachExisting = {}, onDocument = { type, custom -> chosenType = type; chosenCustom = custom })
            }
        }
        compose.onNodeWithTag("capture_list").performScrollToNode(hasTestTag("capture_custom_add"))
        compose.onNodeWithTag("capture_custom_add").performClick()
        // AlertDialog registers a separate native window; the click's Compose frame can finish first.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("custom_type_name").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("custom_type_name").assertIsDisplayed().performTextInput("Versicherung")
        compose.onNodeWithTag("custom_type_save").performClick()
        compose.runOnIdle {
            assertEquals(DocumentType.OTHER, chosenType)
            assertEquals("Versicherung", store.customType(chosenCustom)?.name)
            assertEquals(1, AppStore(context).customDocumentTypes.size)
        }
    }

    @Test fun captureActionsShareTypographyAndAccessibleButtonTargets() {
        verifyCaptureActions(fontScale = 1f)
    }

    @Test fun captureActionsStayReadableAtEnlargedFontScale() {
        verifyCaptureActions(fontScale = 1.3f)
    }

    private fun verifyCaptureActions(fontScale: Float) {
        val custom = store.addCustomDocumentType("Betriebshaftpflicht", true)
        val selected = mutableListOf<String>()
        compose.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale)) {
                QuartalsmappeTheme {
                    CaptureScreen(
                        store,
                        onDailyClose = { selected += "daily" },
                        onInvoice = { selected += "invoice" },
                        onOrder = { selected += "order" },
                        onAttachExisting = { selected += "attach" },
                        onDocument = { type, ownType -> selected += ownType ?: type.name },
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("capture_list").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val actions = listOf(
            Triple("capture_daily", "Tagesabschluss", "daily"),
            Triple("capture_invoice", "Rechnung", "invoice"),
            Triple("capture_order", "Bestellung", "order"),
            Triple("capture_attach", "Zu einem Eintrag ergänzen", "attach"),
            Triple("capture_BANK_STATEMENT", "Kontoauszug", DocumentType.BANK_STATEMENT.name),
            Triple("capture_custom_${custom.id}", custom.name, custom.id),
        )
        var reference: TextLayoutResult? = null
        actions.forEach { (tag, title, _) ->
            compose.onNodeWithTag("capture_list").performScrollToNode(hasTestTag(tag))
            val tile = compose.onNodeWithTag(tag)
            tile.assertIsDisplayed().assertHasClickAction().assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(title, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertTitleFitsActualBounds(title, layout)
            if (reference == null) reference = layout
            assertEquals(reference!!.layoutInput.style.fontSize, layout.layoutInput.style.fontSize)
            assertEquals(reference!!.layoutInput.style.fontWeight, layout.layoutInput.style.fontWeight)
            assertEquals(reference!!.layoutInput.style.lineHeight, layout.layoutInput.style.lineHeight)
            tile.performClick()
        }
        compose.runOnIdle { assertEquals(actions.map { it.third }, selected) }
    }

    private fun assertTitleFitsActualBounds(title: String, layout: TextLayoutResult) {
        // String Text's GetTextLayoutResult recreates MultiParagraph at the parent's maximum
        // width, but reports the narrower, intrinsic-sized rendered node. Its synthetic
        // didOverflowWidth is therefore not a clipping signal. Recreate at that node's actual
        // width and verify every line plus the entire text against the actual allocated height.
        val paragraph = MultiParagraph(
            intrinsics = layout.multiParagraph.intrinsics,
            constraints = Constraints(maxWidth = layout.size.width),
            maxLines = layout.layoutInput.maxLines,
            overflow = layout.layoutInput.overflow,
        )
        val details = "title=$title, size=${layout.size}, constraints=${layout.layoutInput.constraints}, " +
            "semanticsParagraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
            "actualParagraph=${paragraph.width}x${paragraph.height}, " +
            "lines=" + (0 until paragraph.lineCount).joinToString { line ->
                "$line:[${paragraph.getLineLeft(line)},${paragraph.getLineRight(line)}]"
            }
        assertFalse("No title lines may be discarded: $details", paragraph.didExceedMaxLines)
        assertTrue("The full title height must fit: $details", paragraph.height <= layout.size.height)
        assertEquals("The entire title must be present: $details", title.length, paragraph.getLineEnd(paragraph.lineCount - 1))
        repeat(paragraph.lineCount) { line ->
            assertFalse("No title line may be ellipsized: $details", paragraph.isLineEllipsized(line))
            assertTrue("Title line $line must start within the node: $details", paragraph.getLineLeft(line) >= 0f)
            assertTrue("Title line $line must end within the node: $details", paragraph.getLineRight(line) <= layout.size.width)
        }
    }

    @Test fun originalFileButtonLaunchesTheRealStoredPdf() {
        val document = importPdf(DocumentType.BANK_STATEMENT)
        val recordingContext = object : ContextWrapper(context) {
            var launched: Intent? = null
            override fun startActivity(intent: Intent) { launched = intent }
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides recordingContext) {
                QuartalsmappeTheme { DocumentMetadataEditor(store, document.id, clock = clock, onDone = {}) }
            }
        }
        compose.onNodeWithTag("open_original_${document.id}").assertIsEnabled().assertHasClickAction().performClick()
        compose.runOnIdle {
            assertNotNull(recordingContext.launched)
            @Suppress("DEPRECATION")
            val view = recordingContext.launched!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_VIEW, view.action)
            assertEquals("application/pdf", view.type)
            assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(store.fileFor(document).length(), context.contentResolver.openInputStream(view.data!!)!!.use { it.readBytes().size.toLong() })
        }
    }

    @Test fun missingExportActionCarriesTheSelectedQuarter() {
        var selected: Pair<DocumentType, Quarter>? = null
        compose.setContent {
            QuartalsmappeTheme {
                QuarterScreen(store, clock, initialQuarter = Quarter(2026, 3), onOpenDaily = {}, onOpenInvoice = {}, onEditDocument = {}, onAddDocument = { type, quarter -> selected = type to quarter })
            }
        }
        // Await the initial native hierarchy, not merely the Compose clock becoming idle.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("export_add_bank").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("export_add_bank").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(DocumentType.BANK_STATEMENT to Quarter(2026, 3), selected) }
    }

    private fun sourcePdf(): File = File(context.cacheDir, "exports/capture-entry-test/Test.pdf").apply {
        parentFile!!.mkdirs()
        instrumentation.context.assets.open("testdaten/F15_Kontoauszug_2026_Q4_TEST.pdf").use { input -> outputStream().use { input.copyTo(it) } }
    }

    private fun sourceUri(): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", sourcePdf())

    private fun importPdf(type: DocumentType, owner: DocumentOwner = DocumentOwner.GENERAL, ownerId: String? = null) =
        store.importDocument(sourceUri(), type, owner, ownerId, 2026, 4)
}
