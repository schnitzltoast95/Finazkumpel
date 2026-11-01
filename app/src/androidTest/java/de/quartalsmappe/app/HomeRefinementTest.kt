package de.quartalsmappe.app

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import kotlin.math.abs

/** Regression coverage for the 0.24 Home layout and its real activity navigation. */
@RunWith(AndroidJUnit4::class)
class HomeRefinementTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(HomeRefinementStateRule())
        .around(compose)

    @Test
    fun allKindsShortcutIsInTheHeadingRowAboveTheQuickCaptureTiles() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_all_types"))
        compose.onNodeWithTag("home_quick_heading").assertIsDisplayed()
        compose.onNodeWithTag("home_all_types").assertIsDisplayed().assertHasClickAction()

        val heading = compose.onNodeWithTag("home_quick_heading").fetchSemanticsNode().boundsInRoot
        val shortcut = compose.onNodeWithTag("home_all_types").fetchSemanticsNode().boundsInRoot
        val invoice = compose.onNodeWithText("Rechnung").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("home_list").fetchSemanticsNode().boundsInRoot
        assertTrue("The heading and shortcut must share a vertical band.",
            heading.center.y >= shortcut.top && heading.center.y <= shortcut.bottom)
        assertTrue("Alle Arten belongs to the right of the heading, without overlap.",
            shortcut.left >= heading.right)
        assertTrue("The shortcut belongs on the trailing half of the content.",
            shortcut.center.x > list.center.x)
        assertTrue("The shortcut must precede the quick-capture tiles, not follow them.",
            shortcut.bottom <= invoice.top)

        val overview = compose.onNodeWithTag("home_overview_heading").fetchSemanticsNode().boundsInRoot
        assertTrue("Quick capture must come before the overview on Home.",
            invoice.bottom <= overview.top)

        compose.onNodeWithTag("home_all_types").performClick()
        compose.onNodeWithTag("nav_CAPTURE").assertIsSelected()
        compose.onNodeWithTag("capture_list").assertIsDisplayed()
    }

    @Test
    fun homeUsesTheSpaceAboveNavigationAndScrollsWithoutAContinuationBanner() {
        compose.onNodeWithTag("home_list").assertIsDisplayed()
        compose.onNodeWithTag("home_more_content").assertDoesNotExist()
        compose.onNodeWithText("Weiter unten").assertDoesNotExist()
        val list = compose.onNodeWithTag("home_list").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("main_navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Home must reclaim the banner's space up to the navigation bar.",
            abs(list.bottom - navigation.top) <= 2f)
        val initialScroll = homeScrollPosition()

        compose.onNodeWithTag("home_list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("A normal swipe must advance Home without an extra continuation control.",
            homeScrollPosition() > initialScroll)

        compose.onNodeWithTag("home_list")
            .performScrollToNode(hasText("Datensicherung einrichten"))
        compose.onNodeWithTag("home_list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("Datensicherung einrichten").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("home_more_content").assertDoesNotExist()
        compose.onNodeWithText("Weiter unten").assertDoesNotExist()
        compose.onNodeWithTag("nav_HOME").assertIsSelected()
    }

    @Test
    fun completedDailyCloseShowsACheckAndProminentAmountBelowItsStatus() {
        // Activity launch can expose the root before the initial LazyColumn items are ready.
        // Wait for the real target; do not scroll it into view or bypass the Home action.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Tagesumsatz erfassen")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).size == 1
        }
        compose.onNodeWithText("Tagesabschluss fehlt").assertIsDisplayed()
        compose.onNodeWithTag("home_daily_missing_icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("home_daily_done_icon", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Tagesumsatz erfassen").assertIsDisplayed().performClick()
        compose.onNodeWithText("Umsatz laut Z-Abschlag").performTextReplacement("742,00")
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tagesabschluss speichern"))
        compose.onNodeWithText("Tagesabschluss speichern").performClick()

        compose.onNodeWithTag("nav_HOME").assertIsSelected()
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_daily_amount"))
        compose.onNodeWithText("Tagesabschluss erledigt").assertIsDisplayed()
        compose.onNodeWithTag("home_daily_done_icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("home_daily_amount").assertIsDisplayed().assert(hasText("742,00 €"))
        compose.onNodeWithText("Tagesabschluss fehlt").assertDoesNotExist()
        compose.onNodeWithTag("home_daily_missing_icon", useUnmergedTree = true).assertDoesNotExist()

        val status = compose.onNodeWithTag("home_daily_done_title").fetchSemanticsNode().boundsInRoot
        val amount = compose.onNodeWithTag("home_daily_amount").fetchSemanticsNode().boundsInRoot
        assertTrue("The saved amount must have its own line below the completed status.",
            amount.top >= status.bottom)
        compose.runOnIdle {
            val saved = AppStore(InstrumentationRegistry.getInstrumentation().targetContext).dailyCloses.single()
            assertEquals(74_200L, saved.zAmountCents)
        }
    }

    @Test
    fun everyDestinationRetainsAnAccessibleNameRoleAndExclusiveSelection() {
        val destinations = linkedMapOf(
            "HOME" to "Start",
            "CAPTURE" to "Erfassen",
            "ENTRIES" to "Einträge",
            "SETTINGS" to "Optionen",
        )
        destinations.keys.forEach { selected ->
            compose.onNodeWithTag("nav_$selected").performClick()
            compose.onNodeWithTag("main_navigation").assertIsDisplayed()
            if (selected == "ENTRIES") {
                compose.onNodeWithTag("page_title").assert(hasText("Einträge"))
            }
            destinations.forEach { (destination, label) ->
                val node = compose.onNodeWithTag("nav_$destination")
                node.assertIsDisplayed().assertHasClickAction()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                    .assert(hasText(label) or hasContentDescription(label))
                if (destination == selected) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }
    }

    private fun homeScrollPosition(): Float = compose.onNodeWithTag("home_list")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
}

/** Mirrors the smoke-suite isolation contract; never runs cleanup against the user's app. */
private class HomeRefinementStateRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
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
        check(context.packageName.endsWith(".uitest")) { "Home tests require the isolated test app." }
        listOf("quartalsmappe_appearance", "quartalsmappe_data", "quartalsmappe_backup").forEach { name ->
            check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) {
                "Could not clear isolated Home test preferences: $name."
            }
        }
        listOf("documents", "revisions").forEach { name ->
            val directory = File(context.filesDir, name)
            check(!directory.exists() || directory.deleteRecursively()) {
                "Could not clear isolated Home test files: ${directory.absolutePath}."
            }
        }
    }
}
