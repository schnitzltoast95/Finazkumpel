package de.quartalsmappe.app.ui

import de.quartalsmappe.app.R
import de.quartalsmappe.app.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTypeIconsTest {
    @Test
    fun documentKindsUseTheMockupLucideSymbols() {
        val expected = mapOf(
            DocumentType.Z_REPORT to R.drawable.ic_type_calculator,
            DocumentType.INVOICE_EMAIL to R.drawable.ic_type_receipt_text,
            DocumentType.INVOICE_PAPER to R.drawable.ic_type_receipt_text,
            DocumentType.DELIVERY_NOTE to R.drawable.ic_type_package,
            DocumentType.BANK_STATEMENT to R.drawable.ic_type_landmark,
            DocumentType.CASH_EXPORT to R.drawable.ic_type_file_spreadsheet,
            DocumentType.RENT to R.drawable.ic_type_key_round,
            DocumentType.ELECTRICITY to R.drawable.ic_type_zap,
            DocumentType.GAS to R.drawable.ic_type_flame,
            DocumentType.PAYROLL to R.drawable.ic_type_users,
            DocumentType.GEMA to R.drawable.ic_type_music2,
            DocumentType.BROADCAST_FEE to R.drawable.ic_type_radio,
            DocumentType.BGW to R.drawable.ic_type_shield_check,
            DocumentType.OTHER to R.drawable.ic_type_file,
        )
        assertEquals(DocumentType.entries.toSet(), expected.keys)
        expected.forEach { (type, icon) -> assertEquals(type.name, icon, documentTypeIcon(type)) }
    }

    @Test
    fun entriesUseTheSameIconsAsCapture() {
        assertEquals(documentTypeIcon(DocumentType.Z_REPORT), entryTypeIcon("daily"))
        assertEquals(documentTypeIcon(DocumentType.INVOICE_EMAIL), entryTypeIcon("invoice"))
        assertEquals(documentTypeIcon(DocumentType.DELIVERY_NOTE), entryTypeIcon("order"))
        DocumentType.entries.forEach { type ->
            assertEquals(type.name, documentTypeIcon(type), entryTypeIcon(type.name))
        }
    }

    @Test
    fun eachFurtherCaptureKindHasADistinctSymbol() {
        val furtherKinds = listOf(
            DocumentType.BANK_STATEMENT, DocumentType.CASH_EXPORT, DocumentType.RENT,
            DocumentType.ELECTRICITY, DocumentType.GAS, DocumentType.PAYROLL,
            DocumentType.GEMA, DocumentType.BROADCAST_FEE, DocumentType.BGW, DocumentType.OTHER,
        )
        assertEquals(furtherKinds.size, furtherKinds.map(::documentTypeIcon).distinct().size)
    }

    @Test
    fun customAndUnknownKindsKeepTheFileFallback() {
        assertEquals(documentTypeIcon(DocumentType.OTHER), entryTypeIcon("custom_example"))
        assertEquals(documentTypeIcon(DocumentType.OTHER), entryTypeIcon("unknown"))
    }
}
