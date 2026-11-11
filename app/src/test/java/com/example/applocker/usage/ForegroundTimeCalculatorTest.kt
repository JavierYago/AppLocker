package com.example.applocker.usage

import com.example.applocker.usage.ForegroundTimeCalculator.Companion.ACTIVITY_PAUSED
import com.example.applocker.usage.ForegroundTimeCalculator.Companion.ACTIVITY_RESUMED
import com.example.applocker.usage.ForegroundTimeCalculator.Companion.ACTIVITY_STOPPED
import com.example.applocker.usage.ForegroundTimeCalculator.Companion.DEVICE_STARTUP
import com.example.applocker.usage.ForegroundTimeCalculator.Companion.SCREEN_NON_INTERACTIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundTimeCalculatorTest {

    private val dayStart = 1_000_000L
    private val insta = "com.instagram.android"
    private val chrome = "com.android.chrome"

    private fun calculator(vararg events: UsageEvent) =
        ForegroundTimeCalculator(dayStart).apply { events.forEach(::process) }

    private fun event(pkg: String, type: Int, atSeconds: Long, cls: String = "Main") =
        UsageEvent(pkg, cls, type, dayStart + atSeconds * 1_000)

    private fun ForegroundTimeCalculator.seconds(pkg: String, nowSeconds: Long) =
        (usageByPackage(dayStart + nowSeconds * 1_000)[pkg] ?: 0L) / 1_000

    @Test
    fun `closed session counts from resume to pause`() {
        val calc = calculator(
            event(insta, ACTIVITY_RESUMED, 10),
            event(insta, ACTIVITY_PAUSED, 70),
        )
        assertEquals(60, calc.seconds(insta, nowSeconds = 500))
        assertNull(calc.foregroundPackage())
    }

    @Test
    fun `open session keeps counting until now`() {
        val calc = calculator(event(insta, ACTIVITY_RESUMED, 10))
        assertEquals(90, calc.seconds(insta, nowSeconds = 100))
        assertEquals(insta, calc.foregroundPackage())
    }

    @Test
    fun `sessions of the same app are added up`() {
        val calc = calculator(
            event(insta, ACTIVITY_RESUMED, 0),
            event(insta, ACTIVITY_PAUSED, 30),
            event(chrome, ACTIVITY_RESUMED, 30),
            event(chrome, ACTIVITY_PAUSED, 50),
            event(insta, ACTIVITY_RESUMED, 50),
        )
        assertEquals(40, calc.seconds(insta, nowSeconds = 60))
        assertEquals(20, calc.seconds(chrome, nowSeconds = 60))
        assertEquals(insta, calc.foregroundPackage())
    }

    @Test
    fun `navigating between activities of one app does not lose time`() {
        // El sistema puede reanudar la pantalla B antes de pausar la A.
        val calc = calculator(
            event(insta, ACTIVITY_RESUMED, 0, cls = "Feed"),
            event(insta, ACTIVITY_RESUMED, 20, cls = "Profile"),
            event(insta, ACTIVITY_PAUSED, 21, cls = "Feed"),
            event(insta, ACTIVITY_STOPPED, 22, cls = "Feed"),
        )
        assertEquals(60, calc.seconds(insta, nowSeconds = 60))
        assertEquals(insta, calc.foregroundPackage())
    }

    @Test
    fun `app opened before midnight only counts since day start`() {
        val calc = calculator(
            event(insta, ACTIVITY_RESUMED, atSeconds = -600),
            event(insta, ACTIVITY_PAUSED, atSeconds = 120),
        )
        assertEquals(120, calc.seconds(insta, nowSeconds = 500))
    }

    @Test
    fun `pause without a known resume counts since day start`() {
        val calc = calculator(event(insta, ACTIVITY_PAUSED, 45))
        assertEquals(45, calc.seconds(insta, nowSeconds = 500))
    }

    @Test
    fun `usage entirely before day start is ignored`() {
        val calc = calculator(
            event(insta, ACTIVITY_RESUMED, atSeconds = -600),
            event(insta, ACTIVITY_PAUSED, atSeconds = -300),
        )
        assertEquals(0, calc.seconds(insta, nowSeconds = 500))
    }

    @Test
    fun `unclean shutdown closes the session at the last known event`() {
        val calc = calculator(
            event(insta, ACTIVITY_RESUMED, 0),
            event(chrome, ACTIVITY_STOPPED, 40), // último evento antes de apagarse de golpe
            event("android", DEVICE_STARTUP, 3_000),
        )
        assertEquals(40, calc.seconds(insta, nowSeconds = 3_600))
        assertNull(calc.foregroundPackage())
    }

    @Test
    fun `screen off closes the open session`() {
        val calc = calculator(
            event(insta, ACTIVITY_RESUMED, 0),
            event("android", SCREEN_NON_INTERACTIVE, 30),
        )
        assertEquals(30, calc.seconds(insta, nowSeconds = 3_600))
        assertNull(calc.foregroundPackage())
    }
}
