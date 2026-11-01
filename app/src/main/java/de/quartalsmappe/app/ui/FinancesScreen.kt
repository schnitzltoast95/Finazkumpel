package de.quartalsmappe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.DocumentOwner
import de.quartalsmappe.app.model.centsToGerman
import de.quartalsmappe.app.model.formatGerman
import de.quartalsmappe.app.time.AppClock

enum class FinanceSection(val label: String) {
    INCOME("Einnahmen"),
    EXPENSES("Ausgaben"),
}

@Composable
fun FinancesScreen(
    store: AppStore,
    clock: AppClock,
    modifier: Modifier = Modifier,
    initialSection: FinanceSection = FinanceSection.INCOME,
    onSectionChange: (FinanceSection) -> Unit = {},
    onDailyClose: (String?) -> Unit,
    onDailyHistory: () -> Unit,
    onAddExpense: () -> Unit,
    onInvoice: (String) -> Unit,
) {
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }

    Column(modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = section.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            FinanceSection.entries.forEach { item ->
                Tab(
                    selected = section == item,
                    onClick = {
                        section = item
                        onSectionChange(item)
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("finance_${item.name}"),
                    text = { Text(item.label, style = MaterialTheme.typography.titleSmall) },
                )
            }
        }
        when (section) {
            FinanceSection.INCOME -> IncomeListScreen(
                store = store,
                clock = clock,
                modifier = Modifier.weight(1f),
                onDailyClose = onDailyClose,
                onDailyHistory = onDailyHistory,
            )
            FinanceSection.EXPENSES -> InvoiceListScreen(
                store = store,
                clock = clock,
                modifier = Modifier.weight(1f),
                onAdd = onAddExpense,
                onSelect = onInvoice,
            )
        }
    }
}

@Composable
private fun IncomeListScreen(
    store: AppStore,
    clock: AppClock,
    modifier: Modifier,
    onDailyClose: (String?) -> Unit,
    onDailyHistory: () -> Unit,
) {
    val today = clock.today()
    val todayClose = store.dailyCloses.firstOrNull { it.businessDate == today }
    val recentCloses = store.dailyCloses
        .filter { it.businessDate != today }
        .sortedByDescending { it.businessDate }
        .take(5)

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("finance_income_list"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Deine Einnahmen werden als Tagesumsatz laut Z-Abschlag erfasst.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AppSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Heute · ${today.formatGerman()}", style = MaterialTheme.typography.titleMedium)
                    if (todayClose == null) {
                        Text("Für heute ist noch kein Tagesumsatz erfasst.")
                    } else {
                        Text(
                            centsToGerman(todayClose.zAmountCents),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Tagesabschluss erfasst", style = MaterialTheme.typography.bodyMedium)
                    }
                    AppPrimaryButton(
                        text = if (todayClose == null) "Tagesumsatz erfassen" else "Tagesumsatz ansehen",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDailyClose(todayClose?.id) },
                    )
                }
            }
        }
        if (recentCloses.isNotEmpty()) {
            item { SectionTitle("Letzte Tagesumsätze") }
            items(recentCloses, key = { it.id }) { close ->
                DailyCloseListCard(
                    close = close,
                    hasAttachment = store.documents.any {
                        it.owner == DocumentOwner.DAILY_CLOSE && it.ownerId == close.id
                    },
                    onSelect = { onDailyClose(it) },
                )
            }
        }
        item {
            TextButton(
                onClick = onDailyHistory,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Alle Tagesabschlüsse und Nachträge")
            }
        }
    }
}
