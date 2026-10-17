package com.aven.ai.ui.chat

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingSelectorTest {

    @After
    fun reset() {
        GreetingSelector.lastShown = null
    }

    private fun clockAt(hour: Int): Clock {
        val instant = Instant.parse("2026-01-05T%02d:00:00Z".format(hour))
        return Clock.fixed(instant, ZoneId.of("UTC"))
    }

    @Test
    fun `morning draws from morning, general or personality pools`() {
        val allowed = (GreetingSelector.MORNING + GreetingSelector.GENERAL + GreetingSelector.PERSONALITY)
            .map { GreetingSelector.render(it, "T") }.toSet()
        repeat(30) {
            val g = GreetingSelector.next("T", clockAt(8), Random(it.toLong()))
            assertTrue(g in allowed)
        }
    }

    @Test
    fun `afternoon draws from afternoon, general or personality pools`() {
        val allowed = (GreetingSelector.AFTERNOON + GreetingSelector.GENERAL + GreetingSelector.PERSONALITY)
            .map { GreetingSelector.render(it, "T") }.toSet()
        repeat(30) {
            val g = GreetingSelector.next("T", clockAt(14), Random(it.toLong()))
            assertTrue(g in allowed)
        }
    }

    @Test
    fun `night draws from night, general or personality pools`() {
        val allowed = (GreetingSelector.NIGHT + GreetingSelector.GENERAL + GreetingSelector.PERSONALITY)
            .map { GreetingSelector.render(it, "T") }.toSet()
        repeat(30) {
            GreetingSelector.lastShown = null
            val g = GreetingSelector.next("T", clockAt(23), Random(it.toLong()))
            assertTrue(g in allowed)
        }
        repeat(10) {
            GreetingSelector.lastShown = null
            val g = GreetingSelector.next("T", clockAt(2), Random(it.toLong()))
            assertTrue(g in allowed)
        }
    }

    @Test
    fun `selection varies across draws`() {
        val seen = (0 until 20).map {
            GreetingSelector.lastShown = null
            GreetingSelector.next("T", clockAt(9), Random(it.toLong()))
        }.toSet()
        assertTrue(seen.size > 1)
    }

    @Test
    fun `never repeats consecutively when alternatives exist`() {
        var previous: String? = null
        repeat(50) {
            val g = GreetingSelector.next("T", clockAt(9), Random.Default)
            if (previous != null) {
                assertNotEquals(previous, g)
            }
            previous = g
        }
    }

    @Test
    fun `single eligible greeting is returned as-is`() {
        assertEquals("Only", GreetingSelector.pickFrom(listOf("Only"), Random(0), "Only"))
    }

    @Test
    fun `all supplied strings preserved exactly`() {
        val all = GreetingSelector.NIGHT + GreetingSelector.MORNING +
            GreetingSelector.AFTERNOON + GreetingSelector.GENERAL +
            GreetingSelector.PERSONALITY
        assertEquals(28, all.size)
        assertTrue("Hello, night owl.." in all)
        assertTrue("Good morning, {name}." in all)
        assertTrue("Hey, {name}." in all)
        assertTrue("What are we cooking today ¿" in all)
        assertTrue(all.all { it.isNotBlank() })
    }

    @Test
    fun `name renders only in name templates`() {
        assertEquals("Good morning, T.", GreetingSelector.render("Good morning, {name}.", "T"))
        assertEquals("Still awake ¿", GreetingSelector.render("Still awake ¿", "T"))
        assertEquals("Good morning, User.", GreetingSelector.render("Good morning, {name}.", ""))
    }

    @Test
    fun `period boundaries follow standard convention`() {
        assertEquals(GreetingSelector.Period.MORNING, GreetingSelector.periodFor(5))
        assertEquals(GreetingSelector.Period.MORNING, GreetingSelector.periodFor(11))
        assertEquals(GreetingSelector.Period.AFTERNOON, GreetingSelector.periodFor(12))
        assertEquals(GreetingSelector.Period.AFTERNOON, GreetingSelector.periodFor(17))
        assertEquals(GreetingSelector.Period.NIGHT, GreetingSelector.periodFor(18))
        assertEquals(GreetingSelector.Period.NIGHT, GreetingSelector.periodFor(4))
        assertEquals(GreetingSelector.Period.NIGHT, GreetingSelector.periodFor(0))
    }
}
