package de.quartalsmappe.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.DailyClose
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.time.AppClock
import de.quartalsmappe.app.ui.EntriesScreen
import de.quartalsmappe.app.ui.theme.QuartalsmappeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** These component tests use their own preferences and cache directory, never the user's store. */
@RunWith(AndroidJUnit4::class)
class EntriesOverviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 14)
    private val clock = AppClock { today }
    private lateinit var testContext: EntriesTestContext
    private lateinit var store: AppStore

    @Before
    fun createIsolatedStore() {
        testContext = EntriesTestContext(InstrumentationRegistry.getInstrumentation().targetContext)
        store = AppStore(testContext)
    }

    @After
    fun clearOnlyIsolatedStore() {
        testContext.clearTestFiles()
    }

    @Test
    fun recordedDailyClose_appearsOnceAndOpensExistingEntry() {
        val close = DailyClose(id = "today-income", businessDate = today, zAmountCents = 74_200)
        store.saveDailyClose(close)
        var openedId: String? = null
        composeRule.setContent {
            QuartalsmappeTheme {
                EntriesScreen(
                    store = store,
                    clock = clock,
                    onOpenDaily = { openedId = it },
                    onOpenExport = {},
                    onOpenInvoice = {},
                    onEditDocument = {},
                )
            }
        }

        composeRule.onNodeWithTag("entries_list").performScrollToNode(hasTestTag("entry_daily_${close.id}"))
        composeRule.onAllNodesWithText("742,00 €").assertCountEquals(1)
        composeRule.onNodeWithTag("entry_daily_${close.id}").performClick()
        composeRule.runOnIdle {
            assertEquals(close.id, openedId)
            assertEquals(1, store.dailyCloses.size)
            assertEquals(74_200L, store.dailyCloses.single().zAmountCents)
        }
    }

    @Test
    fun unifiedEntriesShowOrdersAndDailyClosesWithAnExplicitTypeFilter() {
        store.saveInvoice(
            InvoiceCase(id = "existing-order", supplier = "Testlieferant", orderDate = today)
        )
        store.saveDailyClose(DailyClose(id = "existing-close", businessDate = today, zAmountCents = 1200))
        var openedInvoice: String? = null
        var exportRequests = 0
        composeRule.setContent {
            QuartalsmappeTheme {
                EntriesScreen(
                    store = store,
                    clock = clock,
                    onOpenDaily = {},
                    onOpenExport = { exportRequests++ },
                    onOpenInvoice = { openedInvoice = it },
                    onEditDocument = {},
                )
            }
        }

        composeRule.onNodeWithTag("entries_list").performScrollToNode(hasTestTag("entry_daily_existing-close"))
        composeRule.onNodeWithText("12,00 €").assertIsDisplayed()
        composeRule.onNodeWithTag("entries_list").performScrollToNode(hasTestTag("entry_invoice_existing-order"))
        composeRule.onNodeWithText("Testlieferant").assertIsDisplayed()
        composeRule.onNodeWithTag("entries_list").performScrollToNode(hasTestTag("entries_type_filter"))
        composeRule.onNodeWithTag("entries_type_filter").performClick()
        composeRule.onNodeWithText("Bestellung").performClick()
        composeRule.onNodeWithTag("entry_daily_existing-close").assertDoesNotExist()
        composeRule.onNodeWithTag("entries_list").performScrollToNode(hasTestTag("entry_invoice_existing-order"))
        composeRule.onNodeWithText("Testlieferant").assertIsDisplayed()
        composeRule.onNodeWithTag("entry_invoice_existing-order").performClick()
        composeRule.onNodeWithTag("entries_list").performScrollToNode(hasTestTag("entries_export"))
        composeRule.onNodeWithTag("entries_export").performClick()
        composeRule.runOnIdle {
            assertEquals("existing-order", openedInvoice)
            assertEquals(1, exportRequests)
            assertEquals(1, store.invoices.size)
            assertEquals(1, store.dailyCloses.size)
        }
    }
}

private class EntriesTestContext(base: Context) : ContextWrapper(base) {
    private val prefix = "entries_ui_test_${UUID.randomUUID()}_"
    private val preferenceNames = mutableSetOf<String>()
    private val testFiles = File(base.cacheDir, prefix).apply { mkdirs() }

    override fun getFilesDir(): File = testFiles

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val testName = prefix + name
        preferenceNames += testName
        return super.getSharedPreferences(testName, mode)
    }

    fun clearTestFiles() {
        preferenceNames.forEach { super.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        check(testFiles.deleteRecursively()) { "Could not remove isolated entries test files." }
    }
}
