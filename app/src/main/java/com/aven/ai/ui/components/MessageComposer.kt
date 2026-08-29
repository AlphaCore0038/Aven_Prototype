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
import com.aven.ai.ui.theme.AvenSuccess

/**
 * The Aven composer — a single floating rounded container with:
 *   input zone  -> "+" action  |  multiline text  (no mic)
 *   divider
 *   control row -> model chip  |  connectivity indicator  |  send/stop
 *
 * The composer is intentionally controlled: [text]/[onTextChange] are owned by
 * the caller (ChatViewModel from Phase 3 onward) so suggestions and rotation
 * never desync it.
 */
@Composable
fun MessageComposer(
    hint: String,
    modelLabel: String,
    isOnline: Boolean,
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
    val borderColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val inputTextStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif)
    val canSend = text.isNotBlank() && !isGenerating && enabled
    val canType = enabled

    Surface(
        modifier = modifier,
        shape = outerShape,
        color = surfaceColor,
        border = BorderStroke(2.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // -------- input zone --------
            val fieldModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
            Surface(
                shape = innerShape,
                color = surfaceColor,
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
                        textStyle = inputTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
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
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                    color = surfaceColor,
                    border = BorderStroke(2.dp, borderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modelLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // status
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
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
                                .background(if (isOnline) AvenSuccess else MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Cursive),
                            color = if (isOnline) AvenSuccess else MaterialTheme.colorScheme.error
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
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
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
private fun SendStopButton(
    isGenerating: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isGenerating) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(150),
        label = "sendStopBg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary,
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