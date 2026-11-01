package de.quartalsmappe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.time.AppClock

/** Routes to the original invoice case; selecting never creates a second entry. */
@Composable
fun InvoiceSelectionScreen(
    store: AppStore,
    clock: AppClock,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onNewInvoice: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matching = store.invoices
        .filter { invoice ->
            query.isBlank() || invoice.supplier.contains(query.trim(), ignoreCase = true) ||
                invoice.invoiceNumber.contains(query.trim(), ignoreCase = true)
        }
        .sortedByDescending { it.updatedAt }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("invoice_selection"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Wähle die Bestellung oder Rechnung, zu der du etwas ergänzen möchtest. Alle Angaben und Dateien bleiben zusammen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (store.invoices.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Lieferant oder Rechnungsnummer suchen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (matching.isEmpty()) {
            item {
                Text(
                    if (store.invoices.isEmpty()) "Noch keine Bestellung oder Rechnung erfasst."
                    else "Kein passender Eintrag gefunden. Versuche einen anderen Suchbegriff.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(matching, key = { it.id }) { invoice ->
                InvoiceListCard(invoice, clock.today(), store.reminderSettings.dueReminderDays, onSelect)
            }
        }
        item {
            AppSecondaryButton(
                text = "Neue Rechnung erfassen",
                modifier = Modifier.fillMaxWidth(),
                onClick = onNewInvoice,
            )
        }
    }
}
