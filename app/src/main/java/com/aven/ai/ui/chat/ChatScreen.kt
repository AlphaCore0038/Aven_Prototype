package com.aven.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aven.ai.R
import com.aven.ai.engine.AiModelOption
import com.aven.ai.engine.EngineKind
import com.aven.ai.engine.local.LocalModelState
import com.aven.ai.ui.components.MessageComposer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The main chat screen. Owns the ChatViewModel and renders the persisted
 * conversation from Room, the live streaming reply, and the composer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    isPrivate: Boolean,
    conversationId: Long,
    onOpenDrawer: () -> Unit,
    onTogglePrivate: () -> Unit,
    onOpenModelSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
    val controller = viewModel.controller
    val state by controller.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val localModelState by viewModel.localModelState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    var text by rememberSaveable { mutableStateOf("") }
    var modelSheetOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var autoFollow by remember { mutableStateOf(true) }

    LaunchedEffect(conversationId, isPrivate) {
        autoFollow = true
        controller.start(conversationId, isPrivate)
    }

    // Snapshot-aware: canScrollForward is derived from layoutInfo, so it
    // must be observed via snapshotFlow, not as a LaunchedEffect key.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (dragging, canForward) ->
                if (dragging && canForward) autoFollow = false
                else if (!canForward) autoFollow = true
            }
    }

    val itemCount = state.messages.size + if (state.isGenerating) 1 else 0
    LaunchedEffect(itemCount, state.streamingText.length) {
        val target = itemCount - 1
        if (target <= 0) return@LaunchedEffect
        if (!autoFollow || listState.isScrollInProgress) return@LaunchedEffect
        // Viewport check: only follow when already at bottom. A single-item
        // list (new chat, only streaming) has no scroll, so skip.
        if (listState.canScrollForward) return@LaunchedEffect
        listState.scrollToItem(target)
    }

    val currentModelLabel = remember(settings, localModelState) {
        val selected = viewModel.models.firstOrNull {
            it.providerId == settings.defaultProviderId && it.modelId == settings.defaultModelId
        }
        val actual = selected ?: viewModel.models.firstOrNull { model ->
            if (model.providerId == "local") localModelState is LocalModelState.Ready else true
        } ?: viewModel.models.first()

        "${actual.displayName} • ${providerName(actual.providerId)}"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ChatTopBar(
            isPrivate = isPrivate,
            onOpenDrawer = onOpenDrawer,
            onTogglePrivate = onTogglePrivate,
            hasMessages = state.hasMessages
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.hasMessages || state.isGenerating || state.lastError != null) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageRow(message)
                    }
                    if (state.isGenerating) {
                        item(key = "streaming") {
                            StreamingRow(text = state.streamingText)
                        }
                    }
                    if (state.lastError != null) {
                        item(key = "error") {
                            ErrorRow(message = state.lastError!!)
                        }
                    }
                }
            } else {
                WelcomeView(isPrivate = isPrivate, userName = userName)
            }
        }

        MessageComposer(
            hint = if (text.isEmpty()) {
                stringResource(R.string.composer_hint)
            } else {
                stringResource(R.string.composer_hint_chat)
            },
            modelLabel = currentModelLabel,
            isOnline = isOnline,
            isGenerating = state.isGenerating,
            enabled = true,
            text = text,
            onTextChange = { text = it },
            onSend = {
                controller.send(text)
                text = ""
            },
            onStop = { controller.stop() },
            onOpenModelSelection = { modelSheetOpen = true },
            onAddAction = { },
            focusRequester = focusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Text(
            text = stringResource(R.string.footer_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        )
    }

    if (modelSheetOpen) {
        ModelSelectionSheet(
            models = viewModel.models,
            current = viewModel.models.firstOrNull {
                it.providerId == settings.defaultProviderId && it.modelId == settings.defaultModelId
            },
            localModelState = localModelState,
            onPick = { option ->
                scope.launch { viewModel.selectModel(option) }
                modelSheetOpen = false
            },
            onInstallLocalModel = viewModel::installLocalModel,
            onCancelLocalModelDownload = viewModel::cancelLocalModelDownload,
            onRemoveLocalModel = {
                modelSheetOpen = false
                viewModel.removeLocalModel()
            },
            onDismiss = { modelSheetOpen = false }
        )
    }
}

