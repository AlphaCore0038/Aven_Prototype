package com.aven.ai.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aven.ai.R
import com.aven.ai.data.local.ConversationEntity

/**
 * Aven navigation drawer: branding, Pinned / Recent conversation history,
 * and the Settings / New-chat actions. Tap a chat to resume it; long-press
 * opens a small dropdown at the row with Pin/Unpin, Rename, and Delete.
 * Private conversations never reach this list (filtered by
 * [com.aven.ai.data.local.ChatDao]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvenDrawer(
    pinnedChats: List<ConversationEntity>,
    recentChats: List<ConversationEntity>,
    onNewChat: () -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onTogglePin: (id: Long, pinned: Boolean) -> Unit,
    onRenameConversation: (id: Long, title: String) -> Unit,
    onDeleteConversation: (id: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<ConversationEntity?>(null) }

    ModalDrawerSheet(
        modifier = modifier
            .width(320.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerShape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.logo_wordmark),
                contentDescription = "Aven",
                modifier = Modifier.height(28.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 2.dp)
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                DrawerSectionHeader("Pinned Chats...")
                if (pinnedChats.isEmpty()) {
                    DrawerEmptyHint("Nothing pinned yet")
                } else {
                    pinnedChats.forEach { chat ->
                        DrawerChatRow(
                            title = chat.title.ifBlank { "Untitled chat" },
                            isPinned = true,
                            onClick = { onOpenConversation(chat.id) },
                            onPinToggle = {
                                onTogglePin(chat.id, false)
                            },
                            onRenameRequest = {
                                renameTarget = chat
                                renameDraft = chat.title
                            },
                            onDeleteRequest = { confirmDelete = chat }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                DrawerSectionHeader("Recent Chats...")
                if (recentChats.isEmpty()) {
                    DrawerEmptyHint("No chats yet")
                } else {
                    recentChats.forEach { chat ->
                        DrawerChatRow(
                            title = chat.title.ifBlank { "Untitled chat" },
                            isPinned = false,
                            onClick = { onOpenConversation(chat.id) },
                            onPinToggle = {
                                onTogglePin(chat.id, true)
                            },
                            onRenameRequest = {
                                renameTarget = chat
                                renameDraft = chat.title
                            },
                            onDeleteRequest = { confirmDelete = chat }
                        )
                    }
                }
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
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    onClick = onNewChat,
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
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

    renameTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Title") },
                    singleLine = true
                )
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = renameDraft.trim().isNotBlank(),
                    onClick = {
                        onRenameConversation(chat.id, renameDraft.trim().take(MAX_RENAME_LENGTH))
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            }
        )
    }

    confirmDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete chat?") },
            text = {
                Text(
                    text = "\"${chat.title.ifBlank { "Untitled chat" }}\" and all its messages " +
                        "will be permanently deleted."
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConversation(chat.id)
                        confirmDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

/** A single saved conversation row. Tap to open; long-press opens a small
 *  dropdown anchored to this row with Pin/Unpin, Rename and Delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerChatRow(
    title: String,
    isPinned: Boolean,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuExpanded = true }
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin" else "Pin") },
                onClick = {
                    menuExpanded = false
                    onPinToggle()
                }
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuExpanded = false
                    onRenameRequest()
                }
            )
            DropdownMenuItem(
                text = {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    menuExpanded = false
                    onDeleteRequest()
                }
            )
        }
    }
}

@Composable
private fun DrawerEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

private const val MAX_RENAME_LENGTH = 60