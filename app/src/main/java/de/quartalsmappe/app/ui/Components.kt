package de.quartalsmappe.app.ui

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.quartalsmappe.app.R
import de.quartalsmappe.app.model.formatGerman
import de.quartalsmappe.app.time.AppClock
import de.quartalsmappe.app.util.createCameraUri
import de.quartalsmappe.app.util.displayName
import java.time.LocalDate

val AppCardShape = RoundedCornerShape(18.dp)
val AppHeroShape = RoundedCornerShape(24.dp)
val AppPillShape = RoundedCornerShape(14.dp)

@Composable
fun AppSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = borderColor?.let { BorderStroke(1.dp, it) },
        shape = AppCardShape,
        content = content,
    )
}

@Composable
fun AppHeroCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = AppHeroShape,
        content = content,
    )
}

@Composable
fun AppPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier.heightIn(min = 56.dp),
        onClick = onClick,
        enabled = enabled,
        shape = AppPillShape,
        colors = colors,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier.heightIn(min = 52.dp),
        onClick = onClick,
        enabled = enabled,
        shape = AppPillShape,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppPageHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = if (onBack == null) 16.dp else 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Zurück")
            }
        }
        Text(
            title,
            modifier = Modifier.weight(1f).testTag("page_title"),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    clock: AppClock,
    modifier: Modifier = Modifier,
    allowEmpty: Boolean = true,
) {
    val context = LocalContext.current
    val base = value ?: clock.today()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSecondaryButton(
                text = value?.formatGerman() ?: "Datum auswählen",
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "$label: ${value?.formatGerman() ?: "Datum auswählen"}"
                },
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> onValueChange(LocalDate.of(year, month + 1, day)) },
                        base.year,
                        base.monthValue - 1,
                        base.dayOfMonth,
                    ).show()
                },
            )
            if (allowEmpty && value != null) {
                TextButton(onClick = { onValueChange(null) }) { Text("Leeren") }
            }
        }
    }
}

@Composable
fun AttachmentPicker(
    label: String,
    selectedUri: Uri?,
    onSelected: (Uri) -> Unit,
    existingCount: Int = 0,
    allowCamera: Boolean = true,
) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onSelected)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let(onSelected)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (existingCount > 0) {
            Text(
                "$existingCount ${if (existingCount == 1) "Dokument" else "Dokumente"} bereits hinterlegt",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (selectedUri != null) {
            Text(
                "Ausgewählt: ${context.displayName(selectedUri) ?: "Dokument"}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowCamera) {
                AppSecondaryButton(
                    text = "Foto aufnehmen",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        cameraUri = createCameraUri(context)
                        cameraLauncher.launch(cameraUri!!)
                    },
                )
            }
            AppSecondaryButton(
                text = "Datei auswählen",
                modifier = Modifier.weight(1f),
                onClick = { fileLauncher.launch(arrayOf("application/pdf", "image/*")) },
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    tone: StatusTone = StatusTone.NEUTRAL,
    compact: Boolean = false,
) {
    val colors = when (tone) {
        StatusTone.GOOD -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .background(colors.first, RoundedCornerShape(100.dp))
            .padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone == StatusTone.ERROR) {
            Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(16.dp), tint = colors.second)
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.second)
    }
}

enum class StatusTone { GOOD, WARNING, ERROR, NEUTRAL }

@Composable
fun PrimarySaveButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    AppPrimaryButton(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun MissingHint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f),
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ConfirmEditDialog(
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bestehenden Eintrag ändern?") },
        text = {
            Text(
                "Du änderst einen bereits gespeicherten $itemName. " +
                    "Die bisherige Version bleibt zur Sicherheit in der Änderungshistorie erhalten."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Änderung speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

