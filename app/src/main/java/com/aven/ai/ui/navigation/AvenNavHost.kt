package com.aven.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aven.ai.AvenApplication
import com.aven.ai.ui.about.AboutScreen
import com.aven.ai.ui.chat.ChatScreen
import com.aven.ai.ui.settings.ApiKeysScreen
import com.aven.ai.ui.settings.SettingsScreen
import com.aven.ai.ui.settings.UserDetailsScreen

/**
 * Aven navigation graph. The Chat screen carries optional [Destinations.ARG_PRIVATE]
 * and [Destinations.ARG_CONVERSATION_ID] arguments so new chats, private
 * chats, and resumed conversations all reach the same screen.
 */
@Composable
fun AvenNavHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    onOpenModelSelection: () -> Unit,
    modelSheetOpen: Boolean,
    onModelSheetOpenChange: (Boolean) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.chat(),
        modifier = modifier
    ) {
        composable(
            route = "${Destinations.CHAT}?${Destinations.ARG_PRIVATE}={${Destinations.ARG_PRIVATE}}&${Destinations.ARG_CONVERSATION_ID}={${Destinations.ARG_CONVERSATION_ID}}",
            arguments = listOf(
                navArgument(Destinations.ARG_PRIVATE) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(Destinations.ARG_CONVERSATION_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val isPrivate =
                backStackEntry.arguments?.getBoolean(Destinations.ARG_PRIVATE) ?: false
            val conversationId =
                backStackEntry.arguments?.getLong(Destinations.ARG_CONVERSATION_ID) ?: 0L
            ChatScreen(
                isPrivate = isPrivate,
                conversationId = conversationId,
                onOpenDrawer = onOpenDrawer,
                onTogglePrivate = {
                    navController.navigate(Destinations.chat(!isPrivate, conversationId)) {
                        popUpTo(Destinations.CHAT) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenModelSelection = onOpenModelSelection,
                modelSheetOpen = modelSheetOpen,
                onModelSheetOpenChange = onModelSheetOpenChange
            )
        }

        composable(Destinations.SETTINGS) {
            val app = androidx.compose.ui.platform.LocalContext.current
                .applicationContext as AvenApplication
            SettingsScreen(
                settingsRepository = app.container.settingsRepository,
                chatDao = app.container.chatDao,
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Destinations.ABOUT) },
                onOpenApiKeys = { navController.navigate(Destinations.API_KEYS) },
                onOpenUserDetails = { navController.navigate(Destinations.USER_DETAILS) }
            )
        }

        composable(Destinations.USER_DETAILS) {
            val app = androidx.compose.ui.platform.LocalContext.current
                .applicationContext as AvenApplication
            UserDetailsScreen(
                userProfileRepository = app.container.userProfileRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.API_KEYS) {
            val app = androidx.compose.ui.platform.LocalContext.current
                .applicationContext as AvenApplication
            ApiKeysScreen(
                keyManager = app.container.keyManager,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}