package de.quartalsmappe.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.R
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.DocumentType

internal val captureDocumentTypes = listOf(
    DocumentType.BANK_STATEMENT, DocumentType.CASH_EXPORT, DocumentType.RENT,
    DocumentType.ELECTRICITY, DocumentType.GAS, DocumentType.PAYROLL,
    DocumentType.GEMA, DocumentType.BROADCAST_FEE, DocumentType.BGW, DocumentType.OTHER,
)

@Composable
fun CaptureScreen(
    store: AppStore,
    modifier: Modifier = Modifier,
    onDailyClose: () -> Unit,
    onInvoice: () -> Unit,
    onOrder: () -> Unit,
    onAttachExisting: () -> Unit,
    onDocument: (DocumentType, String?) -> Unit,
) {
    var showCustomType by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("capture_list"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            CaptureTile(
                "Tagesabschluss", documentTypeIcon(DocumentType.Z_REPORT), Modifier.testTag("capture_daily"),
                subtitle = "Tagesumsatz mit Z-Abschlag", onClick = onDailyClose,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CaptureTile("Rechnung", documentTypeIcon(DocumentType.INVOICE_EMAIL), Modifier.weight(1f).testTag("capture_invoice"), onClick = onInvoice)
                CaptureTile("Bestellung", documentTypeIcon(DocumentType.DELIVERY_NOTE), Modifier.weight(1f).testTag("capture_order"), onClick = onOrder)
            }
        }
        item {
            CaptureTile(
                "Zu einem Eintrag ergänzen", R.drawable.ic_type_paperclip, Modifier.testTag("capture_attach"),
                subtitle = "Rechnung zur Bestellung, Papierkopie oder Lieferschein", onClick = onAttachExisting,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Weitere Arten", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCustomType = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("capture_custom_add")) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Eigene Art")
                }
            }
        }
        items(captureDocumentTypes.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { type ->
                    CaptureTile(type.label, documentTypeIcon(type), Modifier.weight(1f).testTag("capture_${type.name}")) {
                        onDocument(type, null)
                    }
                }
            }
        }
        if (store.customDocumentTypes.isNotEmpty()) {
            item { SectionTitle("Eigene Arten") }
            items(store.customDocumentTypes, key = { "custom_${it.id}" }) { custom ->
                CaptureTile(
                    custom.name, documentTypeIcon(DocumentType.OTHER), Modifier.testTag("capture_custom_${custom.id}"),
                    subtitle = if (custom.usesAnnualPeriod) "Für ein ganzes Jahr" else "Für ein Quartal",
                ) { onDocument(DocumentType.OTHER, custom.id) }
            }
        }
    }
    if (showCustomType) {
        CustomDocumentTypeDialog(
            store = store,
            onDismiss = { showCustomType = false },
            onCreated = { id -> showCustomType = false; onDocument(DocumentType.OTHER, id) },
        )
    }
}

@Composable
private fun CaptureTile(
    title: String,
    icon: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    AppSurfaceCard(
        modifier = modifier.semantics { role = Role.Button },
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        val contentModifier = Modifier.fillMaxWidth().heightIn(min = 102.dp).padding(16.dp)
        if (subtitle == null) {
            Column(
                contentModifier,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CaptureTileIcon(icon)
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        } else {
            Row(
                contentModifier,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CaptureTileIcon(icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CaptureTileIcon(icon: Int) {
    Icon(painterResource(icon), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun CaptureActionRow(title: String, subtitle: String?, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CustomDocumentTypeDialog(store: AppStore, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var annual by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eigene Art anlegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null }, label = { Text("Name der Art") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("custom_type_name"),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { annual = !annual }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = annual, onCheckedChange = { annual = it })
                    Text("Gilt für ein ganzes Jahr", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Sonst wählst du beim Erfassen das passende Quartal.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching { store.addCustomDocumentType(name, annual) }
                    .onSuccess { onCreated(it.id) }
                    .onFailure { error = it.message ?: "Die Art konnte nicht angelegt werden." }
            }, modifier = Modifier.testTag("custom_type_save")) { Text("Art anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
