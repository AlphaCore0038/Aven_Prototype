package com.aven.ai.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aven.ai.AvenApplication
import com.aven.ai.ui.drawer.AvenDrawer
import com.aven.ai.ui.navigation.AvenNavHost
import com.aven.ai.ui.navigation.Destinations
import kotlinx.coroutines.launch

/** Cap on how many recent chats the drawer lists (pinned are always all shown). */
private const val MAX_RECENT_CHATS = 50

/**
 * Aven application root: owns the navigation controller and the drawer state,
 * wires the drawer actions into real navigation, and feeds the drawer the
 * live saved-conversation history from Room.
 */
@Composable
fun AvenApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var modelSheetOpen by remember { mutableStateOf(false) }
    val activity = LocalContext.current as ComponentActivity
    val navBackStack by navController.currentBackStackEntryAsState()
    val isOnStart = navBackStack?.destination?.route?.startsWith(Destinations.CHAT) == true

    BackHandler(enabled = !drawerState.isOpen && !modelSheetOpen) {
        if (!navController.popBackStack()) {
            if (isOnStart) activity.finishAndRemoveTask()
        }
    }

    val app = LocalContext.current.applicationContext as AvenApplication

    val conversations by app.container.chatDao.observeVisibleConversations()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun navigateFresh(destination: String) {
        closeDrawer()
        navController.navigate(destination) {
            popUpTo(Destinations.CHAT) { inclusive = true }
            launchSingleTop = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AvenDrawer(
                pinnedChats = conversations.filter { it.isPinned },
                recentChats = conversations.filter { !it.isPinned }.take(MAX_RECENT_CHATS),
                onNewChat = { navigateFresh(Destinations.chat(false)) },
                onOpenPrivate = { navigateFresh(Destinations.chat(true)) },
                onOpenSettings = {
                    closeDrawer()
                    navController.navigate(Destinations.SETTINGS)
                },
                onOpenAbout = {
                    closeDrawer()
                    navController.navigate(Destinations.ABOUT)
                },
                onOpenConversation = { id ->
                    navigateFresh(Destinations.chat(false, id))
                },
                onTogglePin = { id, pinned ->
                    scope.launch { app.container.chatDao.setPinned(id, pinned) }
                },
                onRenameConversation = { id, title ->
                    scope.launch { app.container.chatDao.renameConversation(id, title) }
                },
                onDeleteConversation = { id ->
                    scope.launch { app.container.chatDao.deleteConversation(id) }
                }
            )
        }
    ) {
        AvenNavHost(
            navController = navController,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenModelSelection = { /* model selection lives in the composer chip */ },
            modelSheetOpen = modelSheetOpen,
            onModelSheetOpenChange = { modelSheetOpen = it }
        )
    }
}