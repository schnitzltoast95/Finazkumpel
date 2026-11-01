package de.quartalsmappe.app.model

import de.quartalsmappe.app.time.SystemAppClock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

data class DailyClose(
    val id: String = UUID.randomUUID().toString(),
    val businessDate: LocalDate,
    val zAmountCents: Long,
    val note: String = "",
    val documentIds: List<String> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

data class InvoiceCase(
    val id: String = UUID.randomUUID().toString(),
    val supplier: String,
    val amountCents: Long? = null,
    val orderDate: LocalDate? = null,
    val invoiceDate: LocalDate? = null,
    val invoiceNumber: String = "",
    val deliveryDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val paidDate: LocalDate? = null,
    val note: String = "",
    val documentIds: List<String> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

enum class DocumentType(val label: String, val folderName: String) {
    Z_REPORT("Z-Abschlag", "Z-Abschlaege"),
    INVOICE_EMAIL("Rechnung aus E-Mail", "Rechnungen"),
    INVOICE_PAPER("Papierrechnung", "Rechnungen"),
    DELIVERY_NOTE("Lieferschein", "Rechnungen"),
    BANK_STATEMENT("Kontoauszug", "Kontoauszuege"),
    CASH_EXPORT("Kassenexport", "Kassenexporte"),
    RENT("Miete", "Weitere_Unterlagen"),
    ELECTRICITY("Strom", "Weitere_Unterlagen"),
    GAS("Gas", "Weitere_Unterlagen"),
    PAYROLL("Lohnunterlagen", "Weitere_Unterlagen"),
    GEMA("GEMA", "Weitere_Unterlagen"),
    BROADCAST_FEE("Rundfunk", "Weitere_Unterlagen"),
    BGW("BGW", "Weitere_Unterlagen"),
    OTHER("Sonstiges", "Weitere_Unterlagen"),

    ;

    val usesAnnualPeriod: Boolean
        get() = when (this) {
            GEMA, BROADCAST_FEE, BGW -> true
            else -> false
        }
}

enum class DocumentOwner { DAILY_CLOSE, INVOICE, GENERAL }

/** A user-defined capture kind; originals remain GENERAL/OTHER for compatibility. */
data class CustomDocumentType(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val usesAnnualPeriod: Boolean = false,
)

data class StoredDocument(
    val id: String = UUID.randomUUID().toString(),
    val type: DocumentType,
    val owner: DocumentOwner = DocumentOwner.GENERAL,
    val ownerId: String? = null,
    val periodYear: Int? = null,
    val periodQuarter: Int? = null,
    val originalFileName: String,
    val storedFileName: String,
    val mimeType: String,
    val sha256: String,
    val note: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val customTypeId: String? = null,
)

data class ReminderSettings(
    val enabledDays: Set<DayOfWeek> = emptySet(),
    val hour: Int = 19,
    val minute: Int = 0,
    val dueReminderDays: Int = 5,
)

data class Quarter(val year: Int, val number: Int) {
    init {
        require(number in 1..4)
    }

    val start: LocalDate
        get() = LocalDate.of(year, (number - 1) * 3 + 1, 1)

    val end: LocalDate
        get() = YearMonth.of(year, number * 3).atEndOfMonth()

    val label: String
        get() {
            val months = listOf(
                "Januar–März",
                "April–Juni",
                "Juli–September",
                "Oktober–Dezember",
            )
            return "${months[number - 1]} $year ($number. Quartal)"
        }

    fun contains(date: LocalDate?): Boolean =
        date != null && !date.isBefore(start) && !date.isAfter(end)

    fun previous(): Quarter =
        if (number == 1) Quarter(year - 1, 4) else Quarter(year, number - 1)

    companion object {
        fun current(today: LocalDate = SystemAppClock.today()): Quarter =
            Quarter(today.year, ((today.monthValue - 1) / 3) + 1)
    }
}

enum class InvoiceFilter { OPEN, DONE, ALL }

data class InvoiceSignals(
    val label: String,
    val messages: List<String>,
    val isDone: Boolean,
    val isOverdue: Boolean,
)

fun InvoiceCase.signals(
    today: LocalDate = SystemAppClock.today(),
    dueSoonDays: Int = 5,
): InvoiceSignals {
    val messages = buildList {
        if (invoiceDate == null) add("Rechnung fehlt")
        if (deliveryDate == null) add("Lieferung fehlt")
        if (paidDate == null && dueDate != null) {
            when {
                dueDate.isBefore(today) -> add("Seit ${dueDate.formatGerman()} überfällig")
                !dueDate.isAfter(today.plusDays(dueSoonDays.toLong())) ->
                    add("Zahlung bis ${dueDate.formatGerman()}")
                else -> add("Zahlung bis ${dueDate.formatGerman()}")
            }
        } else if (paidDate == null && invoiceDate != null) {
            add("Zahlung noch offen")
        }
    }
    val done = invoiceDate != null && deliveryDate != null && paidDate != null
    val overdue = paidDate == null && dueDate?.isBefore(today) == true
    val label = when {
        done -> "Erledigt"
        overdue -> "Überfällig"
        paidDate == null && dueDate != null && !dueDate.isAfter(today.plusDays(dueSoonDays.toLong())) -> "Bald fällig"
        deliveryDate != null -> "Zahlung ausstehend"
        invoiceDate != null -> "Rechnung erhalten"
        else -> "Bestellt"
    }
    return InvoiceSignals(label, messages, done, overdue)
}

fun LocalDate.formatGerman(): String = "%02d.%02d.%04d".format(dayOfMonth, monthValue, year)

fun centsToGerman(cents: Long?): String {
    if (cents == null) return "Betrag offen"
    val absolute = kotlin.math.abs(cents)
    val sign = if (cents < 0) "-" else ""
    return "$sign${absolute / 100},${(absolute % 100).toString().padStart(2, '0')} €"
}

fun parseEuroToCents(value: String): Long? {
    val normalized = value.trim()
        .replace("€", "")
        .replace(" ", "")
        .replace(".", "")
        .replace(',', '.')
    if (normalized.isBlank()) return null
    val amount = normalized.toBigDecimalOrNull() ?: return null
    return try {
        amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}

