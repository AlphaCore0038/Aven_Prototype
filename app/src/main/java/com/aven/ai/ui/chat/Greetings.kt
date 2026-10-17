package com.aven.ai.ui.chat

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.aven.ai.R
import java.time.Clock
import java.time.LocalTime
import kotlin.random.Random

/** LuckyRookie Regular, applied only to the fresh-chat greeting. */
val LuckyRookieFamily = FontFamily(Font(R.font.luckyrookie_regular, FontWeight.Normal))

/**
 * Time-aware fresh-chat greeting selector. All strings are locally predefined
 * (no network, no LLM); `{name}` renders only where the template contains it.
 * Pure apart from the in-memory [lastShown] guard that prevents repeats.
 */
object GreetingSelector {

    val NIGHT = listOf(
        "Hello, night owl..",
        "Still awake ¿",
        "Burning the midnight oil ¿",
        "Late-night thoughts ¿",
        "What are we working on tonight ¿"
    )

    val MORNING = listOf(
        "Good morning, {name}.",
        "Fresh day, fresh ideas.",
        "Ready to get started ¿",
        "What's on the agenda today ¿"
    )

    val AFTERNOON = listOf(
        "Hey, {name}.",
        "What are we working on ¿",
        "What's on your mind ¿",
        "Ready when you are."
    )

    val GENERAL = listOf(
        "Hey, {name}.",
        "Good to see you.",
        "What's on your mind ¿",
        "What are we building today ¿",
        "Where do we start ¿",
        "Let's make something happen.",
        "What can I help you with ¿",
        "Back again ¿"
    )

    val PERSONALITY = listOf(
        "You're back.",
        "I was wondering when you'd show up.",
        "Alright, what's the plan ¿",
        "Got something interesting ¿",
        "What are we cooking today ¿",
        "Let's see what you've got...",
        "Another idea ¿"
    )

    /** Last greeting shown in this process; never repeated back-to-back. */
    internal var lastShown: String? = null

    fun periodFor(hour: Int): Period = when (hour) {
        in 5..11 -> Period.MORNING
        in 12..17 -> Period.AFTERNOON
        else -> Period.NIGHT
    }

    /**
     * Draws one greeting for [userName] at the [clock] time. The time pool is
     * weighted over general/personality (5:3:2) so off-peak pools still mix in.
     * Deterministic under a seeded [random]; safe to call from composition when
     * guarded by remember (callers must not invoke it on every recomposition).
     */
    fun next(
        userName: String,
        clock: Clock = Clock.systemDefaultZone(),
        random: Random = Random.Default
    ): String {
        val timePool = when (periodFor(LocalTime.now(clock).hour)) {
            Period.MORNING -> MORNING
            Period.AFTERNOON -> AFTERNOON
            Period.NIGHT -> NIGHT
        }
        val category = when (random.nextInt(10)) {
            in 0..4 -> timePool
            in 5..7 -> GENERAL
            else -> PERSONALITY
        }
        val chosen = pickFrom(category.map { render(it, userName) }, random, lastShown)
        lastShown = chosen
        return chosen
    }

    /** Uniform pick excluding [exclude]; falls back to the full pool when that leaves nothing. */
    internal fun pickFrom(
        candidates: List<String>,
        random: Random,
        exclude: String?
    ): String {
        require(candidates.isNotEmpty()) { "greeting pool must not be empty" }
        val eligible = candidates.filterNot { it == exclude }.ifEmpty { candidates }
        return eligible[random.nextInt(eligible.size)]
    }

    internal fun render(template: String, userName: String): String =
        template.replace("{name}", userName.ifBlank { "User" })

    enum class Period { MORNING, AFTERNOON, NIGHT }
}
