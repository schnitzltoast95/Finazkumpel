package de.quartalsmappe.app.time

import java.time.LocalDate

fun interface AppClock {
    fun today(): LocalDate
}

object SystemAppClock : AppClock {
    override fun today(): LocalDate = LocalDate.now()
}
