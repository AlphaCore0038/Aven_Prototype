package com.aven.ai.ui.navigation

/**
 * Aven navigation destinations. Providers may add destinations here without
 * touching the screen implementations.
 */
object Destinations {

    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val MODELS = "models"
    const val MODEL_EDIT = "model_edit"
    const val USER_DETAILS = "user_details"

    const val ARG_PRIVATE = "private"
    const val ARG_CONVERSATION_ID = "conversationId"
    const val ARG_MODEL_ID = "modelId"

    fun chat(private: Boolean = false, conversationId: Long = 0L): String =
        "$CHAT?$ARG_PRIVATE=$private&$ARG_CONVERSATION_ID=$conversationId"

    /** Online-model editor; empty [id] means "add a new model". */
    fun modelEdit(id: String = ""): String = "$MODEL_EDIT?$ARG_MODEL_ID=$id"
}