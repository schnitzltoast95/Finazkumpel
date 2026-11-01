package de.quartalsmappe.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.export.QuarterExportManifest
import de.quartalsmappe.app.export.QuarterExporter
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.centsToGerman
import de.quartalsmappe.app.model.formatGerman
import de.quartalsmappe.app.time.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QuarterScreen(
    store: AppStore,
    clock: AppClock,
    modifier: Modifier = Modifier,
    initialQuarter: Quarter? = null,
    onOpenDaily: (String) -> Unit,
    onOpenInvoice: (String) -> Unit,
    onEditDocument: (String) -> Unit,
    onAddDocument: (DocumentType, Quarter) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = initialQuarter ?: Quarter.current(clock.today())
    var year by rememberSaveable(initialQuarter) { mutableIntStateOf(initial.year) }
    var number by rememberSaveable(initialQuarter) { mutableIntStateOf(initial.number) }
    val quarter = Quarter(year, number)
    var includeInvoices by rememberSaveable { mutableStateOf(false) }
    var showDailyCloses by rememberSaveable { mutableStateOf(false) }
    var showInvoices by rememberSaveable { mutableStateOf(false) }
    var showFiles by rememberSaveable { mutableStateOf(false) }
    var showWarning by rememberSaveable { mutableStateOf(false) }
    var exportError by rememberSaveable { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    val manifest = QuarterExportManifest.create(
        quarter, includeInvoices, store.dailyCloses, store.invoices, store.documents,
        isAvailable = { store.fileFor(it).let { file -> file.isFile && file.canRead() } },
    )
    val bankCount = manifest.files.count { it.document.owner == DocumentOwner.GENERAL && it.document.type == DocumentType.BANK_STATEMENT && store.fileFor(it.document).isFile }
    val cashExportCount = manifest.files.count { it.document.owner == DocumentOwner.GENERAL && it.document.type == DocumentType.CASH_EXPORT && store.fileFor(it.document).isFile }
    val hasGaps = bankCount == 0 || cashExportCount == 0 || manifest.dailyClosesWithoutDocument > 0

    fun changeQuarter(next: Quarter) {
        year = next.year
        number = next.number
        exportError = null
    }

    fun export() {
        if (isExporting) return
        isExporting = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    QuarterExporter.createPackage(context, store, quarter, includeInvoices)
                }
            }.onSuccess { uri ->
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(Intent.createChooser(share, "Quartalspaket teilen")) }
                    .onSuccess { exportError = null }
                    .onFailure { exportError = "Das Paket wurde erstellt, konnte aber nicht geteilt werden. Bitte versuche es erneut." }
            }.onFailure {
                exportError = it.message ?: "Das Paket konnte nicht erstellt werden. Bitte prüfe die ausgewählten Dateien."
            }
            isExporting = false
        }
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Noch nicht alles hinterlegt") },
            text = { Text(buildString {
                append("Für dieses Quartal fehlen noch: ")
                append(listOfNotNull(
                    "Kontoauszug".takeIf { bankCount == 0 },
                    "Kassenexport".takeIf { cashExportCount == 0 },
                    "Z-Abschlag bei ${manifest.dailyClosesWithoutDocument} Tagesabschluss/Tagesabschlüssen".takeIf { manifest.dailyClosesWithoutDocument > 0 },
                ).joinToString(", "))
                append(". Möchtest du das Paket trotzdem erstellen?")
            }) },
            confirmButton = { Button(onClick = { showWarning = false; export() }) { Text("Trotzdem erstellen") } },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text("Abbrechen") } },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(quarter.label, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { changeQuarter(quarter.previous()) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Vorheriges Quartal") }
                    TextButton(
                        onClick = { changeQuarter(if (quarter.number == 4) Quarter(quarter.year + 1, 1) else Quarter(quarter.year, quarter.number + 1)) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Nächstes Quartal") }
                }
                Text(
                    "Prüfe die Einträge für die Finanzberaterin. Jahresunterlagen gehören in jedes Quartal ihres Jahres.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasGaps || manifest.dailyClosesWithoutDocument > 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Noch zu ergänzen")
                    if (bankCount == 0) {
                        TextButton(onClick = { onAddDocument(DocumentType.BANK_STATEMENT, quarter) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export_add_bank")) {
                            Text("Kontoauszug hinzufügen", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (cashExportCount == 0) {
                        TextButton(onClick = { onAddDocument(DocumentType.CASH_EXPORT, quarter) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export_add_cash")) {
                            Text("Kassenexport hinzufügen", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (manifest.dailyClosesWithoutDocument > 0) {
                        TextButton(onClick = { showDailyCloses = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("${manifest.dailyClosesWithoutDocument} ${if (manifest.dailyClosesWithoutDocument == 1) "Tagesabschluss" else "Tagesabschlüsse"} ohne Z-Abschlag ansehen", modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Text("Diese Hinweise verhindern den Export nicht.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            AppSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tagesabschlüsse", style = MaterialTheme.typography.titleMedium)
                    Text("${manifest.dailyCloses.size} ${if (manifest.dailyCloses.size == 1) "Tagesabschluss" else "Tagesabschlüsse"} erfasst")
                    Text("${manifest.dailyClosesWithDocument} ${if (manifest.dailyClosesWithDocument == 1) "Z-Abschlag" else "Z-Abschläge"} als Datei dabei")
                    Text(
                        "Die Tagesumsätze kommen zusätzlich in die Übersicht und die Tabelle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (manifest.dailyCloses.isNotEmpty()) {
                        TextButton(onClick = { showDailyCloses = !showDailyCloses }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (showDailyCloses) "Tagesumsätze ausblenden" else "Tagesumsätze ansehen und bearbeiten")
                        }
                    }
                }
            }
        }
        if (showDailyCloses) {
            items(manifest.dailyCloses, key = { "close_${it.id}" }) { close ->
                TextButton(onClick = { onOpenDaily(close.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${close.businessDate.formatGerman()} · ${centsToGerman(close.zAmountCents)}", style = MaterialTheme.typography.titleSmall)
                        if (!manifest.hasZReport(close.id)) Text("Z-Abschlag noch ergänzen", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Zusammenfassungen im Paket")
                Text("Uebersicht.pdf", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tagesabschlüsse und ${manifest.invoices.size} ${if (manifest.invoices.size == 1) "Rechnung / Bestellung" else "Rechnungen / Bestellungen"} mit Betrag, Status und Datumsangaben.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Tagesumsaetze.csv", style = MaterialTheme.typography.titleSmall)
                Text("${manifest.dailyCloses.size} ${if (manifest.dailyCloses.size == 1) "Tagesumsatz" else "Tagesumsätze"} mit Datum, Betrag, Notiz und Z-Beleg-Status.", style = MaterialTheme.typography.bodyMedium)
                if (manifest.invoices.isNotEmpty()) {
                    TextButton(onClick = { showInvoices = !showInvoices }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (showInvoices) "Rechnungsangaben ausblenden" else "Rechnungen und Bestellungen prüfen")
                    }
                }
            }
        }
        if (showInvoices) {
            items(manifest.invoices, key = { "invoice_${it.id}" }) { invoice ->
                TextButton(onClick = { onOpenInvoice(invoice.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(invoice.supplier, style = MaterialTheme.typography.titleSmall)
                        Text(centsToGerman(invoice.amountCents), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = includeInvoices,
                    role = Role.Switch,
                    onValueChange = { includeInvoices = it; exportError = null },
                ).testTag("export_include_invoices").padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rechnungsdateien mitgeben", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Optional. Dieser Schalter betrifft nur die Anhänge. Rechnungs- und Bestellangaben stehen immer in der Übersicht.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = includeInvoices, onCheckedChange = null)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Originaldateien im Paket (${manifest.files.size})")
                Text("$bankCount ${if (bankCount == 1) "Kontoauszug" else "Kontoauszüge"} · $cashExportCount ${if (cashExportCount == 1) "Kassenexport" else "Kassenexporte"}", style = MaterialTheme.typography.bodyMedium)
                val otherTypes = manifest.files.filter { it.document.owner == DocumentOwner.GENERAL && it.document.type !in listOf(DocumentType.BANK_STATEMENT, DocumentType.CASH_EXPORT) }
                    .groupingBy { store.documentTypeLabel(it.document) }.eachCount()
                otherTypes.forEach { (label, count) -> Text("$label: $count", style = MaterialTheme.typography.bodyMedium) }
                AppSecondaryButton(if (showFiles) "Dateiliste schließen" else "Dateien im Paket ansehen", Modifier.fillMaxWidth()) { showFiles = !showFiles }
            }
        }
        if (showFiles && manifest.files.isEmpty()) {
            item { Text("Für diese Auswahl sind noch keine Originaldateien hinterlegt.", style = MaterialTheme.typography.bodyMedium) }
        } else if (showFiles) {
            items(manifest.files, key = { "file_${it.document.id}" }) { entry ->
                DocumentLibraryRow(store, entry.document, onOpenDaily, onOpenInvoice, onEditDocument, archivePath = entry.archivePath)
            }
        }
        if (manifest.missingFiles.isNotEmpty()) {
            item { MissingHint("${manifest.missingFiles.size} gespeicherte Originaldateien sind nicht verfügbar. Lies eine passende Sicherung unter Optionen ein. Bis dahin wird kein unvollständiges Paket erstellt.") }
        }
        exportError?.let { item { MissingHint(it) } }
        item {
            AppPrimaryButton(
                text = if (isExporting) "Paket wird erstellt …" else "Quartalspaket erstellen",
                modifier = Modifier.fillMaxWidth(),
                enabled = manifest.missingFiles.isEmpty() && !isExporting,
                onClick = { if (hasGaps) showWarning = true else export() },
            )
        }
        item {
            Text(
                "Die Vorschau prüft die Ablage in der App, nicht die steuerliche Vollständigkeit. Beim Teilen werden nur die oben aufgeführten Dateien weitergegeben.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

