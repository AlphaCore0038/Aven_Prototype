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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.aven.ai.ui.theme.RefPalette
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aven.ai.R
import com.aven.ai.data.settings.SettingsRepository
import com.aven.ai.engine.AiModelOption
import com.aven.ai.engine.EngineKind
import com.aven.ai.engine.local.LocalModelState
import com.aven.ai.ui.components.MessageComposer
import com.aven.ai.ui.theme.rememberRefPalette
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The main chat screen. Owns the ChatViewModel and renders the persisted
 * conversation from Room, the live streaming reply, and the composer.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    isPrivate: Boolean,
    conversationId: Long,
    onOpenDrawer: () -> Unit,
    onTogglePrivate: () -> Unit,
    onOpenModelSelection: () -> Unit,
    modelSheetOpen: Boolean,
    onModelSheetOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
    val controller = viewModel.controller
    val state by controller.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val localModelState by viewModel.localModelState.collectAsStateWithLifecycle()
    val gemmaModelState by viewModel.gemmaModelState.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    var text by rememberSaveable { mutableStateOf("") }
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
    // ChatGPT-style follow: pin to the latest content while generating unless
    // the user detached by scrolling up (autoFollow=false). No canScrollForward
    // gate here — that check deadlocked follow, since a growing streaming item
    // always leaves content below the viewport on exactly the frames that must
    // scroll. Detachment is already tracked by autoFollow above.
    LaunchedEffect(itemCount, state.streamingText.length) {
        val target = itemCount - 1
        if (target <= 0) return@LaunchedEffect
        if (!autoFollow || listState.isScrollInProgress) return@LaunchedEffect
        val clamped = target.coerceAtMost(listState.layoutInfo.totalItemsCount - 1)
        if (clamped < 0) return@LaunchedEffect
        listState.scrollToItem(clamped)
    }
    // Floating "back to bottom" pill (ChatGPT ↓): visible whenever the list
    // has content below the viewport, generating or idle. Direct read (not
    // remember+derivedStateOf): itemCount is a plain Int, so capturing it in a
    // remembered lambda would freeze the first-composition value (0, before
    // Room emits) and pin the pill invisible forever. canScrollForward only
    // notifies on true/false flips, so this stays cheap.
    val showJumpToBottom = itemCount > 0 && listState.canScrollForward

    val models by viewModel.models.collectAsStateWithLifecycle()
    val dark = settings.theme != SettingsRepository.AppSettings.THEME_LIGHT
    val ref = rememberRefPalette(dark)

    val currentOption = remember(models, settings, localModelState) {
        val selected = models.firstOrNull {
            it.providerId == settings.defaultProviderId && it.modelId == settings.defaultModelId
        }
        selected ?: models.firstOrNull { model ->
            if (model.providerId == "local") localModelState is LocalModelState.Ready else true
        } ?: models.firstOrNull()
    }
    // Top line is the live prompt input; capsule below selects the model.
    // Provider shown only for remote options, where the registry stores a real one.
    val chatWithLabel = currentOption?.let { "Chat with ${it.displayName}..." } ?: "Chat with..."
    val selectorText = currentOption?.let { option ->
        if (option.kind == EngineKind.REMOTE && option.description.isNotBlank()) {
            "${option.displayName} • ${option.description}"
        } else {
            option.displayName
        }
    } ?: "No model selected"

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
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
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
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
                WelcomeView(isPrivate = isPrivate, userName = userName, dark = dark)
            }
            if (showJumpToBottom) {
                IconButton(
                    onClick = {
                        autoFollow = true
                        scope.launch {
                            val last = (listState.layoutInfo.totalItemsCount - 1)
                                .coerceAtLeast(0)
                            listState.animateScrollToItem(last)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .size(48.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to latest messages",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        MessageComposer(
            chatWithLabel = chatWithLabel,
            selectorText = selectorText,
            isGenerating = state.isGenerating,
            enabled = true,
            text = text,
            onTextChange = { text = it },
            onSend = {
                controller.send(text)
                text = ""
            },
            onStop = { controller.stop() },
            onOpenModelSelection = { if (!state.isGenerating) onModelSheetOpenChange(true) },
            onAddAction = { },
            focusRequester = focusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            isPrivate = isPrivate,
            dark = dark
        )
    }

    if (modelSheetOpen) {
        ModelSelectionSheet(
            models = models,
            current = models.firstOrNull {
                it.providerId == settings.defaultProviderId && it.modelId == settings.defaultModelId
            },
            llamaState = localModelState,
            gemmaState = gemmaModelState,
            dark = dark,
            onPick = { option ->
                if (state.isGenerating) return@ModelSelectionSheet
                scope.launch { viewModel.selectModel(option) }
                onModelSheetOpenChange(false)
            },
            onInstallFor = { id -> viewModel.installLocalModelFor(id) },
            onCancelFor = { id -> viewModel.cancelLocalModelDownloadFor(id) },
            onRemoveFor = { id ->
                onModelSheetOpenChange(false)
                viewModel.removeLocalModelFor(id)
            },
            onDismiss = { onModelSheetOpenChange(false) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSheet(
    models: List<AiModelOption>,
    current: AiModelOption?,
    llamaState: LocalModelState,
    gemmaState: LocalModelState,
    dark: Boolean,
    onPick: (AiModelOption) -> Unit,
    onInstallFor: (String) -> Unit,
    onCancelFor: (String) -> Unit,
    onRemoveFor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ref = rememberRefPalette(dark)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = ref.sheetBg,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 4.dp, bottom = 20.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Select model",
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            if (models.none { it.kind == EngineKind.REMOTE }) {
                Text(
                    text = "No online models configured. Add one in Settings → Online models.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ref.rowBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    models.forEachIndexed { index, option ->
                        val stateForOption = when (option.modelId) {
                            com.aven.ai.engine.local.LocalEngine.GEMMA_ID -> gemmaState
                            com.aven.ai.engine.local.LocalEngine.MODEL_ID -> llamaState
                            else -> if (option.kind == EngineKind.LOCAL) llamaState else LocalModelState.Ready
                        }
                        ModelOptionRow(
                            option = option,
                            isCurrent = option.providerId == current?.providerId &&
                                option.modelId == current?.modelId,
                            localModelState = stateForOption,
                            dark = dark,
                            onClick = {
                                if (option.kind == EngineKind.LOCAL &&
                                    stateForOption !is LocalModelState.Ready
                                ) {
                                    // no-op until installed
                                } else {
                                    onPick(option)
                                }
                            },
                            onInstallLocal = { onInstallFor(option.modelId) },
                            onCancelLocalDownload = { onCancelFor(option.modelId) },
                            onRemoveLocal = { onRemoveFor(option.modelId) }
                        )
                        if (index < models.lastIndex) {
                            HorizontalDivider(
                                color = ref.rowDivider,
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelOptionRow(
    option: AiModelOption,
    isCurrent: Boolean,
    localModelState: LocalModelState,
    dark: Boolean,
    onClick: () -> Unit,
    onInstallLocal: () -> Unit,
    onCancelLocalDownload: () -> Unit,
    onRemoveLocal: () -> Unit
) {
    val isLocal = option.kind == EngineKind.LOCAL
    var confirmRemove by remember { mutableStateOf(false) }
    val ref = rememberRefPalette(dark)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.displayName,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLocal) localModelDescription(option, localModelState) else option.description,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Normal,
                        color = if (localModelState is LocalModelState.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            ref.descGray
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isCurrent && !isLocal) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected model",
                        tint = RefPalette.CheckBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isLocal) {
                    LocalModelStateAction(
                        state = localModelState,
                        sizeLabel = option.sizeLabel,
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
                    "This removes the ${option.displayName} files (${option.sizeLabel ?: "~1 GB"}). " +
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
private fun localModelDescription(option: AiModelOption, state: LocalModelState): String = when (state) {
    LocalModelState.NotInstalled -> "${option.sizeLabel ?: "~1 GB"} · tap ⬇ to download"
    is LocalModelState.Downloading -> "Downloading… ${(state.progress * 100).toInt()}%"
    is LocalModelState.Failed -> state.message
    LocalModelState.Loading -> "Preparing model…"
    LocalModelState.Ready -> "Ready — runs offline"
}

@Composable
private fun LocalModelStateAction(
    state: LocalModelState,
    sizeLabel: String?,
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
                contentDescription = "Download model (${sizeLabel ?: "~1 GB"})",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Template hamburger mark: three 2dp bars (20/20/14dp) with 5dp gaps. */
@Composable
private fun MenuLine(
    width: androidx.compose.ui.unit.Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
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
            .height(56.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.Start
            ) {
                MenuLine(width = 20.dp, color = MaterialTheme.colorScheme.onSurface)
                MenuLine(width = 20.dp, color = MaterialTheme.colorScheme.onSurface)
                MenuLine(width = 14.dp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        if (isPrivate) {
            Text(
                text = "Incognito chat",
                fontSize = 17.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (!hasMessages) {
            IconButton(
                onClick = onTogglePrivate,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
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