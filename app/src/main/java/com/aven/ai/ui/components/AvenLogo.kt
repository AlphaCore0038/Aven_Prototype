package com.aven.ai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aven.ai.R

/**
 * The horizontal Aven wordmark/logo (Full_logo_Aven.png).
 * Used in the top bar, drawer header, splash, and about screens.
 */
@Composable
fun AvenFullLogo(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp
) {
    Image(
        painter = painterResource(R.drawable.logo_full),
        contentDescription = "Aven",
        modifier = modifier.size(width = Dp.Unspecified, height = height),
        contentScale = ContentScale.Fit
    )
}

/**
 * The square Aven monogram (logo mark). Used for the welcome state and the
 * model loading state.
 */
@Composable
fun AvenMonogram(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.logo_monogram),
            contentDescription = "Aven",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}