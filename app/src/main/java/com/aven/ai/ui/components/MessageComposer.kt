package com.aven.ai.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.unit.dp
import com.aven.ai.ui.theme.AvenAccent
import com.aven.ai.ui.theme.AvenError
import com.aven.ai.ui.theme.AvenOnAccent
import com.aven.ai.ui.theme.AvenSuccess
import com.aven.ai.ui.theme.AvenWarning

/**
 * Privacy-critical online/offline status of the active AI provider.
 * Labels are always shown next to a color dot; color is never the only signal.
 */
enum class ComposerStatus(
    val label: String,
    val dotColor: Color,
    val textColor: Color,
    val contentDescription: String
) {
    OFFLINE("OFFLINE", AvenSuccess, AvenSuccess, "Local model — your messages stay on this device"),
    ONLINE("ONLINE", AvenSuccess, AvenSuccess, "Online provider — your messages are sent to the cloud provider"),
    LOADING("LOADING", AvenWarning, AvenWarning, "Local model is loading"),
    CONNECTING("CONNECTING", AvenWarning, AvenWarning, "Connecting to the online provider"),
    GENERATING("GENERATING", AvenAccent, AvenAccent, "The model is generating a response"),
    NOT_INSTALLED("NOT INSTALLED", AvenError, AvenError, "Local model is not installed"),
    UNAVAILABLE("UNAVAILABLE", AvenError, AvenError, "The selected provider is unavailable")
}

/**
 * The Aven composer — a single floating rounded container with:
 *   input zone  -> "+" action  |  multiline text  (no mic)
 *   divider
 *   control row -> model chip  |  status  |  send/stop
 *
 * The composer is intentionally controlled: [text]/[onTextChange] are owned by
 * the caller (ChatViewModel from Phase 3 onward) so suggestions and rotation
 * never desync it.
 */
@Composable
fun MessageComposer(
    hint: String,
    modelLabel: String,
    status: ComposerStatus,
    isGenerating: Boolean,
    enabled: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenModelSelection: () -> Unit,
    onAddAction: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val outerShape = RoundedCornerShape(24.dp)
    val innerShape = RoundedCornerShape(16.dp)
    val borderColor = Color.White
    val inputTextStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif)
    val canSend = text.isNotBlank() && !isGenerating && enabled
    val canType = enabled && !isGenerating

    Surface(
        modifier = modifier,
        shape = outerShape,
        color = Color.Black,
        border = BorderStroke(2.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // -------- input zone --------
            val fieldModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
            Surface(
                shape = innerShape,
                color = Color.Black,
                border = BorderStroke(2.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp, max = 132.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { if (canType) onTextChange(it) },
                        modifier = fieldModifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        textStyle = inputTextStyle.copy(color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Default
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "Prompt Here...",
                                        style = inputTextStyle,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // -------- control row --------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // model selection
                Surface(
                    onClick = onOpenModelSelection,
                    enabled = enabled && !isGenerating,
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black,
                    border = BorderStroke(2.dp, borderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "-- Model --",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // status
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black,
                    border = BorderStroke(2.dp, borderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (status == ComposerStatus.ONLINE || status == ComposerStatus.OFFLINE) Color(0xFF4CAF50) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (status == ComposerStatus.ONLINE) "Online" else status.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Cursive),
                            color = if (status == ComposerStatus.ONLINE || status == ComposerStatus.OFFLINE) Color(0xFF4CAF50) else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // send button
                IconButton(
                    onClick = { if (isGenerating) onStop() else onSend() },
                    enabled = canSend || isGenerating,
                    modifier = Modifier
                        .size(48.dp)
                        .border(2.dp, Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val surfaceColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = surfaceColor,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = "Current model. Tap to change. $label" }
            )
        }
    }
}

@Composable
private fun StatusIndicator(status: ComposerStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = status.contentDescription }
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(status.dotColor)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = status.textColor
        )
    }
}

@Composable
private fun SendStopButton(
    isGenerating: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isGenerating) {
            AvenError.copy(alpha = 0.16f)
        } else {
            AvenAccent
        },
        animationSpec = tween(150),
        label = "sendStopBg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isGenerating) AvenError else AvenOnAccent,
        animationSpec = tween(150),
        label = "sendStopIcon"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bgColor)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (isGenerating) onStop() else onSend() }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isGenerating,
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
            label = "sendStopIcon"
        ) { generating ->
            if (generating) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop generation",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}