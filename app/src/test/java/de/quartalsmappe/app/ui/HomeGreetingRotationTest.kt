package de.quartalsmappe.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HomeGreetingRotationTest {
    private val allowedMessages = setOf(
        "Schön, dass du da bist!",
        "Schön, dich wiederzusehen!",
        "Ich bin für dich da. Legen wir los!",
        "Zusammen behalten wir den Überblick.",
        "Ein kleiner Überblick für deinen Tag.",
    )

    @Test
    fun `rotation only returns approved greetings`() {
        val rotation = HomeGreetingRotation(Random(17))

        repeat(500) {
            assertTrue(rotation.next() in allowedMessages)
        }
    }

    @Test
    fun `consecutive greetings never repeat`() {
        val rotation = HomeGreetingRotation(Random(42))
        var previous = rotation.next()

        repeat(1_000) {
            val greeting = rotation.next()
            assertNotEquals(previous, greeting)
            previous = greeting
        }
    }

    @Test
    fun `restored previous greeting is excluded and new result is remembered`() {
        val rotation = HomeGreetingRotation(Random(91))

        allowedMessages.forEach { restoredGreeting ->
            repeat(100) {
                val greeting = rotation.next(previous = restoredGreeting)
                assertNotEquals(restoredGreeting, greeting)
                assertNotEquals(greeting, rotation.next())
            }
        }
    }

    @Test
    fun `marking restored greeting excludes it from the next visit`() {
        repeat(100) { seed ->
            allowedMessages.forEach { restoredGreeting ->
                val rotation = HomeGreetingRotation(Random(seed))
                rotation.markShown(restoredGreeting)

                assertNotEquals(restoredGreeting, rotation.next())
            }
        }
    }

    @Test
    fun `all greeting variants are reachable`() {
        val rotation = HomeGreetingRotation(Random(2026))
        val seen = List(500) { rotation.next() }.toSet()

        assertEquals(allowedMessages, seen)
    }
}
