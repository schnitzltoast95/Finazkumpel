package de.quartalsmappe.app.ui.theme

import androidx.compose.ui.graphics.Color
import de.quartalsmappe.app.model.AppPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PaletteContrastTest {
    @Test
    fun `every palette has readable light and dark text roles`() {
        AppPalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = appColorScheme(palette, dark)
                val pairs = listOf(
                    "primary" to (scheme.onPrimary to scheme.primary),
                    "primary container" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                    "secondary" to (scheme.onSecondary to scheme.secondary),
                    "secondary container" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                    "tertiary" to (scheme.onTertiary to scheme.tertiary),
                    "tertiary container" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                    "background" to (scheme.onBackground to scheme.background),
                    "surface" to (scheme.onSurface to scheme.surface),
                    "surface variant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
                    "navigation" to (scheme.onSurfaceVariant to scheme.surfaceContainer),
                    "error" to (scheme.onError to scheme.error),
                    "error container" to (scheme.onErrorContainer to scheme.errorContainer),
                    "primary link" to (scheme.primary to scheme.background),
                    "card link" to (scheme.primary to scheme.surface),
                    "daily card supporting text" to (scheme.onPrimary.copy(alpha = 0.82f) to scheme.primary),
                    "completed card supporting text" to (scheme.onPrimaryContainer.copy(alpha = 0.82f) to scheme.primaryContainer),
                )
                pairs.forEach { (role, pair) ->
                    assertContrast("$palette dark=$dark $role", pair.first, pair.second, minimum = 4.5)
                }
                listOf(
                    scheme.surfaceContainerLow,
                    scheme.surfaceContainer,
                    scheme.surfaceContainerHigh,
                ).forEach { surface ->
                    assertContrast("$palette dark=$dark elevated text", scheme.onSurface, surface, minimum = 4.5)
                    assertContrast("$palette dark=$dark supporting text", scheme.onSurfaceVariant, surface, minimum = 4.5)
                }
            }
        }
    }

    @Test
    fun `control outlines and action icons remain identifiable in every palette`() {
        AppPalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = appColorScheme(palette, dark)
                listOf(scheme.background, scheme.surface, scheme.surfaceContainerHigh).forEach { surface ->
                    assertContrast("$palette dark=$dark control outline", scheme.outline, surface, minimum = 3.0)
                }
                assertContrast("$palette dark=$dark action icon", scheme.primary, scheme.primaryContainer, minimum = 3.0)
            }
        }
    }

    @Test
    fun `forest keeps the approved colors and each alternative is distinct`() {
        assertEquals(Color(0xFF2D4A2B), appColorScheme(AppPalette.FOREST, false).primary)
        assertEquals(Color(0xFFFAF9F6), appColorScheme(AppPalette.FOREST, false).background)
        assertEquals(Color(0xFFB5CFA5), appColorScheme(AppPalette.FOREST, true).primary)
        assertEquals(Color(0xFF161C17), appColorScheme(AppPalette.FOREST, true).background)
        listOf(false, true).forEach { dark ->
            assertEquals(AppPalette.entries.size, AppPalette.entries.map { appColorScheme(it, dark).primary }.toSet().size)
        }
    }

    private fun assertContrast(label: String, foreground: Color, background: Color, minimum: Double) {
        val alpha = foreground.alpha.toDouble()
        val channels = listOf(foreground.red, foreground.green, foreground.blue)
        val backdrop = listOf(background.red, background.green, background.blue)
        val visible = channels.mapIndexed { index, channel -> channel * alpha + backdrop[index] * (1.0 - alpha) }
        val foregroundLuminance = luminance(visible)
        val backgroundLuminance = luminance(backdrop.map { it.toDouble() })
        val ratio = (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
        assertTrue("$label contrast $ratio is below $minimum:1", ratio >= minimum)
    }

    private fun luminance(channels: List<Double>): Double {
        val linear = channels.map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
    }
}
