package de.quartalsmappe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp as lerpTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.R
import de.quartalsmappe.app.model.DocumentType
import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.centsToGerman
import de.quartalsmappe.app.model.formatGerman
import de.quartalsmappe.app.model.signals
import de.quartalsmappe.app.time.AppClock
import de.quartalsmappe.app.time.SystemAppClock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

enum class RootTab(val label: String, val iconRes: Int) {
    HOME("Start", R.drawable.ic_nav_home),
    CAPTURE("Erfassen", R.drawable.ic_add),
    ENTRIES("Einträge", R.drawable.ic_nav_documents),
    SETTINGS("Optionen", R.drawable.ic_settings),
}

enum class InvoiceStartMode { INVOICE, ORDER }

sealed interface Overlay {
    data object DailyHistory : Overlay
    data class DailyEdit(val id: String? = null) : Overlay
    data object NewInvoiceChoice : Overlay
    data object InvoiceSelection : Overlay
    data class InvoiceEdit(val id: String? = null, val mode: InvoiceStartMode = InvoiceStartMode.INVOICE) : Overlay
    data class InvoiceDetail(val id: String) : Overlay
    data class DocumentAdd(
        val initialType: DocumentType? = null,
        val initialQuarter: Quarter? = null,
        val initialCustomTypeId: String? = null,
    ) : Overlay
    data class DocumentEdit(val id: String) : Overlay
    data class QuarterExport(val initialQuarter: Quarter? = null) : Overlay
}

private data class AppRoute(
    val tab: RootTab,
    val overlay: Overlay? = null,
    val stateKey: String = UUID.randomUUID().toString(),
)

// Preserve the back stack and each screen's quarter/filter/draft state through editor visits
// and Activity recreation. Only route identifiers are saved, never financial records.
private val RouteStackSaver = listSaver<List<AppRoute>, String>(
    save = { routes -> routes.map { route ->
        JSONObject().apply {
            put("tab", route.tab.name)
            put("key", route.stateKey)
            when (val overlay = route.overlay) {
                null -> put("screen", "root")
                Overlay.DailyHistory -> put("screen", "daily_history")
                is Overlay.DailyEdit -> { put("screen", "daily_edit"); put("id", overlay.id) }
                Overlay.NewInvoiceChoice -> put("screen", "expense_choice")
                Overlay.InvoiceSelection -> put("screen", "invoice_selection")
                is Overlay.InvoiceEdit -> {
                    put("screen", "expense_edit"); put("id", overlay.id); put("mode", overlay.mode.name)
                }
                is Overlay.InvoiceDetail -> { put("screen", "expense_detail"); put("id", overlay.id) }
                is Overlay.DocumentEdit -> { put("screen", "document_edit"); put("id", overlay.id) }
                is Overlay.DocumentAdd -> {
                    put("screen", "document_add"); put("type", overlay.initialType?.name)
                    put("customType", overlay.initialCustomTypeId)
                    overlay.initialQuarter?.let { put("year", it.year); put("quarter", it.number) }
                }
                is Overlay.QuarterExport -> {
                    put("screen", "export")
                    overlay.initialQuarter?.let { put("year", it.year); put("quarter", it.number) }
                }
            }
        }.toString()
    } },
    restore = { saved -> saved.map { value ->
        val json = JSONObject(value)
        val id = if (json.has("id")) json.getString("id") else null
        val quarter = if (json.has("year")) Quarter(json.getInt("year"), json.getInt("quarter")) else null
        val overlay = when (json.getString("screen")) {
            "daily_history" -> Overlay.DailyHistory
            "daily_edit" -> Overlay.DailyEdit(id)
            "expense_choice" -> Overlay.NewInvoiceChoice
            "invoice_selection" -> Overlay.InvoiceSelection
            "expense_edit" -> Overlay.InvoiceEdit(id, InvoiceStartMode.valueOf(json.getString("mode")))
            "expense_detail" -> Overlay.InvoiceDetail(requireNotNull(id))
            "document_edit" -> Overlay.DocumentEdit(requireNotNull(id))
            "document_add" -> Overlay.DocumentAdd(
                if (json.has("type")) DocumentType.valueOf(json.getString("type")) else null, quarter,
                if (json.has("customType")) json.getString("customType") else null,
            )
            "export" -> Overlay.QuarterExport(quarter)
            else -> null
        }
        val restoredTab = when (json.getString("tab")) {
            "FINANCES" -> RootTab.CAPTURE
            "DOCUMENTS" -> RootTab.ENTRIES
            else -> RootTab.valueOf(json.getString("tab"))
        }
        AppRoute(restoredTab, overlay, json.getString("key"))
    } },
)

