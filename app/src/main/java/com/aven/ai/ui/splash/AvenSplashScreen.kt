package com.aven.ai.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aven.ai.R
import com.aven.ai.ui.chat.LuckyRookieFamily
import com.aven.ai.ui.components.AvenMonogram

/**
 * Branded launch layer drawn over the app's first content: monogram and
 * wordmark as one centered group, developer attribution pinned near the
 * bottom. The host shows it briefly, then fades it out.
 */
@Composable
fun AvenSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            AvenMonogram(size = 140.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Aven",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = LuckyRookieFamily,
                    fontSize = 56.sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Developed by",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9E9E9E)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AlphaCore0038",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
