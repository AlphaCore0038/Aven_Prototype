package com.aven.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aven.ai.ui.components.AvenMonogram

/**
 * The Aven welcome / new-chat state. Centered within the conversation area,
 * above the composer. Provides two real actions: focus the composer ("Ask Aven")
 * and open the starter-prompts sheet ("Explore").
 */
@Composable
fun WelcomeView(
    isPrivate: Boolean,
    onAskAven: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AvenMonogram(size = 180.dp)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Hey User....",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (isPrivate) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Private chat ...",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This conversation is not saved to recent chat history",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WelcomeAction(
                label = "Ask Aven",
                onClick = onAskAven
            )
            WelcomeAction(
                label = "Explore",
                onClick = onExplore
            )
        }
    }
}

@Composable
private fun WelcomeAction(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black,
        contentColor = Color.White,
        border = BorderStroke(2.dp, Color.White)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .semantics { contentDescription = label }
        )
    }
}