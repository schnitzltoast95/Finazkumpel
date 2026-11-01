package de.quartalsmappe.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.AppPalette
import de.quartalsmappe.app.model.AppThemeMode
import de.quartalsmappe.app.model.AppearanceSettings
import de.quartalsmappe.app.model.CompanionAnimal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("quartalsmappe_appearance", Context.MODE_PRIVATE)

    @Before @After fun clearOnlyAppearance() {
        check(context.packageName.endsWith(".uitest")) { "Appearance tests require the isolated test app." }
        check(preferences.edit().clear().commit())
    }

    @Test fun appearanceSurvivesReloadWithoutTouchingBusinessOrBackupPreferences() {
        val business = context.getSharedPreferences("quartalsmappe_data", Context.MODE_PRIVATE)
        val backup = context.getSharedPreferences("quartalsmappe_backup", Context.MODE_PRIVATE)
        val originalBusiness = business.all.toMap()
        val originalBackup = backup.all.toMap()
        val store = AppStore(context)
        val reminder = store.reminderSettings
        AppThemeMode.entries.forEach { mode ->
            val desired = AppearanceSettings(AppPalette.ROSE, CompanionAnimal.PORCUPINE, mode)
            store.saveAppearanceSettings(desired)

            assertEquals(desired, store.appearanceSettings)
            val reloaded = AppStore(context)
            assertEquals(desired, reloaded.appearanceSettings)
            assertEquals(reminder, reloaded.reminderSettings)
            assertEquals(store.dailyCloses, reloaded.dailyCloses)
            assertEquals(store.invoices, reloaded.invoices)
            assertEquals(store.documents, reloaded.documents)
            assertEquals(store.customDocumentTypes, reloaded.customDocumentTypes)
            assertEquals(originalBusiness, business.all)
            assertEquals(originalBackup, backup.all)
        }
    }

    @Test fun invalidSavedValuesUseSafeDefaults() {
        check(preferences.edit().putString("palette", "future").putString("companion", "future")
            .putString("theme_mode", "future").commit())
        assertEquals(AppearanceSettings(), AppStore(context).appearanceSettings)
    }

    @Test fun oldAppearancePreferencesLoadSystemModeWithoutChangingOtherChoices() {
        check(preferences.edit().putString("palette", "ocean").putString("companion", "bee").commit())
        assertEquals(AppearanceSettings(AppPalette.OCEAN, CompanionAnimal.BEE, AppThemeMode.SYSTEM),
            AppStore(context).appearanceSettings)
    }
}
