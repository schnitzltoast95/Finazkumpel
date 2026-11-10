package de.quartalsmappe.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.ReminderSettings
import de.quartalsmappe.app.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

private val ReminderDaysSaver = listSaver<Set<DayOfWeek>, Int>(
    save = { days -> days.map { it.value } },
    restore = { values -> values.map { DayOfWeek.of(it) }.toSet() },
)

@Composable
fun SettingsScreen(
    store: AppStore,
    modifier: Modifier = Modifier,
    onDirtyChange: (Boolean) -> Unit = {},
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabledDays by rememberSaveable(stateSaver = ReminderDaysSaver) { mutableStateOf(store.reminderSettings.enabledDays) }
    var hour by rememberSaveable { mutableIntStateOf(store.reminderSettings.hour) }
    var minute by rememberSaveable { mutableIntStateOf(store.reminderSettings.minute) }
    var dueDays by rememberSaveable { mutableIntStateOf(store.reminderSettings.dueReminderDays) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var reminderError by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val savedReminderSettings = store.reminderSettings

    LaunchedEffect(Unit) { store.refreshBackupStatus() }
    val reportDirty by rememberUpdatedState(onDirtyChange)
    LaunchedEffect(enabledDays, hour, minute, dueDays, savedReminderSettings) {
        reportDirty(ReminderSettings(enabledDays, hour, minute, dueDays) != savedReminderSettings)
    }
    DisposableEffect(Unit) {
        onDispose { reportDirty(false) }
    }
    fun runBackup(action: () -> String) {
        if (backupBusy) return
        backupBusy = true
        backupMessage = "Bitte warten, die Dateien werden verarbeitet …"
        scope.launch {
            backupMessage = runCatching { withContext(Dispatchers.IO) { action() } }
                .getOrElse { it.message ?: "Die Sicherung konnte nicht verarbeitet werden." }
            backupBusy = false
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val backupFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runBackup { "Sicherung erstellt: ${store.setBackupFolder(it).fileName}" }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestore = uri
    }
    val dayLabels = mapOf(
        DayOfWeek.MONDAY to "Mo",
        DayOfWeek.TUESDAY to "Di",
        DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr",
        DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So",
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("settings_content"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        store.loadError?.let { message -> item { MissingHint(message) } }
        item {
            AppearanceSettingsSection(store)
        }
        item {
            SectionTitle(
                "Tagesabschluss-Erinnerung",
                "Wähle selbst, an welchen Tagen die App erinnern soll. Ohne Auswahl bleibt sie ausgeschaltet.",
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(DayOfWeek.entries) { day ->
                    FilterChip(
                        selected = day in enabledDays,
                        onClick = {
                            enabledDays = if (day in enabledDays) enabledDays - day else enabledDays + day
                        },
                        label = { Text(dayLabels.getValue(day)) },
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Uhrzeit", style = MaterialTheme.typography.labelLarge)
                AppSecondaryButton(
                    text = "%02d:%02d Uhr".format(hour, minute),
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, selectedHour, selectedMinute ->
                                hour = selectedHour
                                minute = selectedMinute
                            },
                            hour,
                            minute,
                            true,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            SectionTitle("Zahlungserinnerung", "Wann gilt eine Rechnung in der App als „bald fällig“?")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(3, 5, 7, 14)) { days ->
                    FilterChip(
                        selected = dueDays == days,
                        onClick = { dueDays = days },
                        label = { Text("$days Tage vorher") },
                    )
                }
            }
        }
        item {
            reminderError?.let { MissingHint(it) }
            PrimarySaveButton("Erinnerungen speichern", enabled = ReminderSettings(enabledDays, hour, minute, dueDays) != savedReminderSettings) {
                val settings = ReminderSettings(enabledDays, hour, minute, dueDays)
                runCatching {
                    store.saveReminderSettings(settings)
                    ReminderScheduler.scheduleAll(context, settings)
                }.onSuccess {
                    reminderError = null
                    if (enabledDays.isNotEmpty() && Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    reportDirty(false)
                    onDone()
                }.onFailure { reminderError = it.message ?: "Erinnerungen konnten nicht gespeichert werden." }
            }
        }
        item {
            SectionTitle(
                "Datensicherung",
                "Die App sichert Änderungen im gewählten Rhythmus in eine aktuelle ZIP-Datei. Separate Stände legst du nur bei Bedarf an.",
            )
        }
        item {
            AppSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusPill(
                        if (store.backupFolderUri == null) "Nicht eingerichtet"
                        else if (store.lastBackupError != null) "Fehler"
                        else if (store.hasPendingBackup) "Änderungen warten auf Sicherung"
                        else "Automatisch aktiv",
                        when {
                            store.backupFolderUri == null -> StatusTone.WARNING
                            store.lastBackupError != null -> StatusTone.ERROR
                            else -> StatusTone.GOOD
                        },
                    )
                    Text(
                        if (store.backupFolderUri == null) {
                            "Wähle einen Ordner auf dem Gerät oder in Google Drive. Danach legt die App die erste Sicherung sofort an."
                        } else {
                            "Änderungen werden im gewählten Rhythmus in „Quartalsmappe_Aktuell.zip“ gesichert. Ohne Änderungen entsteht keine neue Datei. Android kann den Zeitpunkt wegen Akku- und Hintergrundregeln verschieben."
                        }
                    )
                    if (store.backupFolderUri != null) {
                        Text("Automatischer Abstand", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(1, 3, 7)) { days ->
                                FilterChip(
                                    selected = store.backupIntervalDays == days,
                                    onClick = {
                                        runCatching { store.changeBackupIntervalDays(days) }
                                            .onFailure { backupMessage = it.message ?: "Der Abstand konnte nicht gespeichert werden." }
                                    },
                                    label = { Text(if (days == 1) "Täglich" else "Alle $days Tage") },
                                )
                            }
                        }
                    }
                    store.lastBackupAt?.let { date ->
                        Text(
                            "Letzte Sicherung: ${date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))} Uhr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    store.lastBackupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    AppPrimaryButton(
                        text = if (store.backupFolderUri == null) {
                            "Sicherungsordner auswählen"
                        } else {
                            "Sicherungsordner ändern"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !backupBusy,
                        onClick = { backupFolderLauncher.launch(null) },
                    )
                    if (store.backupFolderUri != null) {
                        AppSecondaryButton(
                            text = "Jetzt sichern",
                            onClick = { runBackup { "Sicherung aktualisiert: ${store.updateBackupNow().fileName}" } },
                            enabled = !backupBusy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppSecondaryButton(
                            text = "Separaten Sicherungsstand anlegen",
                            onClick = {
                                runBackup { "Separater Sicherungsstand erstellt: ${store.createBackupSnapshot().fileName}" }
                            },
                            enabled = !backupBusy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AppSecondaryButton(
                        text = "Sicherung wieder einlesen",
                        enabled = !backupBusy,
                        onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    backupMessage?.let { message ->
                        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Google Drive wird in der Ordnerauswahl angezeigt, wenn die Drive-App installiert und angemeldet ist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Text(
                "Datenschutz: Die Arbeitsdaten bleiben im privaten App-Speicher. Nur die ausdrücklich gewählten Sicherungsdateien werden zusätzlich in den ausgewählten Ordner geschrieben.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Sicherung einlesen?") },
            text = {
                Text(
                    "Vorhandene Einträge werden nicht überschrieben. Die App fügt nur noch nicht vorhandene Daten und Dokumente hinzu und meldet Konflikte."
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    runBackup {
                        val result = store.restoreBackup(uri)
                        "Wiederhergestellt: ${result.dailyClosesAdded} Tagesabschlüsse, " +
                                "${result.invoicesAdded} Vorgänge und ${result.documentsAdded} Dokumente. " +
                                "${result.originalFilesRestored} Originaldateien wiederhergestellt. " +
                                "${result.skippedConflicts} Konflikte wurden sicher übersprungen."
                    }
                }) { Text("Sicher einlesen") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingRestore = null }) { Text("Abbrechen") }
            },
        )
    }
}

