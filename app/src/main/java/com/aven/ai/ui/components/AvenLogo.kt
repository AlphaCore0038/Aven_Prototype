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
 * The square Aven monogram ("A" mark, logo_monogram_a.webp). Used for the
 * welcome state and the about screen.
 */
@Composable
fun AvenMonogram(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.logo_monogram_a),
            contentDescription = "Aven",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}