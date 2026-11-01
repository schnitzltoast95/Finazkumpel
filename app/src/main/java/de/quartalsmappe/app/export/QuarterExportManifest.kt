package de.quartalsmappe.app.export

import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.StoredDocument

data class QuarterExportFile(
    val document: StoredDocument,
    val archivePath: String,
    val isAvailable: Boolean,
)

/** The single selection used by the export review and the generated ZIP. */
data class QuarterExportManifest(
    val quarter: Quarter,
    val includeInvoices: Boolean,
    val dailyCloses: List<DailyClose>,
    val invoices: List<InvoiceCase>,
    val files: List<QuarterExportFile>,
) {
    val generatedFiles: List<String> = listOf("Uebersicht.pdf", "Tagesumsaetze.csv")
    val missingFiles: List<QuarterExportFile> get() = files.filterNot { it.isAvailable }
    val dailyClosesWithDocument: Int get() = dailyCloses.count { hasZReport(it.id) }
    val dailyClosesWithoutDocument: Int get() = dailyCloses.size - dailyClosesWithDocument

    fun hasZReport(closeId: String): Boolean = files.any {
        it.document.owner == DocumentOwner.DAILY_CLOSE && it.document.ownerId == closeId &&
            it.document.type == DocumentType.Z_REPORT && it.isAvailable
    }

    /** Lists custom kinds alongside their unchanged, safe archive locations. */
    fun generalDocumentOverviewLines(typeLabel: (StoredDocument) -> String): List<String> =
        files.filter { it.document.owner == DocumentOwner.GENERAL }.flatMap { entry ->
            typeLabel(entry.document).chunked(80) + entry.archivePath.chunked(80).map { "  $it" }
        }

    companion object {
        fun create(
            quarter: Quarter,
            includeInvoices: Boolean,
            dailyCloses: List<DailyClose>,
            invoices: List<InvoiceCase>,
            documents: List<StoredDocument>,
            isAvailable: (StoredDocument) -> Boolean = { true },
        ): QuarterExportManifest {
            val selectedCloses = dailyCloses.filter { quarter.contains(it.businessDate) }.sortedBy { it.businessDate }
            val selectedInvoices = invoices.filter { it.isRelevantForExport(quarter) }
                .sortedWith(compareBy({ it.supplier.lowercase() }, { it.id }))
            val closeIds = selectedCloses.mapTo(mutableSetOf()) { it.id }
            val invoiceIds = selectedInvoices.mapTo(mutableSetOf()) { it.id }
            val selectedDocuments = documents.filter { document ->
                when (document.owner) {
                    DocumentOwner.DAILY_CLOSE -> document.ownerId in closeIds
                    DocumentOwner.INVOICE -> includeInvoices && document.ownerId in invoiceIds
                    DocumentOwner.GENERAL -> document.periodYear == quarter.year &&
                        (document.periodQuarter == null || document.periodQuarter == quarter.number)
                }
            }.sortedWith(compareBy({ it.type.folderName }, { it.originalFileName.lowercase() }, { it.id }))
            return QuarterExportManifest(
                quarter = quarter,
                includeInvoices = includeInvoices,
                dailyCloses = selectedCloses,
                invoices = selectedInvoices,
                files = selectedDocuments.mapIndexed { index, document ->
                    QuarterExportFile(
                        document = document,
                        archivePath = "${document.type.folderName}/${(index + 1).toString().padStart(3, '0')}_${document.originalFileName.safeExportName()}",
                        isAvailable = isAvailable(document),
                    )
                },
            )
        }
    }
}

private fun InvoiceCase.isRelevantForExport(quarter: Quarter): Boolean {
    val hasDateInQuarter = quarter.contains(orderDate) || quarter.contains(invoiceDate) ||
        quarter.contains(deliveryDate) || quarter.contains(dueDate) || quarter.contains(paidDate)
    val openAtQuarterEnd = invoiceDate?.let { !it.isAfter(quarter.end) } == true &&
        (paidDate == null || paidDate.isAfter(quarter.end))
    return hasDateInQuarter || openAtQuarterEnd
}

private fun String.safeExportName(): String =
    replace(Regex("[^A-Za-z0-9ÄÖÜäöüß._-]"), "_").take(100).ifBlank { "Dokument" }
