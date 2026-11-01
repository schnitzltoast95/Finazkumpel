package de.quartalsmappe.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.R
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.StoredDocument
import de.quartalsmappe.app.model.formatGerman
import de.quartalsmappe.app.time.AppClock
import de.quartalsmappe.app.time.SystemAppClock
import de.quartalsmappe.app.util.openStoredDocument

private val generalDocumentTypes = listOf(
    DocumentType.BANK_STATEMENT,
    DocumentType.CASH_EXPORT,
    DocumentType.RENT,
    DocumentType.ELECTRICITY,
    DocumentType.GAS,
    DocumentType.PAYROLL,
    DocumentType.GEMA,
    DocumentType.BROADCAST_FEE,
    DocumentType.BGW,
    DocumentType.OTHER,
)

@Composable
fun DocumentsScreen(
    store: AppStore,
    modifier: Modifier = Modifier,
    onAdd: (DocumentType?) -> Unit,
    onOpenExport: () -> Unit,
    onOpenDaily: (String) -> Unit,
    onOpenInvoice: (String) -> Unit,
    onEditDocument: (String) -> Unit,
) {
    var ownerFilter by rememberSaveable { mutableStateOf<DocumentOwner?>(null) }
    val documents = store.documents.filter { ownerFilter == null || it.owner == ownerFilter }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Hier findest du alle Originaldateien: Z-Belege, Rechnungen und weitere Unterlagen. Prüfe vor dem Teilen, was im Quartalspaket enthalten ist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AppPrimaryButton("Export prüfen", Modifier.fillMaxWidth(), onClick = onOpenExport)
        }
        item {
            AppSecondaryButton("Unterlage hinzufügen", Modifier.fillMaxWidth()) { onAdd(null) }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = ownerFilter == null, onClick = { ownerFilter = null }, label = { Text("Alle") }) }
                items(DocumentOwner.entries) { owner ->
                    FilterChip(
                        selected = ownerFilter == owner,
                        onClick = { ownerFilter = owner },
                        label = { Text(when (owner) {
                            DocumentOwner.DAILY_CLOSE -> "Z-Belege"
                            DocumentOwner.INVOICE -> "Ausgabenbelege"
                            DocumentOwner.GENERAL -> "Weitere Unterlagen"
                        }) },
                    )
                }
            }
        }
        item { SectionTitle("${documents.size} ${if (documents.size == 1) "Datei" else "Dateien"}") }
        if (documents.isEmpty()) {
            item {
                Text(
                    if (ownerFilter == null) "Noch keine Dateien abgelegt. Du kannst Unterlagen hier hinzufügen oder Belege direkt beim Tagesabschluss und bei Ausgaben hinterlegen."
                    else "Für diese Auswahl sind noch keine Dateien abgelegt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(documents, key = { it.id }) { document ->
                DocumentLibraryRow(store, document, onOpenDaily, onOpenInvoice, onEditDocument)
            }
        }
    }
}

