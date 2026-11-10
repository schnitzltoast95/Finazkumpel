package de.quartalsmappe.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.AppPalette
import de.quartalsmappe.app.model.CompanionAnimal
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Before
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.time.LocalDate

/**
 * Small end-to-end smoke suite for the most important workflows.
 *
 * Every test launches the real [MainActivity] with an empty local store. The suite deliberately
 * avoids the Android document picker so it can run unattended on an emulator or test device.
 */
@RunWith(AndroidJUnit4::class)
class QuartalsmappeSmokeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(ClearQuartalsmappeStateRule())
        .around(composeRule)

    @Before
    fun awaitInitialHomeContent() {
        // Lazy content can register after the Activity is idle; await the real initial action.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Tagesumsatz erfassen")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).size == 1
        }
        composeRule.onNodeWithText("Tagesumsatz erfassen").assertIsDisplayed()
    }

    @Test
    fun emptyStartScreen_showsInitialTasks() {
        composeRule.onNodeWithText("Hallo, Svetlana!").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesabschluss fehlt").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesumsatz erfassen").assertIsDisplayed()
        composeRule.scrollTo("Keine offene Zahlungsfrist")
        composeRule.onNodeWithText("Keine offene Zahlungsfrist").assertIsDisplayed()
        composeRule.scrollTo("Kassenexport fehlt")
        composeRule.onNodeWithText("Kassenexport fehlt").assertIsDisplayed()
    }

    @Test
    fun homeWelcome_changesOnNewVisitAndForeground_butSurvivesRecreation() {
        fun welcome(): String = composeRule.onNodeWithTag("home_welcome")
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text

        val first = welcome()
        composeRule.onNodeWithTag("nav_HOME").performClick()
        assertEquals(first, welcome())

        composeRule.onNodeWithTag("nav_SETTINGS").performClick()
        composeRule.onNodeWithTag("nav_HOME").performClick()
        val nextVisit = welcome()
        assertNotEquals(first, nextVisit)

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Hallo, Svetlana!").assertIsDisplayed()
        assertEquals(nextVisit, welcome())

        composeRule.onNodeWithTag("nav_SETTINGS").performClick()
        composeRule.onNodeWithTag("nav_HOME").performClick()
        val afterRecreation = welcome()
        assertNotEquals(nextVisit, afterRecreation)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.onNodeWithTag("home_welcome").assertIsDisplayed()
        assertNotEquals(afterRecreation, welcome())
    }

    @Test
    fun appearanceChoices_areSelectablePersistedAndDoNotDiscardReminderDraft() {
        composeRule.onNodeWithTag("nav_SETTINGS").performClick()
        composeRule.onNodeWithTag("settings_content").performScrollToNode(hasText("Mo"))
        composeRule.onNodeWithText("Mo").performClick()
        composeRule.onNodeWithText("Mo").assertIsSelected()
        composeRule.onNodeWithTag("settings_content").performScrollToNode(hasTestTag("choose_palette"))
        composeRule.onNodeWithTag("choose_palette").performClick()
        AppPalette.entries.forEach { palette ->
            composeRule.onNodeWithTag("appearance_choices")
                .performScrollToNode(hasTestTag("palette_${palette.name}"))
            composeRule.onNodeWithTag("palette_${palette.name}").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("appearance_choices")
                .performScrollToNode(hasTestTag("palette_${palette.name}"))
            composeRule.onNodeWithTag("palette_${palette.name}").assertIsSelected()
            composeRule.runOnIdle {
                assertEquals(palette, AppStore(InstrumentationRegistry.getInstrumentation().targetContext).appearanceSettings.palette)
            }
        }
        composeRule.onNodeWithTag("appearance_done").performClick()
        composeRule.onNodeWithTag("choose_companion").performClick()
        CompanionAnimal.entries.forEach { animal ->
            composeRule.onNodeWithTag("appearance_choices")
                .performScrollToNode(hasTestTag("companion_${animal.name}"))
            composeRule.onNodeWithTag("companion_${animal.name}").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("appearance_choices")
                .performScrollToNode(hasTestTag("companion_${animal.name}"))
            composeRule.onNodeWithTag("companion_${animal.name}").assertIsSelected()
            composeRule.runOnIdle {
                assertEquals(animal, AppStore(InstrumentationRegistry.getInstrumentation().targetContext).appearanceSettings.companion)
            }
        }
        composeRule.onNodeWithTag("appearance_done").performClick()
        composeRule.onNodeWithTag("settings_content").performScrollToNode(hasText("Mo"))
        composeRule.onNodeWithText("Mo").assertIsSelected()
        composeRule.onNodeWithTag("nav_HOME").performClick()
        composeRule.onNodeWithText("Eingaben verwerfen?").assertIsDisplayed()
        composeRule.onNodeWithText("Verwerfen").performClick()
        composeRule.onNodeWithContentDescription("Dein Begleiter: Nacktkatze").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithContentDescription("Dein Begleiter: Nacktkatze").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_SETTINGS").performClick()
        composeRule.onNodeWithTag("choose_palette").performClick()
        composeRule.onNodeWithTag("appearance_choices")
            .performScrollToNode(hasTestTag("palette_${AppPalette.entries.last().name}"))
        composeRule.onNodeWithTag("palette_${AppPalette.entries.last().name}").assertIsSelected()
    }

    @Test
    fun dailyClose_withoutDocument_isSavedAndShownInHistory() {
        composeRule.onNodeWithText("Tagesumsatz erfassen").performClick()

        composeRule.onNodeWithText("Tagesabschluss").assertIsDisplayed()
        composeRule.onNodeWithText("Umsatz laut Z-Abschlag")
            .performTextReplacement("742,00")
        composeRule.closeKeyboardAndWait()
        composeRule.scrollTo("Tagesabschluss speichern")
        composeRule.onNodeWithText("Tagesabschluss speichern").performClick()

        composeRule.onNodeWithText("Tagesabschluss erledigt").assertIsDisplayed()
        composeRule.onNodeWithText("742,00 €").assertIsDisplayed()

        composeRule.onNodeWithText("Verlauf").performClick()
        composeRule.onNodeWithText("Tagesabschlüsse").assertIsDisplayed()
        composeRule.onNodeWithText("Z-Beleg ergänzen").assertIsDisplayed()
        composeRule.onNodeWithText("742,00 €").assertIsDisplayed()

        composeRule.onNodeWithText("742,00 €").performClick()
        composeRule.onNodeWithText("Umsatz laut Z-Abschlag").assertIsDisplayed()
        composeRule.onNodeWithTag("main_navigation").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithText("Z-Beleg ergänzen").assertIsDisplayed()
        composeRule.onNodeWithText("742,00 €").assertIsDisplayed()
    }

    @Test
    fun dailyClose_withNegativeAmount_showsValidationError() {
        composeRule.onNodeWithText("Tagesumsatz erfassen").performClick()
        composeRule.onNodeWithText("Umsatz laut Z-Abschlag")
            .performTextReplacement("-10,00")
        composeRule.closeKeyboardAndWait()
        composeRule.scrollTo("Tagesabschluss speichern")
        composeRule.onNodeWithText("Tagesabschluss speichern").performClick()

        composeRule.onNodeWithText("Bitte einen gültigen Betrag eingeben")
            .assertIsDisplayed()
    }

    @Test
    fun homeDailyClose_opensAndUpdatesTheExistingDailyClose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText("Tagesumsatz erfassen").performClick()
        composeRule.onNodeWithText("Umsatz laut Z-Abschlag").performTextReplacement("321,00")
        composeRule.closeKeyboardAndWait()
        composeRule.scrollTo("Tagesabschluss speichern")
        composeRule.onNodeWithText("Tagesabschluss speichern").performClick()
        composeRule.onNodeWithText("321,00 €").assertIsDisplayed()

        var savedId = ""
        composeRule.runOnIdle { savedId = AppStore(context).dailyCloses.single().id }
        composeRule.scrollTo("Ansehen")
        composeRule.onNodeWithText("Ansehen").performClick()
        composeRule.onNodeWithText("321,00").assertIsDisplayed()
        composeRule.onNodeWithText("Umsatz laut Z-Abschlag").performTextReplacement("322,00")
        composeRule.closeKeyboardAndWait()
        composeRule.scrollTo("Änderungen speichern")
        composeRule.onNodeWithText("Änderungen speichern").performClick()
        composeRule.onNodeWithText("Bestehenden Eintrag ändern?").assertIsDisplayed()
        composeRule.onNodeWithText("Änderung speichern").performClick()

        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()
        composeRule.scrollTo("Tagesabschluss erledigt")
        composeRule.onNodeWithText("Tagesabschluss erledigt").assertIsDisplayed()
        composeRule.onNodeWithText("322,00 €").assertIsDisplayed()
        composeRule.runOnIdle {
            val closes = AppStore(context).dailyCloses
            assertEquals(1, closes.size)
            assertEquals(savedId, closes.single().id)
            assertEquals(32_200L, closes.single().zAmountCents)
        }
    }

    @Test
    fun incompleteOrder_isCreatedWithOrderedStatus() {
        composeRule.scrollTo("Bestellung")
        composeRule.onNodeWithText("Bestellung").performClick()

        composeRule.onNodeWithText("Bestellung notieren").assertIsDisplayed()
        composeRule.onNodeWithText("Lieferant")
            .performTextReplacement("WaxPro Testlieferant")
        composeRule.closeKeyboardAndWait()
        composeRule.scrollTo("Vorgang speichern")
        composeRule.onNodeWithText("Vorgang speichern").performClick()

        composeRule.onNodeWithText("WaxPro Testlieferant").assertIsDisplayed()
        composeRule.onNodeWithText("Betrag offen").assertIsDisplayed()
        composeRule.onNodeWithText("Bestellt").assertIsDisplayed()
        composeRule.onNodeWithText("Rechnung fehlt", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Lieferung fehlt", substring = true).assertIsDisplayed()
    }

    @Test
    fun documentPeriod_changesOnlyAfterExplicitConfirmation() {
        val current = Quarter.current(LocalDate.now())
        val otherQuarter = if (current.number == 1) 2 else 1

        composeRule.onNodeWithTag("nav_CAPTURE").performClick()
        composeRule.onNodeWithTag("capture_list").performScrollToNode(hasTestTag("capture_BANK_STATEMENT"))
        composeRule.onNodeWithTag("capture_BANK_STATEMENT").performClick()
        composeRule.onNodeWithText(current.label).assertIsDisplayed()

        composeRule.onNodeWithTag("document_period_selector").performClick()
        composeRule.onNodeWithText("Anderen Zeitraum wählen").assertIsDisplayed()
        composeRule.onNodeWithText("$otherQuarter. Quartal", substring = true).performClick()
        composeRule.onNodeWithText("Abbrechen").performClick()
        composeRule.onNodeWithText(current.label).assertIsDisplayed()

        composeRule.onNodeWithTag("document_period_selector").performClick()
        composeRule.onNodeWithText("$otherQuarter. Quartal", substring = true).performClick()
        composeRule.onNodeWithText("Zeitraum übernehmen").performClick()

        composeRule.onNodeWithText(Quarter(current.year, otherQuarter).label).assertIsDisplayed()
        composeRule.onNodeWithText("Anderer Zeitraum", substring = true).assertIsDisplayed()
    }

    @Test
    fun exportNavigation_preservesQuarterAndSelectionAndReturnsToEntries() {
        val current = Quarter.current(LocalDate.now())
        val selectedQuarter = current.previous()
        composeRule.onNodeWithTag("nav_ENTRIES").performClick()
        composeRule.onNodeWithTag("entries_export").performClick()

        composeRule.onNodeWithTag("main_navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_ENTRIES").assertIsSelected()
        composeRule.onNodeWithText("Quartalspaket").assertIsDisplayed()
        composeRule.onNodeWithText(current.label).assertIsDisplayed()
        composeRule.onNodeWithText("Vorheriges Quartal").performClick()
        composeRule.onNodeWithText(selectedQuarter.label).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("export_include_invoices"))
        composeRule.onNodeWithTag("export_include_invoices").performClick().assertIsOn()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Quartalspaket").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_ENTRIES").assertIsSelected()
        composeRule.scrollTo(selectedQuarter.label)
        composeRule.onNodeWithText(selectedQuarter.label).assertIsDisplayed()
        composeRule.scrollTo("Tagesabschlüsse")
        composeRule.onNodeWithText("Tagesabschlüsse").assertIsDisplayed()
        composeRule.onNodeWithText("0 Tagesabschlüsse erfasst").assertIsDisplayed()
        composeRule.scrollTo("Übersicht (PDF)")
        composeRule.onNodeWithText("Übersicht (PDF)").assertIsDisplayed()
        composeRule.scrollTo("Tagesumsätze (CSV)")
        composeRule.onNodeWithText("Tagesumsätze (CSV)").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("export_include_invoices"))
        composeRule.onNodeWithTag("export_include_invoices").assertIsOn()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("export_add_cash"))
        composeRule.onNodeWithTag("export_add_cash").assertIsDisplayed()
        composeRule.onNodeWithText("Kassenexport hinzufügen").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("export_add_bank"))
        composeRule.onNodeWithTag("export_add_bank").assertIsDisplayed()
        composeRule.scrollTo("Quartalspaket erstellen")
        composeRule.onNodeWithText("Quartalspaket erstellen").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithTag("nav_ENTRIES").assertIsSelected()
        composeRule.onNodeWithText("Quartalspaket vorbereiten").assertIsDisplayed()
        composeRule.onNodeWithTag("entries_search").assertIsDisplayed()
        composeRule.onNodeWithText(selectedQuarter.label).assertDoesNotExist()
        Espresso.pressBack()
        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()
        composeRule.onNodeWithText("Hallo, Svetlana!").assertIsDisplayed()
    }

    @Test
    fun rootNavigation_remainsVisibleAndSelectsTheWholeDestination() {
        val destinations = listOf("HOME", "CAPTURE", "ENTRIES", "SETTINGS")
        composeRule.onNodeWithTag("main_navigation").onChild().onChildren().assertCountEquals(4)
        composeRule.onNodeWithTag("nav_QUARTER").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_INVOICES").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_FINANCES").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_DOCUMENTS").assertDoesNotExist()
        destinations.forEach { destination ->
            composeRule.onNodeWithTag("nav_$destination").performClick()
            composeRule.onNodeWithTag("main_navigation").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_$destination").assertIsSelected()
            destinations.filterNot { it == destination }.forEach { other ->
                composeRule.onNodeWithTag("nav_$other").assertIsNotSelected()
            }
            composeRule.onNodeWithContentDescription("Zurück").assertDoesNotExist()
            if (destination != "HOME") {
                Espresso.pressBack()
                composeRule.onNodeWithTag("nav_HOME").assertIsSelected()
                composeRule.onNodeWithText("Hallo, Svetlana!").assertIsDisplayed()
            }
        }

        composeRule.onNodeWithTag("nav_HOME").performClick()
        composeRule.onNodeWithText("Hallo, Svetlana!").assertIsDisplayed()
    }

    @Test
    fun allKindsShortcutAndCaptureTabUseTheSameEntryPoint() {
        composeRule.scrollTo("Alle Arten")
        composeRule.onNodeWithText("Alle Arten").performClick()
        composeRule.onNodeWithTag("nav_CAPTURE").assertIsSelected()
        composeRule.onNodeWithTag("capture_daily").assertIsDisplayed()
        composeRule.onNodeWithTag("capture_invoice").assertIsDisplayed()
        composeRule.onNodeWithTag("capture_order").assertIsDisplayed()
        composeRule.onNodeWithTag("capture_attach").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zurück").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_HOME").performClick()
        composeRule.onNodeWithTag("nav_CAPTURE").performClick()
        composeRule.onNodeWithTag("capture_daily").assertIsDisplayed()
        composeRule.onNodeWithTag("capture_invoice").assertIsDisplayed()
        composeRule.onNodeWithTag("capture_order").assertIsDisplayed()
        composeRule.onNodeWithTag("capture_attach").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_HOME").performClick()
        composeRule.scrollTo("Rechnung")
        composeRule.onNodeWithText("Rechnung").performClick()
        composeRule.onNodeWithTag("nav_CAPTURE").assertIsSelected()
        composeRule.onNodeWithText("Rechnung eintragen").assertIsDisplayed()
        composeRule.onNodeWithText("Lieferant").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()
    }

    @Test
    fun entryForms_keepNavigationAndBackReturnsToThePreviousScreen() {
        composeRule.scrollTo("Bestellung")
        composeRule.onNodeWithText("Bestellung").performClick()
        composeRule.onNodeWithTag("main_navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_CAPTURE").assertIsSelected()
        composeRule.onNodeWithText("Bestellung notieren").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()

        composeRule.scrollTo("Alle Arten")
        composeRule.onNodeWithText("Alle Arten").performClick()
        composeRule.onNodeWithTag("nav_CAPTURE").assertIsSelected()
        composeRule.onNodeWithTag("capture_list").performScrollToNode(hasTestTag("capture_BANK_STATEMENT"))
        composeRule.onNodeWithTag("capture_BANK_STATEMENT").performClick()
        composeRule.onNodeWithTag("main_navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_CAPTURE").assertIsSelected()
        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithTag("capture_list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_CAPTURE").assertIsSelected()
        Espresso.pressBack()
        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()

        composeRule.scrollTo("Tagesumsatz erfassen")
        composeRule.onNodeWithText("Tagesumsatz erfassen").performClick()
        composeRule.onNodeWithTag("main_navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()
    }

    @Test
    fun dirtyOrder_navigationCanBeCancelledOrExplicitlyDiscarded() {
        composeRule.scrollTo("Bestellung")
        composeRule.onNodeWithText("Bestellung").performClick()
        composeRule.onNodeWithText("Lieferant").performTextReplacement("Ungespeicherter Lieferant")
        composeRule.closeKeyboardAndWait()

        composeRule.onNodeWithTag("nav_ENTRIES").performClick()
        composeRule.onNodeWithText("Eingaben verwerfen?").assertIsDisplayed()
        composeRule.onNodeWithText("Weiter bearbeiten").performClick()
        composeRule.onNodeWithText("Ungespeicherter Lieferant").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_CAPTURE").assertIsSelected()

        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithText("Eingaben verwerfen?").assertIsDisplayed()
        composeRule.onNodeWithText("Weiter bearbeiten").performClick()
        composeRule.onNodeWithText("Ungespeicherter Lieferant").assertIsDisplayed()
        composeRule.closeKeyboardAndWait()

        composeRule.onNodeWithTag("nav_ENTRIES").performClick()
        composeRule.onNodeWithText("Verwerfen").performClick()
        composeRule.onNodeWithTag("nav_ENTRIES").assertIsSelected()
        composeRule.onNodeWithText("Quartalspaket vorbereiten").assertIsDisplayed()
        composeRule.onNodeWithText("0 Einträge").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_CAPTURE").performClick()
        composeRule.onNodeWithTag("capture_order").performClick()
        composeRule.onNodeWithText("Bestellung notieren").assertIsDisplayed()
        composeRule.onNodeWithText("Lieferant").assertIsDisplayed()
        composeRule.onNodeWithText("Ungespeicherter Lieferant").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, AppStore(InstrumentationRegistry.getInstrumentation().targetContext).invoices.size) }
    }

    @Test
    fun discardedInvoiceDraftDoesNotReappearAfterReturningFromExistingEntryPicker() {
        composeRule.scrollTo("Rechnung")
        composeRule.onNodeWithText("Rechnung").performClick()
        composeRule.scrollTo("Lieferant")
        composeRule.onNodeWithText("Lieferant").performTextReplacement("Verworfener Rechnungsentwurf")
        composeRule.closeKeyboardAndWait()
        composeRule.scrollTo("Zu vorhandener Bestellung hinzufügen")
        composeRule.onNodeWithText("Zu vorhandener Bestellung hinzufügen").performClick()
        composeRule.onNodeWithText("Eingaben verwerfen?").assertIsDisplayed()
        composeRule.onNodeWithText("Verwerfen").performClick()
        composeRule.onNodeWithTag("invoice_selection").assertIsDisplayed()
        composeRule.onNodeWithText("Noch keine Bestellung oder Rechnung erfasst.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithText("Rechnung eintragen").assertIsDisplayed()
        composeRule.scrollTo("Lieferant")
        assertEquals("", composeRule.onNodeWithText("Lieferant").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text)
        composeRule.onNodeWithText("Verworfener Rechnungsentwurf").assertDoesNotExist()
        composeRule.runOnIdle {
            val store = AppStore(InstrumentationRegistry.getInstrumentation().targetContext)
            assertEquals(0, store.invoices.size)
            assertEquals(0, store.documents.size)
        }
        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithText("Eingaben verwerfen?").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()
    }

    @Test
    fun dailyCloseDraft_survivesRecreationAndStillRequiresDiscardConfirmation() {
        composeRule.onNodeWithText("Tagesumsatz erfassen").performClick()
        composeRule.onNodeWithText("Umsatz laut Z-Abschlag").performTextReplacement("157,35")
        composeRule.closeKeyboardAndWait()
        composeRule.scrollTo("Notiz (optional)")
        composeRule.onNodeWithText("Notiz (optional)").performTextReplacement("Umsatzentwurf nach Neustart")
        composeRule.closeKeyboardAndWait()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Tagesabschluss").assertIsDisplayed()
        composeRule.scrollTo("Umsatz laut Z-Abschlag")
        composeRule.onNodeWithText("157,35").assertIsDisplayed()
        composeRule.scrollTo("Notiz (optional)")
        composeRule.onNodeWithText("Umsatzentwurf nach Neustart").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_ENTRIES").performClick()
        composeRule.onNodeWithText("Eingaben verwerfen?").assertIsDisplayed()
        composeRule.onNodeWithText("Weiter bearbeiten").performClick()
        composeRule.onNodeWithText("Umsatzentwurf nach Neustart").assertIsDisplayed()
        composeRule.scrollTo("Tagesabschluss speichern")
        composeRule.onNodeWithText("Tagesabschluss speichern").performClick()
        composeRule.runOnIdle {
            val saved = AppStore(InstrumentationRegistry.getInstrumentation().targetContext).dailyCloses.single()
            assertEquals(15_735L, saved.zAmountCents)
            assertEquals("Umsatzentwurf nach Neustart", saved.note)
        }
    }

    @Test
    fun orderDraft_survivesRecreationWithoutBecomingACleanDraft() {
        composeRule.scrollTo("Bestellung")
        composeRule.onNodeWithText("Bestellung").performClick()
        composeRule.onNodeWithText("Lieferant").performTextReplacement("Bestellentwurf nach Neustart")
        composeRule.onNodeWithText("Betrag (optional)").performTextReplacement("93,20")
        composeRule.closeKeyboardAndWait()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Bestellung notieren").assertIsDisplayed()
        composeRule.onNodeWithText("Bestellentwurf nach Neustart").assertIsDisplayed()
        composeRule.onNodeWithText("93,20").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_CAPTURE").assertIsSelected()
        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithText("Eingaben verwerfen?").assertIsDisplayed()
        composeRule.onNodeWithText("Weiter bearbeiten").performClick()
        composeRule.onNodeWithText("Bestellentwurf nach Neustart").assertIsDisplayed()

        composeRule.scrollTo("Vorgang speichern")
        composeRule.onNodeWithText("Vorgang speichern").performClick()
        composeRule.runOnIdle {
            val saved = AppStore(InstrumentationRegistry.getInstrumentation().targetContext).invoices.single()
            assertEquals("Bestellentwurf nach Neustart", saved.supplier)
            assertEquals(9_320L, requireNotNull(saved.amountCents))
            assertEquals(LocalDate.now(), saved.orderDate)
            assertNull(saved.invoiceDate)
        }
    }

    @Test
    fun reminderDraft_survivesRecreationAndCanBeExplicitlyDiscarded() {
        composeRule.onNodeWithTag("nav_SETTINGS").performClick()
        composeRule.onNodeWithTag("settings_content").performScrollToNode(hasText("Mo"))
        composeRule.onNodeWithText("Mo").performClick().assertIsSelected()
        composeRule.onNodeWithTag("settings_content").performScrollToNode(hasText("3 Tage vorher"))
        composeRule.onNodeWithText("3 Tage vorher").performClick().assertIsSelected()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("nav_SETTINGS").assertIsSelected()
        composeRule.onNodeWithTag("settings_content").performScrollToNode(hasText("Mo"))
        composeRule.onNodeWithText("Mo").assertIsSelected()
        composeRule.onNodeWithTag("settings_content").performScrollToNode(hasText("3 Tage vorher"))
        composeRule.onNodeWithText("3 Tage vorher").assertIsSelected()
        composeRule.onNodeWithTag("nav_HOME").performClick()
        composeRule.onNodeWithText("Eingaben verwerfen?").assertIsDisplayed()
        composeRule.onNodeWithText("Weiter bearbeiten").performClick()
        composeRule.onNodeWithText("3 Tage vorher").assertIsSelected()
        composeRule.onNodeWithTag("nav_HOME").performClick()
        composeRule.onNodeWithText("Verwerfen").performClick()
        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()
        composeRule.runOnIdle {
            val saved = AppStore(InstrumentationRegistry.getInstrumentation().targetContext).reminderSettings
            assertEquals(emptySet<java.time.DayOfWeek>(), saved.enabledDays)
            assertEquals(5, saved.dueReminderDays)
        }
    }

    @Test
    fun existingOrder_unchangedEditDoesNotCreateAnInvoiceDate() {
        composeRule.scrollTo("Bestellung")
        composeRule.onNodeWithText("Bestellung").performClick()
        composeRule.onNodeWithText("Lieferant").performTextReplacement("Bestellung ohne Rechnung")
        composeRule.closeKeyboardAndWait()
        composeRule.scrollTo("Vorgang speichern")
        composeRule.onNodeWithText("Vorgang speichern").performClick()

        composeRule.scrollTo("Alle Angaben bearbeiten")
        composeRule.onNodeWithText("Alle Angaben bearbeiten").performClick()
        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.scrollTo("Bestellung ohne Rechnung")
        composeRule.onNodeWithText("Bestellung ohne Rechnung").assertIsDisplayed()
        composeRule.onNodeWithText("Rechnung fehlt", substring = true).assertIsDisplayed()

        composeRule.scrollTo("Alle Angaben bearbeiten")
        composeRule.onNodeWithText("Alle Angaben bearbeiten").performClick()
        composeRule.scrollTo("Änderungen speichern")
        composeRule.onNodeWithText("Änderungen speichern").performClick()
        composeRule.onNodeWithText("Bestehenden Eintrag ändern?").assertIsDisplayed()
        composeRule.onNodeWithText("Änderung speichern").performClick()

        composeRule.scrollTo("Bestellung ohne Rechnung")
        composeRule.onNodeWithText("Rechnung fehlt", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Bestellt").assertIsDisplayed()
    }
}

private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.scrollTo(
    text: String,
) {
    onNode(hasScrollAction()).performScrollToNode(hasText(text))
}

private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.closeKeyboardAndWait() {
    Espresso.closeSoftKeyboard()
    waitForIdle()
}

/** Clears only the files and preferences owned by the app's local store. */
private class ClearQuartalsmappeStateRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                clearState()
                try {
                    base.evaluate()
                } finally {
                    clearState()
                }
            }
        }

    private fun clearState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest")) { "Smoke tests require the isolated test app." }
        check(context.getSharedPreferences("quartalsmappe_appearance", Context.MODE_PRIVATE)
            .edit().clear().commit()) { "Could not clear appearance settings." }
        check(
            context.getSharedPreferences(DATA_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        ) { "Could not clear Quartalsmappe test data." }
        check(
            context.getSharedPreferences(BACKUP_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        ) { "Could not clear Quartalsmappe backup settings." }

        deleteIfPresent(File(context.filesDir, "documents"))
        deleteIfPresent(File(context.filesDir, "revisions"))
    }

    private fun deleteIfPresent(directory: File) {
        check(!directory.exists() || directory.deleteRecursively()) {
            "Could not clear ${directory.absolutePath}."
        }
    }

    private companion object {
        const val DATA_PREFERENCES = "quartalsmappe_data"
        const val BACKUP_PREFERENCES = "quartalsmappe_backup"
    }
}
