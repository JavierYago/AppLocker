package com.example.applocker.usage

import com.example.applocker.domain.model.AppLimit
import com.example.applocker.usage.LimitEvaluator.LIMIT_REACHED_MARKER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitEvaluatorTest {

    // 1 h 30 min al día, con avisos a 30 y 10 minutos restantes.
    private val limit = AppLimit("com.instagram.android", 90, listOf(30, 10))

    private fun evaluate(usedMinutes: Int, fired: Set<Int> = emptySet(), limit: AppLimit = this.limit) =
        LimitEvaluator.evaluate(limit, usedMinutes * 60_000L, fired)

    @Test
    fun `nothing happens far from any threshold`() {
        val result = evaluate(usedMinutes = 20)
        assertNull(result.alertToFire)
        assertFalse(result.limitReached)
        assertTrue(result.newlyFired.isEmpty())
        assertEquals(70 * 60_000L, result.remainingMs)
    }

    @Test
    fun `fires when remaining time reaches a threshold`() {
        val result = evaluate(usedMinutes = 60)
        assertEquals(30, result.alertToFire)
        assertEquals(listOf(30), result.newlyFired)
    }

    @Test
    fun `does not repeat an alert already fired today`() {
        val result = evaluate(usedMinutes = 65, fired = setOf(30))
        assertNull(result.alertToFire)
        assertTrue(result.newlyFired.isEmpty())
    }

    @Test
    fun `several thresholds crossed at once produce a single alert`() {
        val result = evaluate(usedMinutes = 85)
        assertEquals(10, result.alertToFire)
        assertEquals(setOf(30, 10), result.newlyFired.toSet())
    }

    @Test
    fun `reaching the limit notifies once and skips pending alerts`() {
        val first = evaluate(usedMinutes = 90)
        assertTrue(first.limitReached)
        assertTrue(first.notifyLimitReached)
        assertNull(first.alertToFire)
        assertEquals(0L, first.remainingMs)
        assertEquals(setOf(30, 10, LIMIT_REACHED_MARKER), first.newlyFired.toSet())

        val second = evaluate(usedMinutes = 95, fired = first.newlyFired.toSet())
        assertTrue(second.limitReached)
        assertFalse(second.notifyLimitReached)
        assertTrue(second.newlyFired.isEmpty())
    }

    @Test
    fun `thresholds not smaller than the limit are ignored`() {
        val result = evaluate(usedMinutes = 0, limit = AppLimit("pkg", 20, listOf(30, 20)))
        assertNull(result.alertToFire)
        assertTrue(result.newlyFired.isEmpty())
    }
}