private fun providerName(providerId: String): String = when (providerId) {
    "zen" -> "Zen"
    "openrouter" -> "OpenRouter"
    "zai" -> "Z.AI"
    "local" -> "On-device"
    else -> providerId
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSheet(
    models: List<AiModelOption>,
    current: AiModelOption?,
    localModelState: LocalModelState,
    onPick: (AiModelOption) -> Unit,
    onInstallLocalModel: () -> Unit,
    onCancelLocalModelDownload: () -> Unit,
    onRemoveLocalModel: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(32.dp)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .padding(4.dp)
                    )
                }
                Text(
                    text = "Model-Selection",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            models.forEach { option ->
                ModelOptionRow(
                    option = option,
                    isCurrent = option.providerId == current?.providerId &&
                        option.modelId == current?.modelId,
                    localModelState = localModelState,
                    onClick = {
                        // Button-only interaction for the offline model until
                        // it is installed; the row itself selects once Ready.
                        if (option.kind == EngineKind.LOCAL &&
                            localModelState !is LocalModelState.Ready
                        ) {
                            // no-op
                        } else {
                            onPick(option)
                        }
                    },
                    onInstallLocal = onInstallLocalModel,
                    onCancelLocalDownload = onCancelLocalModelDownload,
                    onRemoveLocal = onRemoveLocalModel
                )
            }
        }
    }
}

@Composable
private fun ModelOptionRow(
    option: AiModelOption,
    isCurrent: Boolean,
    localModelState: LocalModelState,
    onClick: () -> Unit,
    onInstallLocal: () -> Unit,
    onCancelLocalDownload: () -> Unit,
    onRemoveLocal: () -> Unit
) {
    val isLocal = option.kind == EngineKind.LOCAL
    var confirmRemove by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isLocal) localModelDescription(localModelState) else option.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (localModelState is LocalModelState.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isLocal) {
                    LocalModelStateAction(
                        state = localModelState,
                        onInstall = onInstallLocal,
                        onCancel = onCancelLocalDownload,
                        onRemoveRequest = { confirmRemove = true }
                    )
                }
            }
            val downloading = localModelState as? LocalModelState.Downloading
            if (isLocal && downloading != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { downloading.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Delete offline model?") },
            text = {
                Text(
                    "This removes the Llama 3.2 1B files (~0.8 GB). " +
                        "You can download it again anytime."
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemoveLocal() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

/** State-aware trailing control + description for the offline model row. */
private fun localModelDescription(state: LocalModelState): String = when (state) {
    LocalModelState.NotInstalled -> "~0.8 GB · tap ⬇ to download"
    is LocalModelState.Downloading -> "Downloading… ${(state.progress * 100).toInt()}%"
    is LocalModelState.Failed -> state.message
    LocalModelState.Loading -> "Preparing model…"
    LocalModelState.Ready -> "Ready — runs offline"
}

@Composable
private fun LocalModelStateAction(
    state: LocalModelState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRemoveRequest: () -> Unit
) {
    when (state) {
        LocalModelState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Offline model installed",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(22.dp)
            )
            IconButton(onClick = onRemoveRequest) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove offline model",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        is LocalModelState.Downloading -> IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel download",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
        LocalModelState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
        else -> IconButton(onClick = onInstall) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download model (~0.8 GB)",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    isPrivate: Boolean,
    onOpenDrawer: () -> Unit,
    onTogglePrivate: () -> Unit,
    hasMessages: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open menu",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!hasMessages) {
            IconButton(
                onClick = onTogglePrivate,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.private_chat_icon),
                    contentDescription = "Toggle Private Mode",
                    colorFilter = ColorFilter.tint(
                        if (isPrivate) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                        blendMode = BlendMode.Multiply
                    ),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}