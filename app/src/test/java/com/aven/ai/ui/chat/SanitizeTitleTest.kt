package com.aven.ai.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SanitizeTitleTest {

    @Test
    fun `plain title passes through`() {
        assertEquals("Plan my day", sanitizeTitle("Plan my day"))
    }

    @Test
    fun `strips wrapping double quotes`() {
        assertEquals("Plan my day", sanitizeTitle("\"Plan my day\""))
    }

    @Test
    fun `strips wrapping single quotes and backticks`() {
        assertEquals("Plan my day", sanitizeTitle("'Plan my day'"))
        assertEquals("Plan my day", sanitizeTitle("`Plan my day`"))
    }

    @Test
    fun `removes a title prefix`() {
        assertEquals("Plan my day", sanitizeTitle("Title: Plan my day"))
        assertEquals("Plan my day", sanitizeTitle("title - Plan my day"))
        assertEquals("Plan my day", sanitizeTitle("TITLE: \"Plan my day\""))
    }

    @Test
    fun `takes only the first line`() {
        assertEquals("Plan my day", sanitizeTitle("Plan my day\nHere is why this is good"))
    }

    @Test
    fun `collapses internal whitespace`() {
        assertEquals("Plan my day", sanitizeTitle("  Plan   my    day  "))
    }

    @Test
    fun `drops a trailing period`() {
        assertEquals("Plan my day", sanitizeTitle("Plan my day."))
    }

    @Test
    fun `caps the length at forty eight characters`() {
        val long = "a".repeat(100)
        assertEquals(48, sanitizeTitle(long).length)
        assertEquals("a".repeat(48), sanitizeTitle(long))
    }

    @Test
    fun `blank input yields blank`() {
        assertEquals("", sanitizeTitle(""))
        assertEquals("", sanitizeTitle("   "))
        assertEquals("", sanitizeTitle("\"\""))
    }
}