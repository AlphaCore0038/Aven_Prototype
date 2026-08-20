package com.aven.ai.ui.navigation

/**
 * Aven navigation destinations. Providers may add destinations here without
 * touching the screen implementations.
 */
object Destinations {

    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val API_KEYS = "api_keys"

    const val ARG_PRIVATE = "private"
    const val ARG_CONVERSATION_ID = "conversationId"

    fun chat(private: Boolean = false, conversationId: Long = 0L): String =
        "$CHAT?$ARG_PRIVATE=$private&$ARG_CONVERSATION_ID=$conversationId"
}