@Composable
internal fun DocumentLibraryRow(
    store: AppStore,
    document: StoredDocument,
    onOpenDaily: (String) -> Unit,
    onOpenInvoice: (String) -> Unit,
    onEditDocument: (String) -> Unit,
    archivePath: String? = null,
) {
    val context = LocalContext.current
    var previewError by remember(document.id) { mutableStateOf<String?>(null) }
    val available = store.fileFor(document).let { it.isFile && it.canRead() }
    val close = store.dailyCloses.firstOrNull { it.id == document.ownerId }
    val invoice = store.invoices.firstOrNull { it.id == document.ownerId }
    val canEdit = when (document.owner) {
        DocumentOwner.DAILY_CLOSE -> close != null
        DocumentOwner.INVOICE -> invoice != null
        DocumentOwner.GENERAL -> true
    }
    val sourceLabel = when (document.owner) {
        DocumentOwner.DAILY_CLOSE -> close?.let { "Tagesabschluss · ${it.businessDate.formatGerman()}" } ?: "Zugehöriger Tagesabschluss nicht gefunden"
        DocumentOwner.INVOICE -> invoice?.let { "Rechnung / Bestellung · ${it.supplier}" } ?: "Zugehöriger Eintrag nicht gefunden"
        DocumentOwner.GENERAL -> when {
            document.periodYear == null -> "Ohne Ablagezeitraum"
            document.periodQuarter == null -> "Jahresunterlage ${document.periodYear}"
            else -> "${document.periodQuarter}. Quartal ${document.periodYear}"
        }
    }
    AppSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("document_${document.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(store.documentTypeLabel(document), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(document.originalFileName, style = MaterialTheme.typography.titleMedium)
            Text(sourceLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (document.note.isNotBlank()) Text(document.note, style = MaterialTheme.typography.bodySmall)
            archivePath?.let { Text("Im Paket: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!available) MissingHint("Originaldatei fehlt. Lies eine passende Sicherung unter Optionen ein.")
            previewError?.let { MissingHint(it) }
            BoxWithConstraints {
                val preview: @Composable (Modifier) -> Unit = { buttonModifier ->
                    TextButton(
                        modifier = buttonModifier.heightIn(min = 48.dp).testTag("preview_${document.id}"),
                        enabled = available,
                        onClick = {
                            previewError = if (openStoredDocument(context, store, document)) null
                            else "Die Vorschau konnte nicht geöffnet werden. Prüfe, ob eine passende App für diese Datei installiert ist."
                        },
                    ) { Text("Vorschau") }
                }
                val edit: @Composable (Modifier) -> Unit = { buttonModifier ->
                    TextButton(
                        modifier = buttonModifier.heightIn(min = 48.dp).testTag("edit_document_${document.id}"),
                        enabled = canEdit,
                        onClick = {
                            when (document.owner) {
                                DocumentOwner.DAILY_CLOSE -> close?.let { onOpenDaily(it.id) }
                                DocumentOwner.INVOICE -> invoice?.let { onOpenInvoice(it.id) }
                                DocumentOwner.GENERAL -> onEditDocument(document.id)
                            }
                        },
                    ) { Text("Angaben bearbeiten") }
                }
                if (maxWidth / LocalDensity.current.fontScale < 300.dp) {
                    Column { preview(Modifier.fillMaxWidth()); edit(Modifier.fillMaxWidth()) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        preview(Modifier.weight(1f)); edit(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentAddScreen(
    store: AppStore,
    initialType: DocumentType?,
    initialQuarter: Quarter?,
    clock: AppClock,
    modifier: Modifier = Modifier,
    onDirtyChange: (Boolean) -> Unit = {},
    onDone: () -> Unit,
    initialCustomTypeId: String? = null,
) {
    val currentQuarter = Quarter.current(clock.today())
    val suggestedQuarter = initialQuarter ?: currentQuarter
    var type by rememberSaveable { mutableStateOf(initialType ?: DocumentType.BANK_STATEMENT) }
    var customTypeId by rememberSaveable { mutableStateOf(initialCustomTypeId) }
    var quarterYear by rememberSaveable { mutableIntStateOf(suggestedQuarter.year) }
    var quarterNumber by rememberSaveable { mutableIntStateOf(suggestedQuarter.number) }
    var annualYear by rememberSaveable { mutableIntStateOf(currentQuarter.year) }
    var showPeriodDialog by rememberSaveable { mutableStateOf(false) }
    var pendingYear by rememberSaveable { mutableIntStateOf(suggestedQuarter.year) }
    var pendingQuarter by rememberSaveable { mutableIntStateOf(suggestedQuarter.number) }
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(error) {
        if (error != null) listState.animateScrollToItem(0)
    }
    val initialDraftType = rememberSaveable { initialType ?: DocumentType.BANK_STATEMENT }
    val initialDraftQuarterYear = rememberSaveable { suggestedQuarter.year }
    val initialDraftQuarterNumber = rememberSaveable { suggestedQuarter.number }
    val initialAnnualYear = rememberSaveable { currentQuarter.year }
    val reportDirty by rememberUpdatedState(onDirtyChange)
    LaunchedEffect(type, customTypeId, quarterYear, quarterNumber, annualYear, uri, note) {
        reportDirty(
            type != initialDraftType || customTypeId != initialCustomTypeId || quarterYear != initialDraftQuarterYear ||
                quarterNumber != initialDraftQuarterNumber || annualYear != initialAnnualYear ||
                uri != null || note.isNotEmpty()
        )
    }
    DisposableEffect(Unit) {
        onDispose { reportDirty(false) }
    }
    val isAnnual = store.customType(customTypeId)?.usesAnnualPeriod ?: type.usesAnnualPeriod
    val typeLabel = store.customType(customTypeId)?.name ?: type.label
    val selectedYear = if (isAnnual) annualYear else quarterYear
    val selectedQuarter = if (isAnnual) null else quarterNumber
    val selectedPeriodLabel = if (isAnnual) {
        "$selectedYear (Jahresunterlage)"
    } else {
        Quarter(quarterYear, quarterNumber).label
    }
    val isCurrentPeriod = if (isAnnual) {
        annualYear == currentQuarter.year
    } else {
        quarterYear == suggestedQuarter.year && quarterNumber == suggestedQuarter.number
    }

    if (showPeriodDialog) {
        PeriodSelectionDialog(
            isAnnual = isAnnual,
            currentQuarter = currentQuarter,
            pendingYear = pendingYear,
            pendingQuarter = pendingQuarter,
            onYearChange = { pendingYear = it },
            onQuarterChange = { pendingQuarter = it },
            onConfirm = {
                if (isAnnual) {
                    annualYear = pendingYear
                } else {
                    quarterYear = pendingYear
                    quarterNumber = pendingQuarter
                }
                showPeriodDialog = false
            },
            onDismiss = { showPeriodDialog = false },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Foto oder Datei auswählen und dem passenden Zeitraum zuordnen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let { item { MissingHint(it) } }
        item {
            DocumentTypeSelector(store, type, customTypeId) { selectedType, selectedCustom ->
                type = selectedType; customTypeId = selectedCustom
            }
        }
        item {
            PeriodSummaryCard(
                label = selectedPeriodLabel,
                isAnnual = isAnnual,
                isCurrent = isCurrentPeriod,
                automaticHint = when {
                    isAnnual -> "Automatisch: aktuelles Jahr"
                    initialQuarter != null -> "Vorgeschlagen: gewähltes Quartal"
                    else -> "Automatisch: aktuelles Quartal"
                },
                onChange = {
                    pendingYear = selectedYear
                    pendingQuarter = selectedQuarter ?: currentQuarter.number
                    showPeriodDialog = true
                },
                onReset = {
                    if (isAnnual) {
                        annualYear = currentQuarter.year
                    } else {
                        quarterYear = suggestedQuarter.year
                        quarterNumber = suggestedQuarter.number
                    }
                },
            )
        }
        item {
            AttachmentPicker(
                label = "Dokument",
                selectedUri = uri,
                onSelected = { uri = it; error = null },
                allowCamera = true,
            )
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notiz (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            AppSurfaceCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Kontrolle vor dem Speichern", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "$typeLabel · $selectedPeriodLabel",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Die Unterlage wird genau diesem Zeitraum zugeordnet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            PrimarySaveButton(
                if (isAnnual) "Für $selectedYear speichern"
                else "Für $quarterNumber. Quartal $quarterYear speichern"
            ) {
                val selected = uri
                if (selected == null) {
                    error = "Bitte ein Foto oder eine Datei auswählen"
                    return@PrimarySaveButton
                }
                runCatching {
                    store.importDocument(
                        source = selected,
                        type = type,
                        owner = DocumentOwner.GENERAL,
                        ownerId = null,
                        periodYear = selectedYear,
                        periodQuarter = selectedQuarter,
                        note = note.trim(),
                        customTypeId = customTypeId,
                    )
                }.onSuccess {
                    reportDirty(false)
                    onDone()
                }
                    .onFailure { error = it.message ?: "Dokument konnte nicht gespeichert werden" }
            }
        }
    }
}

@Composable
fun DocumentMetadataEditor(
    store: AppStore,
    documentId: String,
    modifier: Modifier = Modifier,
    clock: AppClock = SystemAppClock,
    onDirtyChange: (Boolean) -> Unit = {},
    onDone: () -> Unit,
) {
    val document = store.documents.firstOrNull { it.id == documentId }
    val reportDirty by rememberUpdatedState(onDirtyChange)
    DisposableEffect(Unit) { onDispose { reportDirty(false) } }
    if (document == null || document.owner != DocumentOwner.GENERAL) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            MissingHint("Dieser Eintrag ist nicht verfügbar. Belege zu Tagesabschlüssen und Rechnungen bearbeitest du beim jeweiligen Eintrag.")
        }
        return
    }
    val initialType = rememberSaveable(documentId) { document.type }
    val initialCustomTypeId by rememberSaveable(documentId) { mutableStateOf(document.customTypeId) }
    val initialYear = rememberSaveable(documentId) { (document.periodYear ?: clock.today().year).toString() }
    val initialQuarter = rememberSaveable(documentId) { document.periodQuarter ?: Quarter.current(clock.today()).number }
    val initialNote = rememberSaveable(documentId) { document.note }
    var type by rememberSaveable(documentId) { mutableStateOf(initialType) }
    var customTypeId by rememberSaveable(documentId) { mutableStateOf(initialCustomTypeId) }
    var year by rememberSaveable(documentId) { mutableStateOf(initialYear) }
    var quarter by rememberSaveable(documentId) { mutableIntStateOf(initialQuarter) }
    var note by rememberSaveable(documentId) { mutableStateOf(initialNote) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmSave by rememberSaveable { mutableStateOf(false) }
    val isAnnual = store.customType(customTypeId)?.usesAnnualPeriod ?: type.usesAnnualPeriod
    val dirty = type != initialType || customTypeId != initialCustomTypeId || year != initialYear || quarter != initialQuarter || note != initialNote
    LaunchedEffect(dirty) { reportDirty(dirty) }

    fun save() {
        val parsedYear = year.toIntOrNull()
        if (parsedYear == null || parsedYear !in 1..9999) {
            error = "Bitte ein gültiges Jahr eingeben."
            return
        }
        runCatching {
            store.updateDocumentMetadata(document.id, type, parsedYear, if (isAnnual) null else quarter, note, customTypeId)
        }.onSuccess {
            reportDirty(false)
            onDone()
        }.onFailure { error = it.message ?: "Die Angaben konnten nicht gespeichert werden." }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OriginalDocumentButton(store, document)
            Text(
                "Art, Zeitraum und Notiz kannst du hier ändern. Die Originaldatei bleibt unverändert.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            DocumentTypeSelector(store, type, customTypeId) { selectedType, selectedCustom ->
                type = selectedType; customTypeId = selectedCustom
            }
        }
        item {
            OutlinedTextField(
                value = year,
                onValueChange = { value -> year = value.filter(Char::isDigit).take(4); error = null },
                label = { Text(if (isAnnual) "Ablagejahr" else "Jahr") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!isAnnual) {
            item {
                Text("Quartal", style = MaterialTheme.typography.labelLarge)
                Column {
                    (1..4).toList().chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { option ->
                                FilterChip(selected = quarter == option, onClick = { quarter = option }, label = { Text("$option. Quartal") }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            item { Text("Diese Jahresunterlage wird in jedem Quartal des gewählten Jahres angeboten.", style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notiz (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { item { MissingHint(it) } }
        item {
            PrimarySaveButton("Änderungen speichern") {
                if (year.toIntOrNull()?.let { it in 1..9999 } != true) error = "Bitte ein gültiges Jahr eingeben."
                else if (dirty) confirmSave = true
                else onDone()
            }
        }
    }
    if (confirmSave) {
        ConfirmEditDialog(
            itemName = "Unterlage",
            onConfirm = { confirmSave = false; save() },
            onDismiss = { confirmSave = false },
        )
    }
}

@Composable
private fun OriginalDocumentButton(store: AppStore, document: StoredDocument) {
    val context = LocalContext.current
    var error by remember(document.id) { mutableStateOf<String?>(null) }
    val available = store.fileFor(document).let { it.isFile && it.canRead() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                error = if (runCatching { openStoredDocument(context, store, document) }.getOrDefault(false)) null
                else "Die Datei konnte nicht geöffnet werden. Installiere eine passende App für dieses Dateiformat und versuche es erneut."
            },
            enabled = available,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("open_original_${document.id}"),
        ) {
            Icon(painterResource(R.drawable.ic_attachment), contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(document.originalFileName, style = MaterialTheme.typography.titleSmall)
                Text("Originaldatei öffnen", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!available) MissingHint("Die Originaldatei fehlt. Lies eine passende Sicherung unter Optionen ein.")
        error?.let { MissingHint(it) }
    }
}

@Composable
private fun DocumentTypeSelector(
    store: AppStore,
    type: DocumentType,
    customTypeId: String?,
    onSelected: (DocumentType, String?) -> Unit,
) {
    var showSelection by rememberSaveable { mutableStateOf(false) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Art", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { showSelection = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("document_type_selector"),
        ) {
            Text(store.customType(customTypeId)?.name ?: type.label, modifier = Modifier.weight(1f))
            Text("Ändern", style = MaterialTheme.typography.labelLarge)
        }
    }
    if (showSelection) {
        ChoiceListDialog(
            title = "Art auswählen",
            options = captureDocumentTypes.map { it.name to it.label } +
                store.customDocumentTypes.map { "custom_${it.id}" to it.name } + listOf("new_custom" to "Eigene Art anlegen"),
            selected = customTypeId?.let { "custom_$it" } ?: type.name,
            onSelected = { key ->
                showSelection = false
                when {
                    key == "new_custom" -> showCustom = true
                    key.startsWith("custom_") -> onSelected(DocumentType.OTHER, key.removePrefix("custom_"))
                    else -> onSelected(DocumentType.valueOf(key), null)
                }
            },
            onDismiss = { showSelection = false },
        )
    }
    if (showCustom) {
        CustomDocumentTypeDialog(store, onDismiss = { showCustom = false }) { id ->
            showCustom = false
            onSelected(DocumentType.OTHER, id)
        }
    }
}

@Composable
private fun PeriodSummaryCard(
    label: String,
    isAnnual: Boolean,
    isCurrent: Boolean,
    automaticHint: String,
    onChange: () -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(if (isAnnual) "Ablagejahr" else "Ablagezeitraum", style = MaterialTheme.typography.labelLarge)
        AppSurfaceCard(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
            borderColor = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            } else {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
            },
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isCurrent) {
                                automaticHint
                            } else {
                                "Anderer Zeitraum – bitte vor dem Speichern prüfen"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onChange, modifier = Modifier.testTag("document_period_selector")) { Text("Ändern") }
                }
                if (!isCurrent) {
                    TextButton(onClick = onReset) {
                        Text(if (isAnnual) "Aktuelles Jahr verwenden" else "Vorgeschlagenen Zeitraum verwenden")
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSelectionDialog(
    isAnnual: Boolean,
    currentQuarter: Quarter,
    pendingYear: Int,
    pendingQuarter: Int,
    onYearChange: (Int) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAnnual) "Anderes Jahr wählen" else "Anderen Zeitraum wählen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isAnnual) {
                        "Ändere das Jahr nur, wenn die Unterlage nicht zum aktuellen Jahr gehört."
                    } else {
                        "Ändere den Zeitraum nur, wenn die Unterlage wirklich zu einem anderen Quartal gehört."
                    }
                )
                Text("Jahr", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items((currentQuarter.year - 2..currentQuarter.year + 1).toList()) { option ->
                        FilterChip(
                            selected = pendingYear == option,
                            onClick = { onYearChange(option) },
                            label = { Text(option.toString()) },
                        )
                    }
                }
                if (!isAnnual) {
                    Text("Quartal", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (1..2).forEach { option ->
                            PeriodQuarterChip(
                                option = option,
                                selected = pendingQuarter == option,
                                onClick = { onQuarterChange(option) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (3..4).forEach { option ->
                            PeriodQuarterChip(
                                option = option,
                                selected = pendingQuarter == option,
                                onClick = { onQuarterChange(option) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (isAnnual) "Jahr übernehmen" else "Zeitraum übernehmen")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun PeriodQuarterChip(
    option: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = when (option) {
        1 -> "Jan–März"
        2 -> "Apr–Juni"
        3 -> "Juli–Sept"
        else -> "Okt–Dez"
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text("$option. Quartal · $months") },
    )
}

