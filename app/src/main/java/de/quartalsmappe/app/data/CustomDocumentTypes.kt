package de.quartalsmappe.app.data

import de.quartalsmappe.app.model.CustomDocumentType
import de.quartalsmappe.app.model.DocumentType
import java.util.UUID

internal fun validateCustomTypeName(name: String): String {
    val trimmed = name.trim()
    require(trimmed.isNotBlank()) { "Bitte gib einen Namen für die eigene Art ein." }
    require(trimmed.length <= 80) { "Der Name darf höchstens 80 Zeichen lang sein." }
    require(trimmed.none { it.isISOControl() || it == '\u2028' || it == '\u2029' }) {
        "Bitte gib den Namen in einer Zeile ein."
    }
    return trimmed
}

internal fun validateNewCustomTypeName(name: String, existing: List<CustomDocumentType>): String {
    val trimmed = validateCustomTypeName(name)
    val builtInNames = DocumentType.entries.map { it.label } + listOf("Tagesabschluss", "Rechnung", "Bestellung")
    require(builtInNames.none { it.equals(trimmed, ignoreCase = true) } &&
        existing.none { it.name.equals(trimmed, ignoreCase = true) }) {
        "Eine Art mit diesem Namen gibt es bereits. Bitte wähle einen anderen Namen."
    }
    return trimmed
}

internal data class CustomTypeMerge(
    val types: List<CustomDocumentType>,
    val importedIdMapping: Map<String, String>,
)

/** Existing definitions never change. Incoming documents use the corresponding mapped ID. */
internal fun mergeCustomDocumentTypes(
    existing: List<CustomDocumentType>,
    incoming: List<CustomDocumentType>,
): CustomTypeMerge {
    require(incoming.map { it.id }.toSet().size == incoming.size) {
        "Die Sicherung enthält widersprüchliche eigene Arten."
    }
    val result = existing.toMutableList()
    val mapping = mutableMapOf<String, String>()
    incoming.forEach { imported ->
        require(imported.id.isNotBlank()) { "Die Sicherung enthält eine ungültige eigene Art." }
        val normalized = imported.copy(name = validateCustomTypeName(imported.name))
        val equivalent = result.firstOrNull {
            it.name.equals(normalized.name, ignoreCase = true) && it.usesAnnualPeriod == normalized.usesAnnualPeriod
        }
        val selected = equivalent ?: normalized.let {
            if (result.any { current -> current.id == it.id }) it.copy(id = UUID.randomUUID().toString()) else it
        }.also { result.add(it) }
        mapping[imported.id] = selected.id
    }
    return CustomTypeMerge(result, mapping)
}
