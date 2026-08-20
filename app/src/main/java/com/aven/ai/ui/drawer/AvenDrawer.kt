package com.aven.ai.ui.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aven.ai.ui.components.AvenFullLogo

/**
 * Aven navigation drawer: branding, New Chat, search, pinned/recent chat
 * sections, and the Settings / About actions. History sections are wired to
 * the conversation repository in Phase 6.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvenDrawer(
    onNewChat: () -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier
            .width(320.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        drawerContainerColor = Color.Black,
        drawerContentColor = Color.White,
        drawerShape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "Aven",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White, thickness = 2.dp)
            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.weight(1f)) {
                DrawerSectionHeader("Pinned Chats...")
                Spacer(modifier = Modifier.height(48.dp)) // Placeholder for list
                
                DrawerSectionHeader("Recent Chats...")
                Spacer(modifier = Modifier.height(48.dp)) // Placeholder for list
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(56.dp)
                        .border(2.dp, Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }

                Surface(
                    onClick = onNewChat,
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF2C2C2C),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "+ New chat",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

