package com.aven.ai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aven.ai.ui.theme.rememberRefPalette

/**
 * The Aven composer — a single floating rounded container matching the
 * reference visual design: the "Chat with ..." line is the live prompt input,
 * the capsule below selects the model, and the white circle sends/stops.
 * The composer is intentionally controlled: [text]/[onTextChange] are owned by
 * the caller (ChatViewModel from Phase 3 onward) so suggestions and rotation
 * never desync it. No voice functionality exists; the reference's white button
 * is wired purely as the send/stop control.
 */
@Composable
fun MessageComposer(
    chatWithLabel: String,
    selectorText: String,
    isGenerating: Boolean,
    enabled: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenModelSelection: () -> Unit,
    onAddAction: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    isPrivate: Boolean = false,
    dark: Boolean = true
) {
    val ref = rememberRefPalette(dark)
    val outerShape = RoundedCornerShape(28.dp)
    val innerShape = RoundedCornerShape(22.dp)
    val borderColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val inputTextStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Default
    )
    val canSend = text.isNotBlank() && !isGenerating && enabled
    val canType = enabled

    Surface(
        modifier = if (isPrivate) {
            modifier.dashedBorder(ref.dashBorder)
        } else {
            modifier
        },
        shape = outerShape,
        color = ref.card,
        border = if (isPrivate) null else BorderStroke(1.dp, ref.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 16.dp)
        ) {
            // -------- prompt input: the "Chat with ..." line IS the live text field --------
            val fieldModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp, max = 132.dp)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
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
                                    text = chatWithLabel,
                                    fontSize = 17.sp,
                                    fontFamily = FontFamily.Default,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = (-0.2).sp,
                                    color = ref.labelGray
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // -------- selector capsule + send --------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpenModelSelection,
                    enabled = enabled && !isGenerating,
                    shape = innerShape,
                    color = ref.capsule,
                    border = null,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .semantics {
                            contentDescription = "Current model. Tap to change. $selectorText"
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectorText,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // send/stop button (template white paper-plane circle)
                val sendInteraction = remember { MutableInteractionSource() }
                val sendPressed by sendInteraction.collectIsPressedAsState()
                val sendScale by animateFloatAsState(
                    targetValue = if (sendPressed) 0.95f else 1f,
                    animationSpec = tween(100),
                    label = "sendScale"
                )
                Surface(
                    onClick = { if (isGenerating) onStop() else onSend() },
                    enabled = canSend || isGenerating,
                    shape = CircleShape,
                    color = if (dark) Color.White else Color.Black,
                    shadowElevation = 6.dp,
                    interactionSource = sendInteraction,
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            scaleX = sendScale
                            scaleY = sendScale
                        }
                        .alpha(if (canSend || isGenerating) 1f else 0.4f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGenerating) Icons.Default.Stop else PaperPlaneIcon,
                            contentDescription = if (isGenerating) "Stop generation" else "Send message",
                            tint = if (dark) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Paper-plane icon reproducing the exact SVG geometry from 01_main_chat.html
 * (24x24 viewBox). Used only for the send state; the stop state keeps the
 * Material Stop icon. No voice semantics anywhere.
 */
private val PaperPlaneIcon: ImageVector = ImageVector.Builder(
    name = "PaperPlane",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    addPath(
        pathData = listOf(
            PathNode.MoveTo(21.5f, 2.5f),
            PathNode.LineTo(2.5f, 10.5f),
            PathNode.LineTo(10.5f, 13.5f),
            PathNode.LineTo(13.5f, 21.5f),
            PathNode.LineTo(21.5f, 2.5f),
            PathNode.Close,
            PathNode.MoveTo(10.5f, 13.5f),
            PathNode.LineTo(21.5f, 2.5f)
        ),
        fill = SolidColor(Color.Black)
    )
}.build()

/**
 * Rounded-rectangle dashed outline used for the private/incognito composer.
 * Drawn as an overlay so the dynamic layout and IME behavior are untouched.
 */
private fun Modifier.dashedBorder(
    color: androidx.compose.ui.graphics.Color
): Modifier = this.drawWithContent {
    drawContent()
    val strokeWidth = 1.dp.toPx()
    val inset = strokeWidth / 2f
    val radius = 28.dp.toPx()
    drawRoundRect(
        color = color.copy(alpha = 0.6f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)
        )
    )
}

