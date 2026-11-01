package de.quartalsmappe.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsTest {
    @Test fun defaultsPreserveTheExistingLook() {
        assertEquals(AppPalette.FOREST, AppearanceSettings().palette)
        assertEquals(CompanionAnimal.OWL, AppearanceSettings().companion)
        assertEquals(AppThemeMode.SYSTEM, AppearanceSettings().themeMode)
    }

    @Test fun everyStableIdRoundTrips() {
        AppPalette.entries.forEach { assertEquals(it, AppPalette.fromStableId(it.stableId)) }
        CompanionAnimal.entries.forEach { assertEquals(it, CompanionAnimal.fromStableId(it.stableId)) }
        AppThemeMode.entries.forEach { assertEquals(it, AppThemeMode.fromStableId(it.stableId)) }
        assertEquals(10, AppPalette.entries.map { it.stableId }.distinct().size)
        assertEquals(5, CompanionAnimal.entries.map { it.stableId }.distinct().size)
        assertEquals(3, AppThemeMode.entries.map { it.stableId }.distinct().size)
    }

    @Test fun missingOrFutureIdsFallBackIndependently() {
        assertEquals(AppearanceSettings(), AppearanceSettings.fromStableIds(null, "future-animal"))
        assertEquals(AppearanceSettings(companion = CompanionAnimal.SHEEP),
            AppearanceSettings.fromStableIds("future-palette", CompanionAnimal.SHEEP.stableId))
        assertEquals(AppearanceSettings(palette = AppPalette.OCEAN),
            AppearanceSettings.fromStableIds(AppPalette.OCEAN.stableId, "unknown"))
        assertEquals(AppearanceSettings(AppPalette.OCEAN, CompanionAnimal.SHEEP),
            AppearanceSettings.fromStableIds("ocean", "sheep", "future-mode"))
        assertEquals(AppearanceSettings(themeMode = AppThemeMode.DARK),
            AppearanceSettings.fromStableIds(null, null, "dark"))
    }

    @Test fun explicitModesOverrideTheDeviceWhileSystemModeFollowsIt() {
        listOf(false, true).forEach { systemDark ->
            assertEquals(systemDark, AppThemeMode.SYSTEM.isDarkTheme(systemDark))
            assertEquals(false, AppThemeMode.LIGHT.isDarkTheme(systemDark))
            assertEquals(true, AppThemeMode.DARK.isDarkTheme(systemDark))
        }
    }

    @Test fun oldAppearancePreferencesKeepTheirPaletteAndCompanionWithSystemMode() {
        assertEquals(AppearanceSettings(AppPalette.ROSE, CompanionAnimal.SPHYNX, AppThemeMode.SYSTEM),
            AppearanceSettings.fromStableIds("rose", "sphynx"))
    }
}
