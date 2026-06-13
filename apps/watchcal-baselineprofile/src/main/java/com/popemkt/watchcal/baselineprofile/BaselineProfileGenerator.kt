package com.popemkt.watchcal.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The journey ART pre-compiles from: cold start into the agenda, then a few
 * scrolls. Run via `generateBaselineProfile` (specs/01-architecture § Entrypoints).
 * The scroll is best-effort — safe whether the agenda is long, short, or the
 * permission screen is showing on a fresh profile run.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()

        device.waitForIdle()
        repeat(3) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                10,
            )
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.popemkt.watchcal"
    }
}
