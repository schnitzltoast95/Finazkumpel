package de.quartalsmappe.app.ui

import androidx.annotation.DrawableRes
import de.quartalsmappe.app.R
import de.quartalsmappe.app.model.DocumentType

// The same Lucide symbols identify a kind when creating it and when finding it again.
@DrawableRes
internal fun documentTypeIcon(type: DocumentType): Int = when (type) {
    DocumentType.Z_REPORT -> R.drawable.ic_type_calculator
    DocumentType.INVOICE_EMAIL, DocumentType.INVOICE_PAPER -> R.drawable.ic_type_receipt_text
    DocumentType.DELIVERY_NOTE -> R.drawable.ic_type_package
    DocumentType.BANK_STATEMENT -> R.drawable.ic_type_landmark
    DocumentType.CASH_EXPORT -> R.drawable.ic_type_file_spreadsheet
    DocumentType.RENT -> R.drawable.ic_type_key_round
    DocumentType.ELECTRICITY -> R.drawable.ic_type_zap
    DocumentType.GAS -> R.drawable.ic_type_flame
    DocumentType.PAYROLL -> R.drawable.ic_type_users
    DocumentType.GEMA -> R.drawable.ic_type_music2
    DocumentType.BROADCAST_FEE -> R.drawable.ic_type_radio
    DocumentType.BGW -> R.drawable.ic_type_shield_check
    DocumentType.OTHER -> R.drawable.ic_type_file
}

@DrawableRes
internal fun entryTypeIcon(typeKey: String): Int = when (typeKey) {
    "daily" -> documentTypeIcon(DocumentType.Z_REPORT)
    "invoice" -> documentTypeIcon(DocumentType.INVOICE_EMAIL)
    "order" -> documentTypeIcon(DocumentType.DELIVERY_NOTE)
    else -> DocumentType.entries.firstOrNull { it.name == typeKey }
        ?.let(::documentTypeIcon) ?: documentTypeIcon(DocumentType.OTHER)
}
