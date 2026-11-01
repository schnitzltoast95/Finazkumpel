package de.quartalsmappe.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import de.quartalsmappe.app.R
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.AppPalette
import de.quartalsmappe.app.model.AppThemeMode
import de.quartalsmappe.app.model.AppearanceSettings
import de.quartalsmappe.app.model.CompanionAnimal
import de.quartalsmappe.app.ui.theme.appColorScheme
import de.quartalsmappe.app.ui.theme.LocalAppDarkTheme

internal fun companionDrawable(animal: CompanionAnimal, busy: Boolean = false): Int = when (animal) {
    CompanionAnimal.OWL -> if (busy) R.drawable.mascot_home_busy else R.drawable.mascot_home_calm
    CompanionAnimal.BEE -> R.drawable.mascot_bee
    CompanionAnimal.PORCUPINE -> R.drawable.mascot_porcupine
    CompanionAnimal.SHEEP -> R.drawable.mascot_sheep
    CompanionAnimal.SPHYNX -> R.drawable.mascot_sphynx
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsSection(store: AppStore) {
    var chooser by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val settings = store.appearanceSettings
    fun save(next: AppearanceSettings) {
        runCatching { store.saveAppearanceSettings(next) }
            .onSuccess { error = null }
            .onFailure { error = "Die Auswahl konnte nicht gespeichert werden. Bitte versuche es erneut." }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Dein Finanzkumpel", "Deine Auswahl wird sofort auf diesem Gerät gespeichert.")
        AppSurfaceCard(
            modifier = Modifier.fillMaxWidth().testTag("choose_theme_mode"),
            onClick = { error = null; chooser = "theme_mode" },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val foreground = MaterialTheme.colorScheme.onSurface
                val background = MaterialTheme.colorScheme.surfaceContainerHigh
                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(24.dp)) {
                        drawCircle(foreground)
                        drawArc(background, startAngle = -90f, sweepAngle = 180f, useCenter = true)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Darstellung", style = MaterialTheme.typography.titleSmall)
                    Text(settings.themeMode.label, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(20.dp))
            }
        }
        AppSurfaceCard(
            modifier = Modifier.fillMaxWidth().testTag("choose_palette"),
            onClick = { error = null; chooser = "palette" },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaletteSwatches(settings.palette)
                Column(Modifier.weight(1f)) {
                    Text("Farbschema", style = MaterialTheme.typography.titleSmall)
                    Text(settings.palette.label, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(20.dp))
            }
        }
        AppSurfaceCard(
            modifier = Modifier.fillMaxWidth().testTag("choose_companion"),
            onClick = { error = null; chooser = "companion" },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(companionDrawable(settings.companion)), null,
                    Modifier.size(64.dp), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Text("Begleiter", style = MaterialTheme.typography.titleSmall)
                    Text(settings.companion.label, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(20.dp))
            }
        }
    }

    if (chooser != null) {
        ModalBottomSheet(
            onDismissRequest = { chooser = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AppearanceSheetSystemBars()
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8f).dp)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(when (chooser) {
                    "theme_mode" -> "Darstellung wählen"
                    "palette" -> "Farbschema wählen"
                    else -> "Begleiter wählen"
                },
                    style = MaterialTheme.typography.headlineSmall)
                Text("Antippen genügt – deine Auswahl wird sofort gespeichert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                        .selectableGroup().testTag("appearance_choices"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (chooser == "theme_mode") {
                        items(AppThemeMode.entries, key = { it.stableId }) { mode ->
                            val selected = settings.themeMode == mode
                            Row(
                                Modifier.fillMaxWidth().clip(AppPillShape)
                                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .selectable(selected, role = Role.RadioButton,
                                        onClick = { save(settings.copy(themeMode = mode)) })
                                    .testTag("theme_mode_${mode.name}")
                                    .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    Text(mode.label, style = MaterialTheme.typography.bodyLarge, color = contentColor)
                                    if (mode == AppThemeMode.SYSTEM) {
                                        Text("Wie auf deinem Handy eingestellt", style = MaterialTheme.typography.bodySmall,
                                            color = contentColor)
                                    }
                                }
                                RadioButton(selected, onClick = null)
                            }
                        }
                    } else if (chooser == "palette") {
                        items(AppPalette.entries, key = { it.stableId }) { palette ->
                            val selected = settings.palette == palette
                            Row(
                                Modifier.fillMaxWidth().clip(AppPillShape)
                                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .selectable(selected, role = Role.RadioButton,
                                        onClick = { save(settings.copy(palette = palette)) })
                                    .testTag("palette_${palette.name}")
                                    .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PaletteSwatches(palette)
                                Text(palette.label, Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurface)
                                RadioButton(selected, onClick = null)
                            }
                        }
                    } else {
                        items(CompanionAnimal.entries.chunked(2)) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { animal ->
                                    val selected = settings.companion == animal
                                    Column(
                                        Modifier.weight(1f).clip(AppPillShape)
                                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerLow)
                                            .selectable(selected, role = Role.RadioButton,
                                                onClick = { save(settings.copy(companion = animal)) })
                                            .testTag("companion_${animal.name}").padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Image(painterResource(companionDrawable(animal)), null,
                                            Modifier.size(96.dp), contentScale = ContentScale.Fit)
                                        Text(animal.label, textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                                else MaterialTheme.colorScheme.onSurface)
                                        RadioButton(selected, onClick = null, modifier = Modifier.size(24.dp))
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                AppPrimaryButton("Fertig", Modifier.fillMaxWidth().testTag("appearance_done")) { chooser = null }
            }
        }
    }
}

@Composable
private fun AppearanceSheetSystemBars() {
    val view = LocalView.current
    val darkTheme = LocalAppDarkTheme.current
    SideEffect {
        // Material 3 1.3 creates a separate dialog Window with system-derived bar appearance.
        // Follow the app's explicit mode here too; never alter the underlying Activity Window.
        val window = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}

@Composable
private fun PaletteSwatches(palette: AppPalette) {
    val colors = appColorScheme(palette, LocalAppDarkTheme.current)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(colors.primary, colors.secondary, colors.tertiary).forEach { color ->
            Box(Modifier.size(16.dp).clip(CircleShape).background(color))
        }
    }
}
