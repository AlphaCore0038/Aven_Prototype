package com.aven.ai.ui.components

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

private val CodeBlockSurface = Color(0xFF252525)

private fun formatLanguageLabel(raw: String?): String {
    if (raw.isNullOrBlank()) return "Code"
    return when (raw.trim().lowercase()) {
        "python", "py" -> "Python"
        "kotlin", "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "c" -> "C"
        "cpp", "c++", "cc", "cxx" -> "C++"
        "bash", "sh", "shell", "zsh" -> "Bash"
        "cmd", "bat" -> "CMD"
        "json" -> "JSON"
        "xml", "html" -> "XML"
        "sql" -> "SQL"
        "markdown", "md" -> "Markdown"
        else -> raw.trim().replaceFirstChar { it.uppercaseChar() }
    }
}

@Composable
fun AssistantMarkdown(
    content: String,
    modifier: Modifier = Modifier
) {
    Markdown(
        content = content,
        modifier = modifier,
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeText = Color.White,
            inlineCodeText = MaterialTheme.colorScheme.onSurface,
            linkText = MaterialTheme.colorScheme.primary,
            codeBackground = CodeBlockSurface,
            inlineCodeBackground = CodeBlockSurface,
            dividerColor = MaterialTheme.colorScheme.outlineVariant,
            tableText = MaterialTheme.colorScheme.onSurface,
            tableBackground = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.displayMedium,
            h2 = MaterialTheme.typography.headlineSmall,
            h3 = MaterialTheme.typography.titleLarge,
            h4 = MaterialTheme.typography.titleMedium,
            h5 = MaterialTheme.typography.titleMedium,
            h6 = MaterialTheme.typography.titleMedium,
            text = MaterialTheme.typography.bodyLarge,
            paragraph = MaterialTheme.typography.bodyLarge,
            quote = MaterialTheme.typography.bodyLarge,
            bullet = MaterialTheme.typography.bodyLarge,
            list = MaterialTheme.typography.bodyLarge,
            ordered = MaterialTheme.typography.bodyLarge,
            code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            inlineCode = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            link = MaterialTheme.typography.bodyLarge
        ),
        components = markdownComponents(
            codeBlock = {
                val lang = it.node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(it.content)?.toString()
                val code = if (it.node.children.isNotEmpty()) {
                    val start = it.node.children[0].startOffset
                    val end = it.node.children[it.node.children.size - 1].endOffset
                    it.content.subSequence(start, end).toString().replaceIndent()
                } else it.content
                CodeBlockWithCopy(code = code, language = lang)
            },
            codeFence = {
                val lang = it.node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(it.content)?.toString()
                val code = if (it.node.children.size >= 3) {
                    val start = it.node.children[2].startOffset
                    val min = if (lang != null && it.node.children.size > 3) 3 else 2
                    val end = it.node.children[(it.node.children.size - 2).coerceAtLeast(min)].endOffset
                    it.content.subSequence(start, end).toString().replaceIndent()
                } else it.content
                CodeBlockWithCopy(code = code, language = lang)
            }
        )
    )
}

@Composable
private fun CodeBlockWithCopy(
    code: String,
    language: String?
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var copied by remember { mutableStateOf(false) }
    val displayLang = formatLanguageLabel(language)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CodeBlockSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayLang,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (copied) {
                        Text(
                            text = "Copied",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("Aven code", code))
                                    )
                                    copied = true
                                    delay(1500)
                                    copied = false
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White
                )
            }
        }
    }
}
