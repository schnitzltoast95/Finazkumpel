package de.quartalsmappe.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.R
import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.centsToGerman
import de.quartalsmappe.app.model.formatGerman
import de.quartalsmappe.app.model.parseEuroToCents
import de.quartalsmappe.app.time.AppClock
import de.quartalsmappe.app.util.openStoredDocument
import java.time.LocalDate
import java.util.UUID

private val DailyCloseDateSaver = Saver<LocalDate, String>(
    save = { it.toString() },
    restore = { LocalDate.parse(it) },
)

@Composable
fun DailyCloseEditor(
    store: AppStore,
    closeId: String?,
    clock: AppClock,
    modifier: Modifier = Modifier,
    onDirtyChange: (Boolean) -> Unit = {},
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val existing = store.dailyCloses.firstOrNull { it.id == closeId }
    val draftId = rememberSaveable(closeId) { existing?.id ?: UUID.randomUUID().toString() }
    val initialDate = rememberSaveable(closeId, saver = DailyCloseDateSaver) { existing?.businessDate ?: clock.today() }
    val initialAmount = rememberSaveable(closeId) {
        existing?.zAmountCents?.let { "${it / 100},${(it % 100).toString().padStart(2, '0')}" }.orEmpty()
    }
    val initialNote = rememberSaveable(closeId) { existing?.note.orEmpty() }
    var date by rememberSaveable(closeId, stateSaver = DailyCloseDateSaver) { mutableStateOf(initialDate) }
    var amount by rememberSaveable(closeId) { mutableStateOf(initialAmount) }
    var note by rememberSaveable(closeId) { mutableStateOf(initialNote) }
    var selectedUri by rememberSaveable(closeId) { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<DailyClose?>(null) }
    val attachedDocuments = store.documents.filter { it.owner == DocumentOwner.DAILY_CLOSE && it.ownerId == draftId }
    val reportDirty by rememberUpdatedState(onDirtyChange)
    LaunchedEffect(date, amount, note, selectedUri) {
        reportDirty(date != initialDate || amount != initialAmount || note != initialNote || selectedUri != null)
    }
    DisposableEffect(Unit) {
        onDispose { reportDirty(false) }
    }

    fun save(close: DailyClose) {
        runCatching { store.saveDailyClose(close) }.onFailure {
            error = it.message ?: "Der Tagesabschluss konnte nicht gespeichert werden"
            return
        }
        selectedUri?.let { uri ->
            runCatching {
                val q = Quarter.current(date)
                store.importDocument(
                    source = uri,
                    type = DocumentType.Z_REPORT,
                    owner = DocumentOwner.DAILY_CLOSE,
                    ownerId = draftId,
                    periodYear = q.year,
                    periodQuarter = q.number,
                )
            }.onFailure {
                error = "Der Tagesabschluss ist gespeichert, aber der Beleg konnte nicht angehängt werden: " +
                    (it.message ?: "Bitte wähle die Datei erneut aus und versuche es noch einmal.")
                return
            }
        }
        error = null
        reportDirty(false)
        onDone()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                "Trage den Tagesumsatz laut Z-Abschlag ein. Betrag und Beleg gehören zum selben Tagesabschluss.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            DateField(
                label = "Geschäftstag",
                value = date,
                onValueChange = { it?.let { chosen -> date = chosen } },
                clock = clock,
                allowEmpty = false,
            )
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; error = null },
                label = { Text("Umsatz laut Z-Abschlag") },
                suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message -> ({ Text(message) }) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            AttachmentPicker(
                label = "Z-Abschlag als Beleg (optional)",
                selectedUri = selectedUri,
                onSelected = { selectedUri = it },
                existingCount = attachedDocuments.size,
            )
        }
        items(attachedDocuments, key = { it.id }) { document ->
            AppSurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    openError = if (openStoredDocument(context, store, document)) null
                    else "Die Datei konnte nicht geöffnet werden. Prüfe, ob das Original vorhanden und eine passende Anzeige-App installiert ist."
                },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_attachment), contentDescription = null, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Z-Abschlag", style = MaterialTheme.typography.labelLarge)
                        Text(document.originalFileName)
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Datei öffnen")
                }
            }
        }
        openError?.let { message -> item { MissingHint(message) } }
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
            if (existing != null) {
                Text(
                    "Beim Speichern wird die bisherige Version zusätzlich geschützt aufbewahrt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrimarySaveButton(if (existing == null) "Tagesabschluss speichern" else "Änderungen speichern") {
                val cents = parseEuroToCents(amount)
                if (cents == null || cents < 0) {
                    error = "Bitte einen gültigen Betrag eingeben"
                    return@PrimarySaveButton
                }
                val close = DailyClose(
                    id = draftId,
                    businessDate = date,
                    zAmountCents = cents,
                    note = note.trim(),
                    documentIds = existing?.documentIds.orEmpty(),
                    createdAt = existing?.createdAt ?: java.time.LocalDateTime.now(),
                )
                if (existing == null) save(close) else pendingSave = close
            }
        }
    }

    pendingSave?.let { close ->
        ConfirmEditDialog(
            itemName = "Tagesabschluss",
            onConfirm = {
                pendingSave = null
                save(close)
            },
            onDismiss = { pendingSave = null },
        )
    }
}

@Composable
fun DailyHistoryScreen(
    store: AppStore,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit = {},
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Hier findest du deine erfassten Tagesumsätze mit den zugehörigen Z-Belegen. Für einen früheren Tag wähle beim Nachtragen den Geschäftstag aus.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AppPrimaryButton(
                text = "Tagesabschluss nachtragen",
                modifier = Modifier.fillMaxWidth(),
                onClick = onAdd,
            )
        }
        if (store.dailyCloses.isEmpty()) {
            item { MissingHint("Noch keine Tagesabschlüsse erfasst.") }
        } else {
            items(store.dailyCloses, key = { it.id }) { close ->
                val attachmentCount = store.documents.count {
                    it.owner == DocumentOwner.DAILY_CLOSE && it.ownerId == close.id
                }
                DailyCloseListCard(close, attachmentCount > 0, onSelect, attachmentCount)
            }
        }
    }
}

@Composable
internal fun DailyCloseListCard(
    close: DailyClose,
    hasAttachment: Boolean,
    onSelect: (String) -> Unit,
    attachmentCount: Int = if (hasAttachment) 1 else 0,
) {
    AppSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onSelect(close.id) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth / LocalDensity.current.fontScale < 300.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(close.businessDate.formatGerman(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            centsToGerman(close.zAmountCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            close.businessDate.formatGerman(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            centsToGerman(close.zAmountCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (attachmentCount > 0) R.drawable.ic_attachment else R.drawable.ic_warning),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (attachmentCount > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    if (attachmentCount > 0) "$attachmentCount ${if (attachmentCount == 1) "Datei" else "Dateien"}" else "Z-Beleg ergänzen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

