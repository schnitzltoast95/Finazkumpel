package de.quartalsmappe.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.R
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.InvoiceFilter
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.centsToGerman
import de.quartalsmappe.app.model.formatGerman
import de.quartalsmappe.app.model.parseEuroToCents
import de.quartalsmappe.app.model.rememberedSupplierNames
import de.quartalsmappe.app.model.signals
import de.quartalsmappe.app.time.AppClock
import de.quartalsmappe.app.util.openStoredDocument
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Composable
fun NewInvoiceChoiceScreen(
    modifier: Modifier = Modifier,
    onChoose: (InvoiceStartMode) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Starte mit der Rechnung oder einer Bestellung. Rechnung, Lieferung und Zahlung bleiben im selben Eintrag.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AppSurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChoose(InvoiceStartMode.INVOICE) },
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Rechnung liegt vor", style = MaterialTheme.typography.titleLarge)
                    Text("Rechnungsdaten eintragen und PDF oder Foto ablegen.")
                }
            }
        }
        item {
            AppSurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChoose(InvoiceStartMode.ORDER) },
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Nur Bestellung notieren", style = MaterialTheme.typography.titleLarge)
                    Text("Später Rechnung, Lieferung und Zahlung ergänzen.")
                }
            }
        }
    }
}

@Composable
fun InvoiceListScreen(
    store: AppStore,
    clock: AppClock,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(InvoiceFilter.OPEN) }
    val today = clock.today()
    val shown = store.invoices.filter { invoice ->
        when (filter) {
            InvoiceFilter.OPEN -> !invoice.signals(today, store.reminderSettings.dueReminderDays).isDone
            InvoiceFilter.DONE -> invoice.signals(today, store.reminderSettings.dueReminderDays).isDone
            InvoiceFilter.ALL -> true
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Rechnungen und Bestellungen für dein Geschäft, mit Zahlungsfrist und Bearbeitungsstand.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AppPrimaryButton(
                text = "Rechnung oder Bestellung erfassen",
                modifier = Modifier.fillMaxWidth(),
                onClick = onAdd,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InvoiceFilter.entries) { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = {
                            Text(
                                when (item) {
                                    InvoiceFilter.OPEN -> "Offen"
                                    InvoiceFilter.DONE -> "Erledigt"
                                    InvoiceFilter.ALL -> "Alle"
                                }
                            )
                        },
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    when (filter) {
                        InvoiceFilter.OPEN -> "Keine offenen Rechnungen oder Bestellungen."
                        InvoiceFilter.DONE -> "Noch keine Rechnungen vollständig erledigt."
                        InvoiceFilter.ALL -> "Noch keine Rechnungen oder Bestellungen erfasst."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(shown, key = { it.id }) { invoice ->
                InvoiceListCard(invoice, today, store.reminderSettings.dueReminderDays, onSelect)
            }
        }
    }
}

@Composable
internal fun InvoiceListCard(
    invoice: InvoiceCase,
    today: LocalDate,
    dueSoonDays: Int,
    onSelect: (String) -> Unit,
) {
    val signals = invoice.signals(today, dueSoonDays)
    AppSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onSelect(invoice.id) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(invoice.supplier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val invoiceDescription = if (invoice.invoiceDate != null) {
                        listOfNotNull(
                            "Rechnung",
                            invoice.invoiceNumber.takeIf { it.isNotBlank() },
                            "vom ${invoice.invoiceDate.formatGerman()}",
                        ).joinToString(" ")
                    } else {
                        invoice.orderDate?.let { "Bestellung vom ${it.formatGerman()}" } ?: "Bestellung"
                    }
                    Text(invoiceDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(centsToGerman(invoice.amountCents))
                }
                StatusPill(
                    signals.label,
                    when {
                        signals.isOverdue -> StatusTone.ERROR
                        signals.isDone -> StatusTone.GOOD
                        signals.label == "Bald fällig" -> StatusTone.WARNING
                        else -> StatusTone.NEUTRAL
                    },
                )
            }
            signals.messages.take(2).forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun InvoiceEditorScreen(
    store: AppStore,
    invoiceId: String?,
    startMode: InvoiceStartMode,
    clock: AppClock,
    modifier: Modifier = Modifier,
    onDirtyChange: (Boolean) -> Unit = {},
    onChooseExisting: (() -> Unit)? = null,
    onDone: (String) -> Unit,
) {
    val today = clock.today()
    val existing = store.invoices.firstOrNull { it.id == invoiceId }
    val effectiveMode = when {
        existing == null -> startMode
        existing.invoiceDate != null -> InvoiceStartMode.INVOICE
        else -> startMode
    }
    val draftId = rememberSaveable(invoiceId) { existing?.id ?: UUID.randomUUID().toString() }
    // Restore the original baseline too; restored edits must not become a clean draft.
    val initialDraft = rememberSaveable(invoiceId, saver = InvoiceDraftSaver) {
        InvoiceEditorDraft(
            supplier = existing?.supplier.orEmpty(),
            amount = existing?.amountCents?.let { "${it / 100},${(it % 100).toString().padStart(2, '0')}" }.orEmpty(),
            orderDate = if (existing != null) existing.orderDate else if (effectiveMode == InvoiceStartMode.ORDER) today else null,
            invoiceDate = if (existing != null) existing.invoiceDate else if (effectiveMode == InvoiceStartMode.INVOICE) today else null,
            invoiceNumber = existing?.invoiceNumber.orEmpty(),
            deliveryDate = existing?.deliveryDate,
            dueDate = existing?.dueDate,
            paidDate = existing?.paidDate,
            note = existing?.note.orEmpty(),
        )
    }
    var supplier by rememberSaveable(invoiceId) { mutableStateOf(initialDraft.supplier) }
    var amount by rememberSaveable(invoiceId) { mutableStateOf(initialDraft.amount) }
    var orderDate by rememberSaveable(invoiceId, saver = InvoiceDateStateSaver) { mutableStateOf(initialDraft.orderDate) }
    var invoiceDate by rememberSaveable(invoiceId, saver = InvoiceDateStateSaver) {
        mutableStateOf(
            initialDraft.invoiceDate ?: today.takeIf {
                existing != null && effectiveMode == InvoiceStartMode.INVOICE
            }
        )
    }
    var invoiceNumber by rememberSaveable(invoiceId) { mutableStateOf(initialDraft.invoiceNumber) }
    var deliveryDate by rememberSaveable(invoiceId, saver = InvoiceDateStateSaver) { mutableStateOf(initialDraft.deliveryDate) }
    var dueDate by rememberSaveable(invoiceId, saver = InvoiceDateStateSaver) { mutableStateOf(initialDraft.dueDate) }
    var paidDate by rememberSaveable(invoiceId, saver = InvoiceDateStateSaver) { mutableStateOf(initialDraft.paidDate) }
    var note by rememberSaveable(invoiceId) { mutableStateOf(initialDraft.note) }
    var more by rememberSaveable(invoiceId) { mutableStateOf(existing != null) }
    var selectedUri by rememberSaveable(invoiceId) { mutableStateOf<Uri?>(null) }
    var attachmentType by rememberSaveable(invoiceId) { mutableStateOf(DocumentType.INVOICE_EMAIL) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val errorItemIndex = 1 +
        (if (existing != null && existing.invoiceDate == null && effectiveMode == InvoiceStartMode.INVOICE) 1 else 0)
    LaunchedEffect(error) {
        if (error != null) listState.animateScrollToItem(errorItemIndex)
    }
    var pendingSave by remember { mutableStateOf<InvoiceCase?>(null) }

    val currentDraft = InvoiceEditorDraft(
        supplier, amount, orderDate, invoiceDate, invoiceNumber,
        deliveryDate, dueDate, paidDate, note,
    )
    val isDirty = currentDraft != initialDraft || selectedUri != null
    val reportDirty by rememberUpdatedState(onDirtyChange)
    LaunchedEffect(isDirty) { reportDirty(isDirty) }
    DisposableEffect(Unit) {
        onDispose { reportDirty(false) }
    }

    val duplicate = store.possibleDuplicate(supplier, invoiceNumber, draftId)
    val attachedCount = store.documents.count { it.owner == DocumentOwner.INVOICE && it.ownerId == draftId }
    val supplierSuggestions = remember(store.invoices, supplier, existing?.id) {
        store.invoices.rememberedSupplierNames(
            query = supplier,
            excludingInvoiceId = existing?.id,
        )
    }

    fun save(invoice: InvoiceCase) {
        runCatching { store.saveInvoice(invoice) }.onFailure {
            error = it.message ?: "Der Vorgang konnte nicht gespeichert werden"
            return
        }
        selectedUri?.let { uri ->
            runCatching {
                val date = invoiceDate ?: orderDate ?: today
                val q = Quarter.current(date)
                store.importDocument(
                    source = uri,
                    type = attachmentType,
                    owner = DocumentOwner.INVOICE,
                    ownerId = draftId,
                    periodYear = q.year,
                    periodQuarter = q.number,
                )
            }.onFailure {
                error = "Der Vorgang ist gespeichert, aber die Datei konnte nicht angehängt werden: " +
                    (it.message ?: "Bitte wähle die Datei erneut aus und versuche es noch einmal.")
                return
            }
        }
        error = null
        onDone(draftId)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("invoice_editor"),
        state = listState,
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (existing != null && existing.invoiceDate == null && effectiveMode == InvoiceStartMode.INVOICE) {
            item {
                Text(
                    "Du ergänzt die Rechnung zur Bestellung bei ${existing.supplier}. Die Bestellung und ihre Dateien bleiben erhalten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                "Mit * markierte Angaben werden zum Speichern benötigt. Alles andere kannst du später ergänzen.",
                modifier = Modifier.testTag("invoice_required_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let { message -> item { MissingHint(message) } }
        if (duplicate != null) {
            item { MissingHint("Diese Rechnungsnummer ist bei ${duplicate.supplier} bereits vorhanden. Bitte kurz prüfen.") }
        }
        item {
            SectionTitle(
                "Grundangaben",
                "Wer hat die Rechnung oder Bestellung gestellt? Der Betrag darf offenbleiben, wenn er noch nicht feststeht.",
            )
        }
        if (existing == null && effectiveMode == InvoiceStartMode.INVOICE && onChooseExisting != null) {
            item {
                AppSecondaryButton(
                    text = "Zu vorhandener Bestellung hinzufügen",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onChooseExisting,
                )
            }
        }
        item {
            SupplierField(
                value = supplier,
                suggestions = supplierSuggestions,
                onValueChange = {
                    supplier = it
                    error = null
                },
            )
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; error = null },
                label = { Text("Betrag (optional)") },
                suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (effectiveMode == InvoiceStartMode.INVOICE) {
            item {
                SectionTitle(
                    "Rechnungsangaben",
                    "Das Rechnungsdatum wird benötigt. Nummer und Zahlungsfrist helfen beim späteren Wiederfinden und Erinnern.",
                )
            }
            item {
                DateField("Rechnung vom *", invoiceDate, { invoiceDate = it }, clock, allowEmpty = false)
            }
            item { DateField("Bezahlen bis (optional)", dueDate, { dueDate = it }, clock) }
            item {
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text("Rechnungsnummer (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SectionTitle(
                    "Rechnungsbeleg",
                    "PDF oder Foto sind optional und bleiben sicher mit diesem Vorgang verbunden.",
                )
                Spacer(Modifier.height(8.dp))
                InvoiceAttachmentTypePicker(attachmentType, { attachmentType = it })
                AttachmentPicker(
                    label = "Rechnung (optional)",
                    selectedUri = selectedUri,
                    onSelected = { selectedUri = it },
                    existingCount = attachedCount,
                )
            }
        } else {
            item {
                SectionTitle(
                    "Bestellangaben",
                    "Das Bestelldatum wird benötigt. Rechnung und Lieferung kannst du später ergänzen.",
                )
            }
            item { DateField("Bestellt am *", orderDate, { orderDate = it }, clock, allowEmpty = false) }
        }
        item {
            TextButton(onClick = { more = !more }, modifier = Modifier.fillMaxWidth()) {
                Text(if (more) "Optionale Angaben ausblenden" else "Optionale Angaben ergänzen")
            }
        }
        if (more) {
            item {
                SectionTitle(
                    "Weitere Angaben",
                    "Lieferung, Zahlung und eine eigene Notiz sind optional.",
                )
            }
            if (effectiveMode == InvoiceStartMode.INVOICE) {
                item { DateField("Bestellt am (optional)", orderDate, { orderDate = it }, clock) }
            } else {
                item { DateField("Rechnung vom (optional)", invoiceDate, { invoiceDate = it }, clock) }
                item { DateField("Bezahlen bis (optional)", dueDate, { dueDate = it }, clock) }
                item {
                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it },
                        label = { Text("Rechnungsnummer (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    InvoiceAttachmentTypePicker(attachmentType, { attachmentType = it })
                    AttachmentPicker(
                        label = "Rechnung (optional)",
                        selectedUri = selectedUri,
                        onSelected = { selectedUri = it },
                        existingCount = attachedCount,
                    )
                }
            }
            item { DateField("Geliefert am (optional)", deliveryDate, { deliveryDate = it }, clock) }
            item { DateField("Bezahlt am (optional)", paidDate, { paidDate = it }, clock) }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            if (existing != null) {
                Text(
                    "Beim Speichern wird die bisherige Version zusätzlich geschützt aufbewahrt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrimarySaveButton(if (existing == null) "Vorgang speichern" else "Änderungen speichern") {
                val amountCents = if (amount.isBlank()) null else parseEuroToCents(amount)
                when {
                    supplier.isBlank() -> error = "Lieferant ist ein Pflichtfeld. Bitte trage einen Namen ein."
                    amount.isNotBlank() && (amountCents == null || amountCents <= 0) ->
                        error = "Der Betrag ist nicht gültig. Bitte gib eine positive Zahl ein."
                    (if (effectiveMode == InvoiceStartMode.INVOICE) invoiceDate else orderDate) == null ->
                        error = "Das Rechnungs- oder Bestelldatum ist ein Pflichtfeld. Bitte wähle ein Datum."
                    else -> {
                        val invoice = InvoiceCase(
                            id = draftId,
                            supplier = supplier.trim(),
                            amountCents = amountCents,
                            orderDate = orderDate,
                            invoiceDate = invoiceDate,
                            invoiceNumber = invoiceNumber.trim(),
                            deliveryDate = deliveryDate,
                            dueDate = dueDate,
                            paidDate = paidDate,
                            note = note.trim(),
                            documentIds = existing?.documentIds.orEmpty(),
                            createdAt = existing?.createdAt ?: LocalDateTime.now(),
                        )
                        if (existing == null) save(invoice) else pendingSave = invoice
                    }
                }
            }
        }
    }

    pendingSave?.let { invoice ->
        ConfirmEditDialog(
            itemName = "Rechnungsvorgang",
            onConfirm = {
                pendingSave = null
                save(invoice)
            },
            onDismiss = { pendingSave = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierField(
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val menuExpanded = expanded && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { expanded = it && suggestions.isNotEmpty() },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Lieferant") },
            supportingText = { Text("Pflichtfeld") },
            trailingIcon = {
                if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = suggestions.isNotEmpty())
                .testTag("invoice_supplier"),
        )
        ExposedDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEachIndexed { index, name ->
                DropdownMenuItem(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("supplier_suggestion_$index"),
                    onClick = {
                        onValueChange(name)
                        expanded = false
                    },
                    text = {
                        Text(
                            name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

private data class InvoiceEditorDraft(
    val supplier: String,
    val amount: String,
    val orderDate: LocalDate?,
    val invoiceDate: LocalDate?,
    val invoiceNumber: String,
    val deliveryDate: LocalDate?,
    val dueDate: LocalDate?,
    val paidDate: LocalDate?,
    val note: String,
)

private val InvoiceDraftSaver = listSaver<InvoiceEditorDraft, String>(
    save = {
        listOf(
            it.supplier, it.amount, it.orderDate?.toString().orEmpty(),
            it.invoiceDate?.toString().orEmpty(), it.invoiceNumber,
            it.deliveryDate?.toString().orEmpty(), it.dueDate?.toString().orEmpty(),
            it.paidDate?.toString().orEmpty(), it.note,
        )
    },
    restore = {
        InvoiceEditorDraft(
            supplier = it[0], amount = it[1], orderDate = it[2].asDraftDate(),
            invoiceDate = it[3].asDraftDate(), invoiceNumber = it[4],
            deliveryDate = it[5].asDraftDate(), dueDate = it[6].asDraftDate(),
            paidDate = it[7].asDraftDate(), note = it[8],
        )
    },
)

private val InvoiceDateStateSaver = listSaver<MutableState<LocalDate?>, String>(
    save = { listOf(it.value?.toString().orEmpty()) },
    restore = { mutableStateOf(it.single().asDraftDate()) },
)

private fun String.asDraftDate(): LocalDate? = takeIf { it.isNotEmpty() }?.let(LocalDate::parse)

@Composable
fun InvoiceDetailScreen(
    store: AppStore,
    invoiceId: String,
    clock: AppClock,
    modifier: Modifier = Modifier,
    onDirtyChange: (Boolean) -> Unit = {},
    onEdit: () -> Unit,
    onAddInvoice: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val today = clock.today()
    val context = LocalContext.current
    val invoice = store.invoices.firstOrNull { it.id == invoiceId }
    if (invoice == null) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            MissingHint("Der Vorgang wurde nicht gefunden.")
            AppPrimaryButton(text = "Zurück", onClick = onBack)
        }
        return
    }
    val signals = invoice.signals(today, store.reminderSettings.dueReminderDays)
    var attachmentUri by rememberSaveable(invoiceId) { mutableStateOf<Uri?>(null) }
    var attachmentType by rememberSaveable(invoiceId) { mutableStateOf(DocumentType.INVOICE_PAPER) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var pendingUpdate by remember(invoiceId) { mutableStateOf<InvoiceCase?>(null) }
    var updateError by remember(invoiceId) { mutableStateOf<String?>(null) }
    val attachments = store.documents.filter { it.owner == DocumentOwner.INVOICE && it.ownerId == invoiceId }
    val reportDirty by rememberUpdatedState(onDirtyChange)
    LaunchedEffect(attachmentUri) { reportDirty(attachmentUri != null) }
    DisposableEffect(Unit) {
        onDispose { reportDirty(false) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("invoice_detail"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AppSurfaceCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text(invoice.supplier, style = MaterialTheme.typography.headlineSmall)
                            Text(centsToGerman(invoice.amountCents), style = MaterialTheme.typography.titleLarge)
                        }
                        StatusPill(
                            signals.label,
                            when {
                                signals.isOverdue -> StatusTone.ERROR
                                signals.isDone -> StatusTone.GOOD
                                signals.label == "Bald fällig" -> StatusTone.WARNING
                                else -> StatusTone.NEUTRAL
                            },
                        )
                    }
                    signals.messages.forEach { Text("• $it") }
                }
            }
        }
        if (invoice.invoiceDate == null) {
            item {
                AppPrimaryButton(
                    text = "Rechnung zur Bestellung ergänzen",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAddInvoice ?: onEdit,
                )
            }
        }
        item {
            SectionTitle("Zeitlicher Verlauf")
            Spacer(Modifier.height(6.dp))
            AppSurfaceCard {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    invoice.orderDate?.let { TimelineRow("Bestellt am", it) }
                    invoice.invoiceDate?.let { TimelineRow("Rechnung vom", it) }
                    invoice.deliveryDate?.let { TimelineRow("Geliefert am", it) }
                    invoice.dueDate?.let { TimelineRow("Bezahlen bis", it) }
                    invoice.paidDate?.let { TimelineRow("Bezahlt am", it) }
                }
            }
        }
        item {
            updateError?.let { MissingHint(it) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (invoice.deliveryDate == null) {
                    AppSecondaryButton(
                        text = "Heute geliefert",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { pendingUpdate = invoice.copy(deliveryDate = today) },
                    )
                }
                if (invoice.paidDate == null) {
                    AppPrimaryButton(
                        text = "Als bezahlt markieren",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { pendingUpdate = invoice.copy(paidDate = today) },
                    )
                }
            }
            TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Alle Angaben bearbeiten") }
        }
        item {
            openError?.let { MissingHint(it) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Dateien")
                Spacer(Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_attachment), contentDescription = "Angehängte Dateien", modifier = Modifier.size(18.dp))
                Text(attachments.size.toString(), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
            if (attachments.isEmpty()) {
                Text("Noch keine Datei angehängt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(attachments, key = { it.id }) { document ->
            AppSurfaceCard(
                modifier = Modifier.fillMaxWidth().testTag("invoice_attachment_${document.id}"),
                onClick = {
                    openError = if (openStoredDocument(context, store, document)) null
                    else "Die Datei konnte nicht geöffnet werden. Prüfe, ob das Original vorhanden und eine passende Anzeige-App installiert ist."
                },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_attachment), contentDescription = null, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(document.type.label, style = MaterialTheme.typography.labelLarge)
                        Text(document.originalFileName)
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Datei öffnen")
                }
            }
        }
        item {
            InvoiceAttachmentTypePicker(attachmentType, { attachmentType = it })
            AttachmentPicker(
                label = "Weitere Datei hinzufügen",
                selectedUri = attachmentUri,
                onSelected = { attachmentUri = it },
                existingCount = 0,
            )
            attachmentError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (attachmentUri != null) {
                AppPrimaryButton(
                    text = "Dokument speichern",
                    onClick = {
                        val date = invoice.deliveryDate ?: invoice.invoiceDate ?: today
                        val q = Quarter.current(date)
                        runCatching {
                            store.importDocument(
                                source = attachmentUri!!,
                                type = attachmentType,
                                owner = DocumentOwner.INVOICE,
                                ownerId = invoice.id,
                                periodYear = q.year,
                                periodQuarter = q.number,
                            )
                        }.onSuccess {
                            attachmentUri = null
                            attachmentError = null
                        }.onFailure {
                            attachmentError = it.message ?: "Dokument konnte nicht gespeichert werden"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (invoice.note.isNotBlank()) {
            item {
                SectionTitle("Notiz")
                Text(invoice.note)
            }
        }
    }

    pendingUpdate?.let { updatedInvoice ->
        ConfirmEditDialog(
            itemName = "Rechnungsvorgang",
            onConfirm = {
                pendingUpdate = null
                runCatching { store.saveInvoice(updatedInvoice) }
                    .onSuccess { updateError = null }
                    .onFailure { updateError = it.message ?: "Die Änderung konnte nicht gespeichert werden" }
            },
            onDismiss = { pendingUpdate = null },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun InvoiceAttachmentTypePicker(selected: DocumentType, onSelect: (DocumentType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Art der Datei", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DocumentType.INVOICE_EMAIL, DocumentType.INVOICE_PAPER, DocumentType.DELIVERY_NOTE).forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    label = { Text(type.label) },
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(label: String, date: LocalDate?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            date?.formatGerman() ?: "Noch offen",
            color = if (date == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

