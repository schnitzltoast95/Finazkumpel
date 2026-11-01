package de.quartalsmappe.app.ui

import kotlin.random.Random

internal class HomeGreetingRotation(private val random: Random = Random.Default) {
    private var last: String? = null

    fun next(previous: String? = last): String {
        val candidates = messages.filterNot { it == previous }
        return candidates.random(random).also { last = it }
    }

    fun markShown(message: String) {
        last = message
    }

    companion object {
        val messages: List<String> = listOf(
            "Schön, dass du da bist!",
            "Schön, dich wiederzusehen!",
            "Ich bin für dich da. Legen wir los!",
            "Zusammen behalten wir den Überblick.",
            "Ein kleiner Überblick für deinen Tag.",
        )
    }
}
