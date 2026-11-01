package de.quartalsmappe.app.model

import java.time.LocalDateTime
import java.util.Locale

private val supplierWhitespace = Regex("\\s+")

/**
 * Returns recently used supplier names without creating a separate address book.
 * Only already-saved invoice cases can contribute suggestions.
 */
fun List<InvoiceCase>.rememberedSupplierNames(
    query: String = "",
    excludingInvoiceId: String? = null,
    limit: Int = 4,
): List<String> {
    if (limit <= 0) return emptyList()

    val cleanedQuery = query.cleanedSupplierName()
    val queryKey = cleanedQuery.lowercase(Locale.GERMAN)

    val usage = asSequence()
        .filterNot { it.id == excludingInvoiceId }
        .filter { it.supplier.cleanedSupplierName().isNotEmpty() }
        .groupBy { it.supplier.cleanedSupplierName().lowercase(Locale.GERMAN) }
        .values
        .map { matches ->
            val latest = matches.maxWith(
                compareBy<InvoiceCase> { it.updatedAt }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
            SupplierUsage(
                displayName = latest.supplier.cleanedSupplierName(),
                count = matches.size,
                lastUsed = latest.updatedAt,
            )
        }

    return usage.asSequence()
        .filter { queryKey.isEmpty() || it.displayName.lowercase(Locale.GERMAN).contains(queryKey) }
        .filterNot { queryKey.isNotEmpty() && it.displayName.equals(cleanedQuery, ignoreCase = true) }
        .sortedWith(
            compareByDescending<SupplierUsage> { it.count }
                .thenByDescending { it.lastUsed }
                .thenBy { it.displayName.lowercase(Locale.GERMAN) }
        )
        .map { it.displayName }
        .take(limit)
        .toList()
}

private data class SupplierUsage(
    val displayName: String,
    val count: Int,
    val lastUsed: LocalDateTime,
)

private fun String.cleanedSupplierName(): String = trim().replace(supplierWhitespace, " ")