@Composable
fun QuartalsmappeApp(
    store: AppStore,
    clock: AppClock = SystemAppClock,
) {
    val greetingRotation = remember { HomeGreetingRotation() }
    val screenState = rememberSaveableStateHolder()
    var routes by rememberSaveable(stateSaver = RouteStackSaver) { mutableStateOf(listOf(AppRoute(RootTab.HOME))) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val currentRoute = routes.last()
    val tab = currentRoute.tab
    val currentOverlay = currentRoute.overlay

    fun replaceRoutes(next: List<AppRoute>) {
        val retainedKeys = next.map { it.stateKey }.toSet()
        routes.filterNot { it.stateKey in retainedKeys }.forEach { screenState.removeState(it.stateKey) }
        routes = next
    }

    fun navigate(action: () -> Unit) {
        if (hasUnsavedChanges) pendingNavigation = action else action()
    }

    fun back() {
        hasUnsavedChanges = false
        replaceRoutes(if (routes.size > 1) routes.dropLast(1) else listOf(AppRoute(RootTab.HOME)))
    }

    fun open(destination: Overlay, owner: RootTab = tab) {
        routes = routes + AppRoute(owner, destination)
    }

    fun selectTab(destination: RootTab) {
        hasUnsavedChanges = false
        replaceRoutes(if (destination == RootTab.HOME) listOf(AppRoute(RootTab.HOME))
        else listOf(AppRoute(RootTab.HOME), AppRoute(destination)))
    }

    BackHandler(enabled = routes.size > 1) { navigate { back() } }
    if (store.loadError != null && tab != RootTab.SETTINGS) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Gespeicherte Daten nicht lesbar") },
            text = { Text(store.loadError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { selectTab(RootTab.SETTINGS) }) {
                    Text("Zur Wiederherstellung")
                }
            },
        )
    }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    Scaffold(
        modifier = Modifier.background(Brush.verticalGradient(listOf(
            MaterialTheme.colorScheme.background,
            lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer, 0.10f),
        ))),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            if (currentOverlay != null || tab != RootTab.HOME) {
                AppPageHeader(
                    title = when (currentOverlay) {
                        Overlay.DailyHistory -> "Tagesabschlüsse"
                        is Overlay.DailyEdit -> "Tagesabschluss"
                        Overlay.NewInvoiceChoice -> "Erfassen"
                        Overlay.InvoiceSelection -> "Eintrag auswählen"
                        is Overlay.InvoiceEdit -> {
                            val existing = store.invoices.firstOrNull { it.id == currentOverlay.id }
                            when {
                                existing != null -> "Vorgang bearbeiten"
                                currentOverlay.mode == InvoiceStartMode.ORDER -> "Bestellung notieren"
                                else -> "Rechnung eintragen"
                            }
                        }
                        is Overlay.InvoiceDetail -> if (store.invoices.firstOrNull { it.id == currentOverlay.id }?.invoiceDate == null) "Bestellung" else "Rechnung"
                        is Overlay.DocumentAdd -> "Unterlage hinzufügen"
                        is Overlay.DocumentEdit -> "Unterlage bearbeiten"
                        is Overlay.QuarterExport -> "Quartalspaket"
                        null -> when (tab) {
                            RootTab.CAPTURE -> "Erfassen"
                            RootTab.ENTRIES -> tab.label
                            RootTab.SETTINGS -> "Einstellungen"
                            else -> tab.label
                        }
                    },
                    onBack = if (currentOverlay != null) ({ navigate { back() } }) else null,
                )
            }
        },
        bottomBar = {
            if (!imeVisible) {
                AppBottomNavigation(tab) { destination ->
                    if (destination != tab || currentOverlay != null) navigate { selectTab(destination) }
                }
            }
        },
    ) { padding ->
        val screenModifier = Modifier.padding(padding).imePadding()
        screenState.SaveableStateProvider(currentRoute.stateKey) {
        when (currentOverlay) {
            Overlay.DailyHistory -> DailyHistoryScreen(
                store,
                screenModifier,
                onAdd = { open(Overlay.DailyEdit()) },
                onSelect = { open(Overlay.DailyEdit(it)) },
            )
            is Overlay.DailyEdit -> DailyCloseEditor(
                store = store,
                closeId = currentOverlay.id,
                clock = clock,
                modifier = screenModifier,
                onDirtyChange = { hasUnsavedChanges = it },
                onDone = { back() },
            )
            Overlay.NewInvoiceChoice -> NewInvoiceChoiceScreen(
                modifier = screenModifier,
                onChoose = { open(Overlay.InvoiceEdit(mode = it)) },
            )
            Overlay.InvoiceSelection -> InvoiceSelectionScreen(
                store = store,
                clock = clock,
                modifier = screenModifier,
                onSelect = { open(Overlay.InvoiceDetail(it)) },
                onNewInvoice = { open(Overlay.InvoiceEdit()) },
            )
            is Overlay.InvoiceEdit -> InvoiceEditorScreen(
                store = store,
                invoiceId = currentOverlay.id,
                startMode = currentOverlay.mode,
                clock = clock,
                modifier = screenModifier,
                onDirtyChange = { hasUnsavedChanges = it },
                onDone = { savedId ->
                    hasUnsavedChanges = false
                    replaceRoutes(routes.filterNot {
                        it.overlay is Overlay.InvoiceEdit || it.overlay is Overlay.InvoiceDetail ||
                            it.overlay == Overlay.NewInvoiceChoice || it.overlay == Overlay.InvoiceSelection
                    } + AppRoute(tab, Overlay.InvoiceDetail(savedId)))
                },
                onChooseExisting = { navigate { open(Overlay.InvoiceSelection) } },
            )
            is Overlay.InvoiceDetail -> InvoiceDetailScreen(
                store = store,
                invoiceId = currentOverlay.id,
                clock = clock,
                modifier = screenModifier,
                onDirtyChange = { hasUnsavedChanges = it },
                onEdit = { navigate { open(Overlay.InvoiceEdit(currentOverlay.id,
                    if (store.invoices.firstOrNull { it.id == currentOverlay.id }?.invoiceDate == null)
                        InvoiceStartMode.ORDER else InvoiceStartMode.INVOICE)) } },
                onAddInvoice = { navigate { open(Overlay.InvoiceEdit(currentOverlay.id, InvoiceStartMode.INVOICE)) } },
                onBack = { navigate { back() } },
            )
            is Overlay.DocumentAdd -> DocumentAddScreen(
                store = store,
                initialType = currentOverlay.initialType,
                initialQuarter = currentOverlay.initialQuarter,
                initialCustomTypeId = currentOverlay.initialCustomTypeId,
                clock = clock,
                modifier = screenModifier,
                onDirtyChange = { hasUnsavedChanges = it },
                onDone = { back() },
            )
            is Overlay.DocumentEdit -> DocumentMetadataEditor(
                store = store,
                documentId = currentOverlay.id,
                clock = clock,
                modifier = screenModifier,
                onDirtyChange = { hasUnsavedChanges = it },
                onDone = { back() },
            )
            is Overlay.QuarterExport -> QuarterScreen(
                store = store,
                clock = clock,
                modifier = screenModifier,
                initialQuarter = currentOverlay.initialQuarter,
                onOpenDaily = { open(Overlay.DailyEdit(it)) },
                onOpenInvoice = { open(Overlay.InvoiceDetail(it)) },
                onEditDocument = { open(Overlay.DocumentEdit(it)) },
                onAddDocument = { type, quarter -> open(Overlay.DocumentAdd(type, quarter)) },
            )
            null -> when (tab) {
                RootTab.HOME -> HomeScreen(
                    store = store,
                    clock = clock,
                    greetingRotation = greetingRotation,
                    modifier = screenModifier,
                    onDailyClose = { id -> open(Overlay.DailyEdit(id)) },
                    onDailyHistory = { open(Overlay.DailyHistory) },
                    onAddInvoice = { open(Overlay.InvoiceEdit(), RootTab.CAPTURE) },
                    onAddOrder = { open(Overlay.InvoiceEdit(mode = InvoiceStartMode.ORDER), RootTab.CAPTURE) },
                    onAllCapture = { selectTab(RootTab.CAPTURE) },
                    onAddCashExport = { quarter ->
                        open(Overlay.DocumentAdd(DocumentType.CASH_EXPORT, quarter), RootTab.CAPTURE)
                    },
                    onOpenSettings = { selectTab(RootTab.SETTINGS) },
                    onOpenInvoices = { selectTab(RootTab.ENTRIES) },
                    onOpenDocuments = { selectTab(RootTab.ENTRIES) },
                    onOpenExport = { open(Overlay.QuarterExport()) },
                    onInvoice = { open(Overlay.InvoiceDetail(it), RootTab.ENTRIES) },
                )
                RootTab.CAPTURE -> CaptureScreen(
                    store = store,
                    modifier = screenModifier,
                    onDailyClose = { open(Overlay.DailyEdit(store.dailyCloses.firstOrNull { it.businessDate == clock.today() }?.id)) },
                    onInvoice = { open(Overlay.InvoiceEdit()) },
                    onOrder = { open(Overlay.InvoiceEdit(mode = InvoiceStartMode.ORDER)) },
                    onAttachExisting = { open(Overlay.InvoiceSelection) },
                    onDocument = { type, customTypeId -> open(Overlay.DocumentAdd(type, initialCustomTypeId = customTypeId)) },
                )
                RootTab.ENTRIES -> EntriesScreen(
                    store = store,
                    clock = clock,
                    modifier = screenModifier,
                    onOpenExport = { open(Overlay.QuarterExport()) },
                    onOpenDaily = { open(Overlay.DailyEdit(it)) },
                    onOpenInvoice = { open(Overlay.InvoiceDetail(it)) },
                    onEditDocument = { open(Overlay.DocumentEdit(it)) },
                )
                RootTab.SETTINGS -> SettingsScreen(
                    store,
                    screenModifier,
                    onDirtyChange = { hasUnsavedChanges = it },
                    onDone = { hasUnsavedChanges = false },
                )
            }
        }
        }
    }

    pendingNavigation?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingNavigation = null },
            title = { Text("Eingaben verwerfen?") },
            text = { Text("Deine Änderungen sind noch nicht gespeichert.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingNavigation = null
                    hasUnsavedChanges = false
                    // A confirmed discard must also clear drafts when the next route is a child.
                    screenState.removeState(currentRoute.stateKey)
                    action()
                }) {
                    Text("Verwerfen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNavigation = null }) { Text("Weiter bearbeiten") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBottomNavigation(selectedTab: RootTab, onSelect: (RootTab) -> Unit) {
    // Selection/focus belongs to the whole tab, not a second capsule around its icon.
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
    NavigationBar(
        modifier = Modifier.testTag("main_navigation"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
    ) {
        RootTab.entries.forEach { destination ->
            val selected = destination == selectedTab
            val interactionSource = remember { MutableInteractionSource() }
            val focused by interactionSource.collectIsFocusedAsState()
            NavigationBarItem(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    .clip(AppCardShape)
                    .background(when {
                        selected && focused -> lerp(MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer, 0.12f)
                        selected -> MaterialTheme.colorScheme.secondaryContainer
                        focused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        else -> Color.Transparent
                    })
                    .testTag("nav_${destination.name}"),
                selected = selected,
                interactionSource = interactionSource,
                onClick = { onSelect(destination) },
                icon = { Icon(painterResource(destination.iconRes), if (selected) null else destination.label,
                    Modifier.size(if (selected) 28.dp else 24.dp)) },
                label = { Text(destination.label, fontWeight = FontWeight.SemiBold) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
    }
}

@Composable
private fun HomeScreen(
    store: AppStore,
    clock: AppClock,
    greetingRotation: HomeGreetingRotation,
    modifier: Modifier = Modifier,
    onDailyClose: (String?) -> Unit,
    onDailyHistory: () -> Unit,
    onAddInvoice: () -> Unit,
    onAddOrder: () -> Unit,
    onAllCapture: () -> Unit,
    onAddCashExport: (Quarter) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInvoices: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenExport: () -> Unit,
    onInvoice: (String) -> Unit,
) {
    var greeting by rememberSaveable { mutableStateOf(greetingRotation.next()) }
    SideEffect { greetingRotation.markShown(greeting) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Keep the welcome stable while reading and through activity recreation.
        // A new Home visit selects its text above; returning from the background selects here.
        var firstStart = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (firstStart) firstStart = false
                else greeting = greetingRotation.next(previous = greeting)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val today = clock.today()
    val todayClose = store.dailyCloses.firstOrNull { it.businessDate == today }
    val openInvoices = store.invoices.filterNot { it.signals(today, store.reminderSettings.dueReminderDays).isDone }
    val unpaidInvoices = store.invoices.filter { it.paidDate == null }
    val deadlineHorizon = today.plusDays(store.reminderSettings.dueReminderDays.toLong())
    val overdueInvoices = unpaidInvoices.filter { it.dueDate?.isBefore(today) == true }
    val dueSoonInvoices = unpaidInvoices.filter {
        val due = it.dueDate
        due != null && !due.isBefore(today) && !due.isAfter(deadlineHorizon)
    }
    val missingDueDateInvoices = unpaidInvoices.filter { it.invoiceDate != null && it.dueDate == null }
    val nextDueDate = unpaidInvoices
        .mapNotNull { it.dueDate }
        .filterNot { it.isBefore(today) }
        .minOrNull()
    val exportQuarter = Quarter.current(today).previous()
    val hasCashExport = store.documents.any {
        it.type == DocumentType.CASH_EXPORT &&
            it.periodYear == exportQuarter.year &&
            it.periodQuarter == exportQuarter.number
    }
    val reminderTime = LocalTime.of(store.reminderSettings.hour, store.reminderSettings.minute)
    val dailyCloseIsDue = todayClose == null &&
        today.dayOfWeek in store.reminderSettings.enabledDays &&
        !LocalTime.now().isBefore(reminderTime)
    val mascotIsBusy = overdueInvoices.isNotEmpty() ||
        dueSoonInvoices.isNotEmpty() ||
        missingDueDateInvoices.isNotEmpty() ||
        dailyCloseIsDue
    val lastBackupAt = store.lastBackupAt
    val backupReady = store.backupFolderUri != null && store.lastBackupError == null
    val attentionItems = openInvoices.take(3).map { invoice ->
        val signals = invoice.signals(today, store.reminderSettings.dueReminderDays)
        HomeOverviewItem(
            title = invoice.supplier,
            context = "${signals.label} · ${centsToGerman(invoice.amountCents)}",
            tone = if (signals.isOverdue) StatusTone.ERROR else StatusTone.WARNING,
            onClick = { onInvoice(invoice.id) },
        )
    }
    val overviewItems = buildList {
        if (attentionItems.isEmpty()) {
            add(deadlineOverviewItem(today, deadlineHorizon, overdueInvoices, dueSoonInvoices,
                missingDueDateInvoices.size, nextDueDate, onOpenInvoices))
        } else {
            addAll(attentionItems)
            if (openInvoices.size > attentionItems.size) {
                add(HomeOverviewItem(
                    title = "${openInvoices.size - attentionItems.size} weitere offene Vorgänge",
                    context = "Alle offenen Rechnungen und Bestellungen ansehen",
                    tone = StatusTone.WARNING,
                    onClick = onOpenInvoices,
                ))
            }
        }
        add(HomeOverviewItem("Quartalspaket vorbereiten", "Inhalt prüfen und für die Finanzberaterin exportieren",
            StatusTone.NEUTRAL, onOpenExport))
        add(HomeOverviewItem(
            if (hasCashExport) "Kassenexport hinterlegt" else "Kassenexport fehlt",
            "Für ${exportQuarter.label} · kein Abgabetermin bekannt",
            if (hasCashExport) StatusTone.GOOD else StatusTone.WARNING,
            if (hasCashExport) onOpenDocuments else ({ onAddCashExport(exportQuarter) }),
        ))
        add(HomeOverviewItem(
            when {
                store.lastBackupError != null -> "Datensicherung prüfen"
                backupReady && store.hasPendingBackup -> "Sicherung steht aus"
                backupReady -> "Datensicherung aktiv"
                else -> "Datensicherung einrichten"
            },
            when {
                store.lastBackupError != null -> "Letzter Sicherungsversuch fehlgeschlagen"
                backupReady && store.hasPendingBackup -> "Änderungen warten auf die nächste automatische Sicherung"
                backupReady && lastBackupAt != null -> "Zuletzt ${lastBackupAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))} Uhr gesichert"
                backupReady -> "Aktueller Sicherungsstand im gewählten Rhythmus"
                else -> "Einmalige Einrichtung · unabhängig vom Quartal"
            },
            when {
                store.lastBackupError != null -> StatusTone.ERROR
                backupReady && store.hasPendingBackup -> StatusTone.NEUTRAL
                backupReady -> StatusTone.GOOD
                else -> StatusTone.WARNING
            },
            onOpenSettings,
        ))
    }

    var headerOffsetPx by rememberSaveable { mutableFloatStateOf(0f) }
    val collapseRangePx = with(LocalDensity.current) { 136.dp.toPx() }
    val nestedScrollConnection = remember(collapseRangePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                headerOffsetPx = (headerOffsetPx + available.y).coerceIn(-collapseRangePx, 0f)
                return Offset.Zero
            }
        }
    }
    val expandedFraction = (1f + headerOffsetPx / collapseRangePx).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        HomeWelcomeHeader(
            greeting = greeting,
            companionRes = companionDrawable(store.appearanceSettings.companion, mascotIsBusy),
            companionLabel = store.appearanceSettings.companion.label,
            expandedFraction = expandedFraction,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("home_list"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
            DailyCloseSummaryCard(
                close = todayClose,
                today = today,
                reminderTime = reminderTime,
                reminderEnabled = today.dayOfWeek in store.reminderSettings.enabledDays,
                reminderIsDue = dailyCloseIsDue,
                onOpen = onDailyClose,
                onHistory = onDailyHistory,
            )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Schnell erfassen",
                        modifier = Modifier.weight(1f).testTag("home_quick_heading"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onAllCapture,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("home_all_types")) {
                        Text("Alle Arten")
                        Spacer(Modifier.width(4.dp))
                        Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                BoxWithConstraints {
                    if (maxWidth / LocalDensity.current.fontScale < 340.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionCard(
                                title = "Rechnung",
                                iconRes = documentTypeIcon(DocumentType.INVOICE_EMAIL),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onAddInvoice,
                            )
                            QuickActionCard(
                                title = "Bestellung",
                                iconRes = documentTypeIcon(DocumentType.DELIVERY_NOTE),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onAddOrder,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionCard(
                                title = "Rechnung",
                                iconRes = documentTypeIcon(DocumentType.INVOICE_EMAIL),
                                modifier = Modifier.weight(1f),
                                onClick = onAddInvoice,
                            )
                            QuickActionCard(
                                title = "Bestellung",
                                iconRes = documentTypeIcon(DocumentType.DELIVERY_NOTE),
                                modifier = Modifier.weight(1f),
                                onClick = onAddOrder,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    if (attentionItems.isEmpty()) "Auf einen Blick" else "Aktuelles",
                    modifier = Modifier.testTag("home_overview_heading"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                HomeOverviewCard(overviewItems)
            }
        }
    }
}

@Composable
private fun HomeWelcomeHeader(
    greeting: String,
    companionRes: Int,
    companionLabel: String,
    expandedFraction: Float,
) {
    val companionSize = (80f + 108f * expandedFraction).dp
    val topPadding = (8f + 20f * expandedFraction).dp
    val headingStyle = lerpTextStyle(
        MaterialTheme.typography.headlineSmall,
        MaterialTheme.typography.displaySmall,
        expandedFraction,
    )
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = topPadding, bottom = 8.dp)
    ) {
        val stacked = (maxWidth - companionSize - 8.dp) / LocalDensity.current.fontScale < 140.dp
        val welcome: @Composable (Modifier) -> Unit = { textModifier ->
            Column(textModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Hallo, Svetlana!",
                    modifier = Modifier.testTag("home_greeting"),
                    style = headingStyle,
                )
                Text(
                    greeting,
                    modifier = Modifier.testTag("home_welcome").graphicsLayer {
                        alpha = (expandedFraction * 1.5f).coerceIn(0.35f, 1f)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        val companion: @Composable () -> Unit = {
            Image(
                painter = painterResource(companionRes),
                contentDescription = "Dein Begleiter: $companionLabel",
                modifier = Modifier.size(companionSize).testTag("home_companion"),
                contentScale = ContentScale.Fit,
            )
        }
        if (stacked) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                welcome(Modifier.fillMaxWidth())
                companion()
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                welcome(Modifier.weight(1f))
                companion()
            }
        }
    }
}

private data class HomeOverviewItem(
    val title: String,
    val context: String,
    val tone: StatusTone,
    val onClick: () -> Unit,
)

private fun deadlineOverviewItem(
    today: LocalDate,
    horizon: LocalDate,
    overdueInvoices: List<InvoiceCase>,
    dueSoonInvoices: List<InvoiceCase>,
    missingDueDateCount: Int,
    nextDueDate: LocalDate?,
    onClick: () -> Unit,
): HomeOverviewItem {
    val oldestOverdue = overdueInvoices.mapNotNull { it.dueDate }.minOrNull()
    val nextSoon = dueSoonInvoices.mapNotNull { it.dueDate }.minOrNull()
    return when {
        overdueInvoices.isNotEmpty() -> HomeOverviewItem(
            title = "${overdueInvoices.size} ${if (overdueInvoices.size == 1) "Zahlungsfrist" else "Zahlungsfristen"} überfällig",
            context = "Älteste Frist: ${oldestOverdue?.formatGerman() ?: "unbekannt"} · Stand ${today.formatGerman()}",
            tone = StatusTone.ERROR,
            onClick = onClick,
        )
        dueSoonInvoices.isNotEmpty() -> HomeOverviewItem(
            title = "${dueSoonInvoices.size} ${if (dueSoonInvoices.size == 1) "Zahlung" else "Zahlungen"} bis ${horizon.formatGerman()} fällig",
            context = "Nächste Zahlungsfrist: ${nextSoon?.formatGerman() ?: horizon.formatGerman()}",
            tone = StatusTone.WARNING,
            onClick = onClick,
        )
        missingDueDateCount > 0 -> HomeOverviewItem(
            title = "$missingDueDateCount ${if (missingDueDateCount == 1) "Zahlungsfrist fehlt" else "Zahlungsfristen fehlen"}",
            context = "Fälligkeitsdatum ergänzen · Stand ${today.formatGerman()}",
            tone = StatusTone.WARNING,
            onClick = onClick,
        )
        nextDueDate != null -> HomeOverviewItem(
            title = "Bis ${horizon.formatGerman()} nichts fällig",
            context = "Nächste Zahlungsfrist: ${nextDueDate.formatGerman()}",
            tone = StatusTone.GOOD,
            onClick = onClick,
        )
        else -> HomeOverviewItem(
            title = "Keine offene Zahlungsfrist",
            context = "Stand ${today.formatGerman()}",
            tone = StatusTone.GOOD,
            onClick = onClick,
        )
    }
}

@Composable
private fun DailyCloseSummaryCard(
    close: DailyClose?,
    today: LocalDate,
    reminderTime: LocalTime,
    reminderEnabled: Boolean,
    reminderIsDue: Boolean,
    onOpen: (String?) -> Unit,
    onHistory: () -> Unit,
) {
    val isOpen = close == null
    val useLightPrimary = isOpen && MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = when {
        !isOpen -> MaterialTheme.colorScheme.secondaryContainer
        useLightPrimary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        !isOpen -> MaterialTheme.colorScheme.onSecondaryContainer
        useLightPrimary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val dayLabel = today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString() }
    val reminderLabel = "%02d:%02d Uhr".format(reminderTime.hour, reminderTime.minute)

    AppHeroCard(
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp).testTag("home_daily_missing_icon"),
                    )
                    Text(
                        "Tagesabschluss fehlt",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_nav_check),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp).testTag("home_daily_done_icon"),
                    )
                    Text(
                        "Tagesabschluss erledigt",
                        modifier = Modifier.weight(1f).testTag("home_daily_done_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    centsToGerman(close.zAmountCents),
                    modifier = Modifier.testTag("home_daily_amount"),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Heute · $dayLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.84f),
            )
            Text(
                if (isOpen) "Tagesumsatz laut Z-Abschlag erfassen." else "Tagesumsatz laut Z-Abschlag gespeichert.",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.84f),
            )
            if (isOpen && reminderEnabled) {
                Text(
                    if (reminderIsDue) "Seit $reminderLabel offen" else "Erinnerung heute um $reminderLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints {
                val compact = maxWidth / LocalDensity.current.fontScale < 300.dp
                val primaryAction: @Composable (Modifier) -> Unit = { modifier ->
                    AppPrimaryButton(
                        text = if (isOpen) "Tagesumsatz erfassen" else "Ansehen",
                        modifier = modifier,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (useLightPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (useLightPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { onOpen(close?.id) }
                }
                val historyAction: @Composable () -> Unit = {
                    TextButton(
                        onClick = onHistory,
                        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                    ) {
                        Icon(painterResource(R.drawable.ic_history), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Verlauf", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (compact) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        primaryAction(Modifier.fillMaxWidth())
                        historyAction()
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        primaryAction(Modifier.weight(1f))
                        historyAction()
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppSurfaceCard(
        modifier = modifier.heightIn(min = 108.dp),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    title,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
        }
    }
}

@Composable
private fun HomeOverviewCard(items: List<HomeOverviewItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val statusColors = when (item.tone) {
                StatusTone.GOOD -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                StatusTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick)
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(statusColors.first, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.tone == StatusTone.GOOD || item.tone == StatusTone.NEUTRAL) {
                        Icon(
                            painter = painterResource(if (item.tone == StatusTone.GOOD) R.drawable.ic_nav_check else R.drawable.ic_document),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = statusColors.second,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = statusColors.second,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        item.context,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun CompactInvoiceCard(
    invoice: InvoiceCase,
    today: LocalDate,
    dueSoonDays: Int,
    onInvoice: (String) -> Unit,
) {
    val signals = invoice.signals(today, dueSoonDays)
    AppSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onInvoice(invoice.id) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(invoice.supplier, style = MaterialTheme.typography.titleMedium)
                    Text(centsToGerman(invoice.amountCents), style = MaterialTheme.typography.bodyLarge)
                }
                StatusPill(
                    signals.label,
                    when {
                        signals.isOverdue -> StatusTone.ERROR
                        signals.isDone -> StatusTone.GOOD
                        else -> StatusTone.WARNING
                    },
                )
            }
            signals.messages.take(2).forEach { message ->
                Text("• $message", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

