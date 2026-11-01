package de.quartalsmappe.app

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.AppThemeMode
import de.quartalsmappe.app.ui.SettingsScreen
import de.quartalsmappe.app.ui.theme.LocalAppDarkTheme
import de.quartalsmappe.app.ui.theme.QuartalsmappeTheme
import de.quartalsmappe.app.ui.theme.appColorScheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class AppearanceModeUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appearancePreferences get() = context.getSharedPreferences("quartalsmappe_appearance", Context.MODE_PRIVATE)

    @Before @After fun clearOnlyAppearance() {
        check(context.packageName.endsWith(".uitest")) { "Appearance tests require the isolated test app." }
        check(appearancePreferences.edit().clear().commit())
    }

    @Test fun chooserOverridesDeviceColorsImmediatelyAndPreservesTheReminderDraft() {
        val store = AppStore(context)
        val savedReminder = store.reminderSettings
        val business = context.getSharedPreferences("quartalsmappe_data", Context.MODE_PRIVATE)
        val backup = context.getSharedPreferences("quartalsmappe_backup", Context.MODE_PRIVATE)
        val originalBusiness = business.all.toMap()
        val originalBackup = backup.all.toMap()
        var systemDark by mutableStateOf(true)
        var effectiveDark = false
        var renderedBackground = Color.Unspecified
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                val appearance = store.appearanceSettings
                QuartalsmappeTheme(appearance.palette, appearance.themeMode.isDarkTheme(isSystemInDarkTheme())) {
                    val dark = LocalAppDarkTheme.current
                    val background = MaterialTheme.colorScheme.background
                    SideEffect { effectiveDark = dark; renderedBackground = background }
                    SettingsScreen(store, onDone = {})
                }
            }
        }
        compose.onNodeWithTag("choose_theme_mode").assertIsDisplayed()
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Mo"))
        compose.onNodeWithText("Mo").performClick()
        compose.onNodeWithTag("settings_content").performScrollToNode(hasTestTag("choose_theme_mode"))
        compose.onNodeWithTag("choose_theme_mode").performClick()
        compose.onNodeWithText("Darstellung wählen").assertIsDisplayed()

        fun assertResolved(mode: AppThemeMode, expectedDark: Boolean) {
            compose.onNodeWithTag("theme_mode_${mode.name}").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("theme_mode_${mode.name}").assertIsSelected()
            compose.runOnIdle {
                assertEquals(mode, AppStore(context).appearanceSettings.themeMode)
                assertEquals(expectedDark, effectiveDark)
                assertEquals(appColorScheme(store.appearanceSettings.palette, expectedDark).background, renderedBackground)
            }
            assertSheetSystemBars(expectedDark)
        }

        assertResolved(AppThemeMode.LIGHT, false) // Phone dark, app explicitly light.
        compose.runOnIdle { systemDark = false }
        assertResolved(AppThemeMode.DARK, true) // Phone light, app explicitly dark.
        assertResolved(AppThemeMode.SYSTEM, false)
        compose.runOnIdle { systemDark = true }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(true, effectiveDark)
            assertEquals(appColorScheme(store.appearanceSettings.palette, true).background, renderedBackground)
        }
        assertSheetSystemBars(expectedDark = true)
        compose.onNodeWithTag("appearance_done").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("settings_content").performScrollToNode(hasText("Mo"))
        if (DayOfWeek.MONDAY in savedReminder.enabledDays) {
            compose.onNodeWithText("Mo").assertIsNotSelected()
        } else {
            compose.onNodeWithText("Mo").assertIsSelected()
        }
        compose.runOnIdle {
            assertEquals(savedReminder, store.reminderSettings)
            assertEquals(savedReminder, AppStore(context).reminderSettings)
            assertEquals(originalBusiness, business.all)
            assertEquals(originalBackup, backup.all)
        }
    }

    private fun assertSheetSystemBars(expectedDark: Boolean) {
        onView(isRoot()).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            val window = checkNotNull(findDialogWindow(view)) { "The appearance sheet must own a native dialog Window." }
            val controller = WindowCompat.getInsetsController(window, view)
            assertEquals(!expectedDark, controller.isAppearanceLightStatusBars)
            assertEquals(!expectedDark, controller.isAppearanceLightNavigationBars)
        }
    }

    private fun findDialogWindow(view: View): Window? {
        if (view is DialogWindowProvider) return view.window
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findDialogWindow(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
