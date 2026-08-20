package com.aven.ai.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import com.aven.ai.ui.drawer.AvenDrawer
import com.aven.ai.ui.navigation.AvenNavHost
import com.aven.ai.ui.navigation.Destinations
import kotlinx.coroutines.launch

/**
 * Aven application root: owns the navigation controller and the drawer state,
 * and wires the drawer actions into real navigation.
 */
@Composable
fun AvenApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
                onNewChat = { navigateFresh(Destinations.chat(false)) },
                onOpenPrivate = { navigateFresh(Destinations.chat(true)) },
                onOpenSettings = {
                    closeDrawer()
                    navController.navigate(Destinations.SETTINGS)
                },
                onOpenAbout = {
                    closeDrawer()
                    navController.navigate(Destinations.ABOUT)
                }
            )
        }
    ) {
        AvenNavHost(
            navController = navController,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenModelSelection = { /* Phase 6: model selection bottom sheet */ }
        )
    }
}