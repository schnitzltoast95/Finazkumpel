package de.quartalsmappe.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.AppearanceSettings
import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.Quarter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Opt-in screenshot fixture; never runs as part of the ordinary instrumentation suite. */
@RunWith(AndroidJUnit4::class)
class VisualFixtureTest {
    @Test
    fun seedIsolatedScreenshotData() {
        assumeTrue(
            "Screenshot fixtures require the explicit -e visual_seed true argument.",
            InstrumentationRegistry.getArguments().getString("visual_seed") == "true",
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // AppStore creates directories, so this guard must precede even its construction.
        check(context.packageName == "de.quartalsmappe.app.uitest") {
            "Visual fixtures may only mutate the isolated .uitest application."
        }
        check(context.getSharedPreferences("quartalsmappe_backup", 0).getString("folder_uri", null) == null) {
            "Visual fixtures must not write to a configured backup folder."
        }
        val store = AppStore(context)
        check(store.dailyCloses.isEmpty() && store.invoices.isEmpty() && store.documents.isEmpty()) {
            "Seed only a fresh isolated test store; existing test data has not been removed."
        }

        val today = LocalDate.now()
        val quarter = Quarter.current(today)
        val todayClose = DailyClose(
            id = "visual-TEST-close-today",
            businessDate = today,
            zAmountCents = 74_200,
            note = "TEST · Synthetischer Tagesumsatz für die Bildschirmprüfung; kein echter Geschäftsvorfall.",
        )
        val previousClose = DailyClose(
            id = "visual-TEST-close-yesterday",
            businessDate = today.minusDays(1),
            zAmountCents = 68_000,
            note = "TEST · Synthetischer Vortag ohne Z-Beleg für die Bildschirmprüfung.",
        )
        val expense = InvoiceCase(
            id = "visual-TEST-expense-hygiene",
            supplier = "Teststudio · Hygienebedarf",
            amountCents = 18_445,
            orderDate = today.minusDays(4),
            invoiceDate = today.minusDays(2),
            invoiceNumber = "TEST-VISUAL-0021",
            deliveryDate = today.minusDays(1),
            dueDate = today.minusDays(1),
            note = "TEST · Synthetische Ausgabe für die Bildschirmprüfung; keine echte Rechnung.",
        )
        store.saveAppearanceSettings(AppearanceSettings())
        store.saveDailyClose(todayClose)
        store.saveDailyClose(previousClose)
        store.saveInvoice(expense)

        fun importTestPdf(
            assetName: String,
            type: DocumentType,
            owner: DocumentOwner,
            ownerId: String?,
            fixtureFileName: String = assetName,
        ) {
            val source = File(context.cacheDir, "exports/visual-fixture-assets/$fixtureFileName")
            check(source.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
            instrumentation.context.assets.open("testdaten/$assetName").use { input ->
                source.outputStream().use(input::copyTo)
            }
            val document = store.importDocument(
                source = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", source),
                type = type,
                owner = owner,
                ownerId = ownerId,
                periodYear = quarter.year,
                periodQuarter = if (type.usesAnnualPeriod) null else quarter.number,
                note = "TEST · Synthetischer PDF-Musterbeleg für Screenshots; Datumsangaben im Muster sind keine echten Geschäftsdaten.",
            )
            assertTrue("Fixture PDF must be copied into the app store.", store.fileFor(document).isFile)
            assertTrue("Fixture document names must be visibly marked TEST.", document.originalFileName.contains("TEST"))
        }

        importTestPdf("F01_Z-Abschlag_2026-10-12_TEST.pdf", DocumentType.Z_REPORT, DocumentOwner.DAILY_CLOSE, todayClose.id)
        importTestPdf("F08_Rechnung_Hygienebedarf_bald-faellig_TEST.pdf", DocumentType.INVOICE_EMAIL, DocumentOwner.INVOICE, expense.id)
        importTestPdf("F15_Kontoauszug_2026_Q4_TEST.pdf", DocumentType.BANK_STATEMENT, DocumentOwner.GENERAL, null)
        // Reuse the TEST PDF payload solely to exercise distinct type icons, not document contents.
        importTestPdf("F15_Kontoauszug_2026_Q4_TEST.pdf", DocumentType.GAS, DocumentOwner.GENERAL, null, "TEST_Gas.pdf")
        importTestPdf("F15_Kontoauszug_2026_Q4_TEST.pdf", DocumentType.GEMA, DocumentOwner.GENERAL, null, "TEST_GEMA.pdf")

        val reloaded = AppStore(context)
        assertEquals(2, reloaded.dailyCloses.size)
        assertEquals(1, reloaded.invoices.size)
        assertEquals(5, reloaded.documents.size)
        assertEquals(setOf(DocumentType.BANK_STATEMENT, DocumentType.GAS, DocumentType.GEMA),
            reloaded.documents.filter { it.owner == DocumentOwner.GENERAL }.map { it.type }.toSet())
        assertEquals(74_200L, reloaded.dailyCloses.single { it.businessDate == today }.zAmountCents)
        assertEquals(68_000L, reloaded.dailyCloses.single { it.businessDate == today.minusDays(1) }.zAmountCents)
        assertEquals(1, reloaded.dailyCloses.single { it.id == todayClose.id }.documentIds.size)
        assertEquals(1, reloaded.invoices.single().documentIds.size)
        // Deliberately retained for the following screenshot run; isolated-app uninstall cleans up.
    }
}
