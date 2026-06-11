package com.popemkt.watchcal

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * L1 structural gates — the fence map in specs/02-code-unit-cohesion.md.
 * A failure here blocks merge; these are the hard boundaries.
 */
class ArchitectureFencesTest {

    private val domain = Layer("domain", "com.popemkt.watchcal.domain..")
    private val calendar = Layer("calendar", "com.popemkt.watchcal.calendar..")
    private val reminders = Layer("reminders", "com.popemkt.watchcal.reminders..")
    private val ui = Layer("ui", "com.popemkt.watchcal.ui..")

    @Test
    fun `layer fences hold - domain is a leaf and dependencies point one way`() {
        Konsist.scopeFromProduction().assertArchitecture {
            domain.dependsOnNothing()
            calendar.dependsOn(domain)
            reminders.dependsOn(domain, calendar)
            ui.dependsOn(domain, calendar, reminders)
        }
    }

    @Test
    fun `domain stays pure kotlin - no android imports`() {
        Konsist.scopeFromPackage("com.popemkt.watchcal.domain..")
            .files
            .assertTrue { file ->
                file.imports.none { it.name.startsWith("android") }
            }
    }

    @Test
    fun `consumers depend on the CalendarSource interface, not the mirror implementation`() {
        Konsist.scopeFromProduction()
            .files
            .filter { !it.packagee?.name.orEmpty().endsWith("calendar") && it.name != "App" }
            .assertTrue { file ->
                file.imports.none { it.name.endsWith("WearCalendarSource") }
            }
    }
}
