package com.aven.ai.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aven.ai.R
import com.aven.ai.ui.components.AvenMonogram
import com.aven.ai.ui.theme.rememberRefPalette

/**
 * The Aven welcome / new-chat state. Centered within the conversation area,
 * above the composer.
 */
@Composable
fun WelcomeView(
    isPrivate: Boolean,
    userName: String = "",
    modifier: Modifier = Modifier,
    dark: Boolean = true
) {
    val ref = rememberRefPalette(dark)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .then(if (isPrivate) Modifier.offset(y = (-40).dp) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isPrivate) {
            AvenMonogram(size = 128.dp)

            Spacer(modifier = Modifier.height(24.dp))

            // Drawn once per fresh-chat composition (and across rotation via
            // save/restore); never re-rolled on plain recomposition.
            val greeting = rememberSaveable(userName) {
                GreetingSelector.next(userName)
            }
            Text(
                text = greeting,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = LuckyRookieFamily
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        if (isPrivate) {
            Image(
                painter = painterResource(id = R.drawable.private_chat_icon),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                colorFilter = ColorFilter.tint(Color.White, blendMode = BlendMode.Multiply)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Private chat ...",
                fontSize = 17.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This conversation is not saved to recent chat history",
                fontSize = 17.5.sp,
                lineHeight = 24.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                color = ref.dimText,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 310.dp)
            )
        }
    }
}