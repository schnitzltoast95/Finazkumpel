package de.quartalsmappe.app.export

import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.StoredDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuarterExportManifestTest {
    private val quarter = Quarter(2026, 4)

    @Test
    fun `owner dates and general periods select exactly the relevant originals`() {
        val close = DailyClose(id = "close", businessDate = quarter.start, zAmountCents = 1200)
        val oldClose = close.copy(id = "old", businessDate = quarter.start.minusDays(1))
        val invoice = InvoiceCase(id = "invoice", supplier = "Test", invoiceDate = quarter.end)
        val documents = listOf(
            document("z", DocumentOwner.DAILY_CLOSE, close.id, type = DocumentType.Z_REPORT, year = 2000, number = 1),
            document("old-z", DocumentOwner.DAILY_CLOSE, oldClose.id, type = DocumentType.Z_REPORT),
            document("invoice", DocumentOwner.INVOICE, invoice.id),
            document("quarter"),
            document("annual", year = 2026, number = null),
            document("wrong-quarter", number = 3),
            document("wrong-year", year = 2025, number = null),
        )

        val manifest = QuarterExportManifest.create(quarter, true, listOf(close, oldClose), listOf(invoice), documents)

        assertEquals(setOf("z", "invoice", "quarter", "annual"), manifest.files.map { it.document.id }.toSet())
        assertEquals(listOf(close), manifest.dailyCloses)
        assertEquals(1, manifest.dailyClosesWithDocument)
        assertEquals(0, manifest.dailyClosesWithoutDocument)
    }

    @Test
    fun `invoice toggle only changes original files and keeps overview data`() {
        val invoice = InvoiceCase(id = "invoice", supplier = "Test", invoiceDate = quarter.start)
        val documents = listOf(document("invoice-file", DocumentOwner.INVOICE, invoice.id), document("general"))
        val without = QuarterExportManifest.create(quarter, false, emptyList(), listOf(invoice), documents)
        val with = QuarterExportManifest.create(quarter, true, emptyList(), listOf(invoice), documents)

        assertEquals(listOf(invoice), without.invoices)
        assertEquals(without.invoices, with.invoices)
        assertEquals(listOf("general"), without.files.map { it.document.id })
        assertEquals(setOf("general", "invoice-file"), with.files.map { it.document.id }.toSet())
        assertEquals(listOf("Uebersicht.pdf", "Tagesumsaetze.csv"), without.generatedFiles)
    }

    @Test
    fun `old unpaid invoices remain relevant but old settled invoices do not`() {
        val open = InvoiceCase(id = "open", supplier = "Test", invoiceDate = quarter.start.minusDays(30))
        val settled = open.copy(id = "settled", paidDate = quarter.start.minusDays(1))
        val paidLater = open.copy(id = "later", paidDate = quarter.end.plusDays(1))
        val deliveredNow = settled.copy(id = "delivery", deliveryDate = quarter.start)
        val manifest = QuarterExportManifest.create(quarter, false, emptyList(), listOf(open, settled, paidLater, deliveredNow), emptyList())

        assertEquals(setOf("open", "later", "delivery"), manifest.invoices.map { it.id }.toSet())
    }

    @Test
    fun `missing originals stay in manifest and do not count as available Z reports`() {
        val close = DailyClose(id = "close", businessDate = quarter.start, zAmountCents = 1200, documentIds = listOf("z"))
        val z = document("z", DocumentOwner.DAILY_CLOSE, close.id, type = DocumentType.Z_REPORT)
        val manifest = QuarterExportManifest.create(quarter, false, listOf(close), emptyList(), listOf(z)) { false }

        assertEquals(1, manifest.files.size)
        assertEquals(listOf(z), manifest.missingFiles.map { it.document })
        assertFalse(manifest.hasZReport(close.id))
        assertEquals(1, manifest.dailyClosesWithoutDocument)
    }

    @Test
    fun `custom kinds keep annual and quarterly inclusion with fixed safe archive paths`() {
        val annual = document("custom-year", number = null).copy(customTypeId = "annual-kind")
        val quarterly = document("custom-quarter").copy(customTypeId = "quarterly-kind")
        val otherQuarter = quarterly.copy(id = "other-quarter", periodQuarter = 3)
        val manifest = QuarterExportManifest.create(quarter, false, emptyList(), emptyList(), listOf(annual, quarterly, otherQuarter))
        assertEquals(setOf(annual.id, quarterly.id), manifest.files.map { it.document.id }.toSet())
        assertTrue(manifest.files.all { it.archivePath.startsWith("Weitere_Unterlagen/") })
        assertTrue(manifest.files.all { it.archivePath.count { char -> char == '/' } == 1 })
        val lines = manifest.generalDocumentOverviewLines { if (it == annual) "Versicherung" else "../../Weiterbildung" }
        assertTrue(lines.contains("Versicherung"))
        assertTrue(lines.contains("../../Weiterbildung"))
        assertTrue(manifest.files.all { entry -> lines.contains("  ${entry.archivePath}") })
        assertTrue(manifest.files.none { it.archivePath.contains("Weiterbildung") })
    }

    @Test
    fun `archive paths are safe unique and deterministic for duplicate original names`() {
        val a = document("a").copy(originalFileName = "../../Beleg 1.pdf")
        val b = document("b").copy(originalFileName = a.originalFileName)
        val first = QuarterExportManifest.create(quarter, true, emptyList(), emptyList(), listOf(a, b))
        val reversed = QuarterExportManifest.create(quarter, true, emptyList(), emptyList(), listOf(b, a))

        assertEquals(first.files.map { it.archivePath }, reversed.files.map { it.archivePath })
        assertEquals(2, first.files.map { it.archivePath }.toSet().size)
        assertTrue(first.files.all { it.archivePath.split('/').size == 2 })
        assertTrue(first.files.all { it.archivePath.startsWith("Weitere_Unterlagen/") })
    }

    private fun document(
        id: String,
        owner: DocumentOwner = DocumentOwner.GENERAL,
        ownerId: String? = null,
        type: DocumentType = DocumentType.OTHER,
        year: Int? = 2026,
        number: Int? = 4,
    ) = StoredDocument(
        id = id,
        type = type,
        owner = owner,
        ownerId = ownerId,
        periodYear = year,
        periodQuarter = number,
        originalFileName = "$id.pdf",
        storedFileName = "$id.pdf",
        mimeType = "application/pdf",
        sha256 = "hash-$id",
    )
}
