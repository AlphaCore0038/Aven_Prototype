package com.aven.ai.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aven.ai.data.local.ConversationEntity
import com.aven.ai.ui.chat.LuckyRookieFamily
import com.aven.ai.ui.theme.RefPalette
import com.aven.ai.ui.theme.rememberRefPalette

/**
 * Aven navigation drawer: Pinned / Recent conversation history plus the
 * Settings / New-chat actions. Tap a chat to resume it; long-press opens a
 * small dropdown at the row with Pin/Unpin, Rename, and Delete. Private
 * conversations never reach this list (filtered by
 * [com.aven.ai.data.local.ChatDao]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvenDrawer(
    pinnedChats: List<ConversationEntity>,
    recentChats: List<ConversationEntity>,
    userInitial: String = "",
    avatarColor: Int? = null,
    onNewChat: () -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onTogglePin: (id: Long, pinned: Boolean) -> Unit,
    onRenameConversation: (id: Long, title: String) -> Unit,
    onDeleteConversation: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = true
) {
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<ConversationEntity?>(null) }

    val ref = rememberRefPalette(dark)
    ModalDrawerSheet(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding(),
        drawerContainerColor = ref.drawerBg,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerShape = RoundedCornerShape(0.dp)
    ) {
        // Template top offset is 56dp from the screen edge; the sheet already
        // consumes the status-bar inset, so only the remainder is added here.
        val contentTop =
            (56.dp - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = contentTop, bottom = 24.dp)
        ) {
            Text(
                text = "Aven",
                fontFamily = LuckyRookieFamily,
                fontSize = 24.sp,
                color = if (dark) ref.textStrong else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = ref.rowDivider)
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                DrawerSectionHeader("Pinned", dark = dark, topPad = 0.dp)
                if (pinnedChats.isEmpty()) {
                    DrawerEmptyHint("Nothing pinned yet", dark = dark)
                } else {
                    // Template: flat bullet rows, 15dp gaps, no card backgrounds.
                    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        pinnedChats.forEach { chat ->
                            DrawerChatRow(
                                title = chat.title.ifBlank { "Untitled chat" },
                                isPinned = true,
                                dark = dark,
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
                }

                Spacer(modifier = Modifier.height(24.dp))

                DrawerSectionHeader("Recents", dark = dark, topPad = 4.dp)
                if (recentChats.isEmpty()) {
                    DrawerEmptyHint("No chats yet", dark = dark)
                } else {
                    // Template: same flat rows, 17dp gaps.
                    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
                        recentChats.forEach { chat ->
                            DrawerChatRow(
                                title = chat.title.ifBlank { "Untitled chat" },
                                isPinned = false,
                                dark = dark,
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
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = onOpenSettings,
                    shape = CircleShape,
                    color = avatarColor?.let { Color(it) } ?: RefPalette.AvatarCoral,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userInitial.ifBlank { "A" },
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.38).sp,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    onClick = onNewChat,
                    shape = CircleShape,
                    color = if (dark) Color.White else MaterialTheme.colorScheme.inverseSurface,
                    contentColor = if (dark) Color(0xFF191919) else MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = DrawerPlusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New chat",
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.4).sp
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
    dark: Boolean,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val ref = rememberRefPalette(dark)

    Box(modifier = Modifier.fillMaxWidth()) {
        // Template: flat link row — hollow bullet + title, no card background.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .border(1.5.dp, ref.sectionGray, CircleShape)
            )
            Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title,
                    fontSize = 15.5.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.16).sp,
                    color = if (dark) ref.textStrong else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
private fun DrawerEmptyHint(text: String, dark: Boolean = true) {
    val ref = rememberRefPalette(dark)
    Text(
        text = text,
        fontSize = 14.sp,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        color = ref.sectionGray,
        modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 6.dp)
    )
}

/**
 * Template's New-chat plus: 24x24 viewBox, 2.5 stroke, round caps/joins.
 * The stroke brush is required or the path paints nothing; the Icon tint
 * recolors it to the pill's content color.
 */
private val DrawerPlusIcon: ImageVector = ImageVector.Builder(
    name = "DrawerPlus",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = listOf(
            PathNode.MoveTo(12f, 5f),
            PathNode.LineTo(12f, 19f),
            PathNode.MoveTo(5f, 12f),
            PathNode.LineTo(19f, 12f)
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
}.build()

@Composable
private fun DrawerSectionHeader(
    title: String,
    dark: Boolean = true,
    topPad: Dp = 0.dp
) {
    val ref = rememberRefPalette(dark)
    Text(
        text = title,
        fontSize = 15.sp,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.38).sp,
        color = ref.sectionGray,
        modifier = Modifier.padding(top = topPad, bottom = 12.dp)
    )
}

private const val MAX_RENAME_LENGTH = 60