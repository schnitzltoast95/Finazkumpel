package de.quartalsmappe.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.R
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.StoredDocument
import de.quartalsmappe.app.model.centsToGerman
import de.quartalsmappe.app.model.formatGerman
import de.quartalsmappe.app.model.signals
import de.quartalsmappe.app.time.AppClock

private data class EntryRowData(
    val key: String,
    val id: String,
    val owner: DocumentOwner,
    val title: String,
    val typeKey: String,
    val typeLabel: String,
    val period: String,
    val amount: String?,
    val status: String?,
    val critical: Boolean,
    val pending: Boolean,
    val unpaid: Boolean,
    val attachments: List<StoredDocument>,
    val search: String,
    val sortDate: String,
)

@Composable
fun EntriesScreen(
    store: AppStore,
    clock: AppClock,
    modifier: Modifier = Modifier,
    onOpenExport: () -> Unit,
    onOpenDaily: (String) -> Unit,
    onOpenInvoice: (String) -> Unit,
    onEditDocument: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("all") }
    var statusFilter by rememberSaveable { mutableStateOf("Alle") }
    var filterDialog by rememberSaveable { mutableStateOf<String?>(null) }
    val all = entryRows(store, clock)
    val typeOptions = listOf("all" to "Alle Arten", "daily" to "Tagesabschluss", "invoice" to "Rechnung", "order" to "Bestellung") +
        captureDocumentTypes.map { it.name to it.label } + store.customDocumentTypes.map { "custom_${it.id}" to it.name }
    val selectedTypeLabel = typeOptions.firstOrNull { it.first == typeFilter }?.second ?: "Alle Arten"
    val search = query.trim()
    val rows = all.filter { entry ->
        (typeFilter == "all" || entry.typeKey == typeFilter) &&
            (search.isBlank() || entry.search.contains(search, ignoreCase = true)) &&
            when (statusFilter) {
                "Noch offen" -> entry.pending || entry.critical
                "Unbezahlt" -> entry.unpaid
                "Kritisch" -> entry.critical
                else -> true
            }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("entries_list"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AppSurfaceCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                CaptureActionRow("Quartalspaket vorbereiten", "Für die Finanzberaterin zusammenstellen", R.drawable.ic_nav_documents,
                    Modifier.padding(horizontal = 16.dp).testTag("entries_export"), onOpenExport)
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Einträge suchen") },
                placeholder = { Text("Lieferant, Art, Datei oder Notiz") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("entries_search"),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { filterDialog = "type" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("entries_type_filter")) { Text(selectedTypeLabel) }
                OutlinedButton(onClick = { filterDialog = "status" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("entries_status_filter")) { Text(if (statusFilter == "Alle") "Alle Status" else statusFilter) }
            }
        }
        item { Text("${rows.size} ${if (rows.size == 1) "Eintrag" else "Einträge"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (rows.isEmpty()) {
            item {
                Text(
                    if (all.isEmpty()) "Hier findest du alles wieder, was du unter Erfassen anlegst – auch Bestellungen ohne Datei."
                    else "Keine passenden Einträge. Ändere die Suche oder die Filter.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        items(rows, key = { it.key }) { entry ->
            EntryRow(entry) {
                when (entry.owner) {
                    DocumentOwner.DAILY_CLOSE -> onOpenDaily(entry.id)
                    DocumentOwner.INVOICE -> onOpenInvoice(entry.id)
                    DocumentOwner.GENERAL -> onEditDocument(entry.id)
                }
            }
        }
    }
    if (filterDialog != null) {
        val isType = filterDialog == "type"
        ChoiceListDialog(
            title = if (isType) "Art auswählen" else "Status auswählen",
            options = if (isType) typeOptions else listOf("Alle", "Noch offen", "Unbezahlt", "Kritisch").map { it to it },
            selected = if (isType) typeFilter else statusFilter,
            onSelected = { if (isType) typeFilter = it else statusFilter = it; filterDialog = null },
            onDismiss = { filterDialog = null },
        )
    }
}

private fun entryRows(store: AppStore, clock: AppClock): List<EntryRowData> = buildList {
    fun missingFile(files: List<StoredDocument>) = files.any { !store.fileFor(it).let { file -> file.isFile && file.canRead() } }
    store.dailyCloses.forEach { close ->
        val files = store.documents.filter { it.owner == DocumentOwner.DAILY_CLOSE && it.ownerId == close.id }
        val missing = missingFile(files)
        add(EntryRowData(
            "daily_${close.id}", close.id, DocumentOwner.DAILY_CLOSE, "Tagesabschluss", "daily", "Tagesabschluss",
            close.businessDate.formatGerman(), centsToGerman(close.zAmountCents),
            when { missing -> "Originaldatei fehlt"; files.isEmpty() -> "Z-Abschlag noch ergänzen"; else -> null },
            missing, files.isEmpty(), false, files,
            "Tagesabschluss ${close.businessDate.formatGerman()} ${close.note} ${files.joinToString { it.originalFileName }}",
            close.businessDate.toString(),
        ))
    }
    store.invoices.forEach { invoice ->
        val files = store.documents.filter { it.owner == DocumentOwner.INVOICE && it.ownerId == invoice.id }
        val signals = invoice.signals(clock.today(), store.reminderSettings.dueReminderDays)
        val type = if (invoice.invoiceDate != null) "Rechnung" else "Bestellung"
        val date = invoice.invoiceDate ?: invoice.orderDate
        val status = when {
            missingFile(files) -> "Originaldatei fehlt"
            signals.isOverdue -> "Zahlung überfällig"
            signals.isDone -> "Erledigt"
            invoice.invoiceDate == null -> "Rechnung noch nicht da"
            invoice.paidDate != null -> "Bezahlt · Lieferung noch offen"
            invoice.dueDate != null -> "Zahlung bis ${invoice.dueDate.formatGerman()}"
            else -> "Zahlung offen"
        }
        add(EntryRowData(
            "invoice_${invoice.id}", invoice.id, DocumentOwner.INVOICE, invoice.supplier,
            if (invoice.invoiceDate != null) "invoice" else "order", type, date?.formatGerman() ?: "Datum offen",
            invoice.amountCents?.let { centsToGerman(it) }, status,
            signals.isOverdue || missingFile(files), !signals.isDone, invoice.paidDate == null && invoice.invoiceDate != null,
            files, "$type ${invoice.supplier} ${invoice.invoiceNumber} ${invoice.note} ${files.joinToString { it.originalFileName }}",
            date?.toString() ?: invoice.createdAt.toLocalDate().toString(),
        ))
    }
    store.documents.filter { it.owner == DocumentOwner.GENERAL }.forEach { document ->
        val type = store.documentTypeLabel(document)
        val period = when {
            document.periodYear == null -> "Ohne Zeitraum"
            document.periodQuarter == null -> "Jahr ${document.periodYear}"
            else -> "${document.periodQuarter}. Quartal ${document.periodYear}"
        }
        val missing = missingFile(listOf(document))
        add(EntryRowData(
            "document_${document.id}", document.id, DocumentOwner.GENERAL, document.originalFileName,
            document.customTypeId?.let { "custom_$it" } ?: document.type.name, type, period, null,
            if (missing) "Originaldatei fehlt" else null, missing, missing, false, listOf(document),
            "$type ${document.originalFileName} ${document.note} $period", document.createdAt.toLocalDate().toString(),
        ))
    }
}.sortedWith(compareByDescending<EntryRowData> { it.sortDate }.thenBy { it.title })

@Composable
private fun EntryRow(entry: EntryRowData, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp).testTag("entry_${entry.key}"),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
        ) {
            Icon(painterResource(entryTypeIcon(entry.typeKey)), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (entry.owner == DocumentOwner.DAILY_CLOSE) entry.period else "${entry.typeLabel} · ${entry.period}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.amount?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                entry.status?.let { status ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (entry.critical) Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(status, style = MaterialTheme.typography.bodySmall, color = if (entry.critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.attachments.isNotEmpty()) {
                    Row(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = "${entry.attachments.size} ${if (entry.attachments.size == 1) "Anhang" else "Anhänge"}" },
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_attachment), null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entry.attachments.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
internal fun ChoiceListDialog(title: String, options: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options, key = { it.first }) { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = selected == key,
                            role = Role.RadioButton,
                            onClick = { onSelected(key) },
                        ).heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == key, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
