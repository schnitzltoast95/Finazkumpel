package de.quartalsmappe.app.data

import de.quartalsmappe.app.model.CustomDocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDocumentTypesTest {
    @Test fun `names are trimmed and invalid or duplicate choices rejected`() {
        assertEquals("Versicherung", validateNewCustomTypeName("  Versicherung  ", emptyList()))
        listOf("", " ", "x".repeat(81), "Art\nZwei", "Art\tZwei", "RECHNUNG", "kontoauszug", "Bestellung").forEach {
            assertTrue("Expected rejection for $it", runCatching { validateNewCustomTypeName(it, emptyList()) }.isFailure)
        }
        assertTrue(runCatching {
            validateNewCustomTypeName(" VERSICHERUNG ", listOf(CustomDocumentType(name = "Versicherung")))
        }.isFailure)
    }

    @Test fun `restore merges equivalent definitions but never overwrites existing ones`() {
        val local = CustomDocumentType(id = "local", name = "Versicherung", usesAnnualPeriod = true)
        val incoming = local.copy(id = "backup", name = "versicherung")
        val result = mergeCustomDocumentTypes(listOf(local), listOf(incoming))
        assertEquals(listOf(local), result.types)
        assertEquals(local.id, result.importedIdMapping[incoming.id])
    }

    @Test fun `conflicting ids receive safe remapping and repeated restores stay idempotent`() {
        val local = CustomDocumentType(id = "same", name = "Versicherung")
        val incoming = CustomDocumentType(id = "same", name = "Weiterbildung", usesAnnualPeriod = true)
        val first = mergeCustomDocumentTypes(listOf(local), listOf(incoming))
        assertEquals(local, first.types.first())
        val restored = first.types.last()
        assertEquals(incoming.name, restored.name)
        assertTrue(restored.usesAnnualPeriod)
        assertNotEquals(incoming.id, restored.id)
        assertEquals(restored.id, first.importedIdMapping[incoming.id])
        val second = mergeCustomDocumentTypes(first.types, listOf(incoming))
        assertEquals(first.types, second.types)
        assertEquals(restored.id, second.importedIdMapping[incoming.id])
    }

    @Test fun `annual and quarterly types never silently merge`() {
        val local = CustomDocumentType(id = "annual", name = "Versicherung", usesAnnualPeriod = true)
        val incoming = local.copy(id = "quarterly", usesAnnualPeriod = false)
        val result = mergeCustomDocumentTypes(listOf(local), listOf(incoming))
        assertEquals(listOf(local, incoming), result.types)
        assertEquals(incoming.id, result.importedIdMapping[incoming.id])
    }

    @Test fun `duplicate ids within a backup are rejected before a merge`() {
        val first = CustomDocumentType(id = "same", name = "Versicherung")
        assertTrue(runCatching {
            mergeCustomDocumentTypes(emptyList(), listOf(first, first.copy(name = "Weiterbildung")))
        }.isFailure)
    }
}
