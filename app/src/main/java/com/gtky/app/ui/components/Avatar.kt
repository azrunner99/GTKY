package com.gtky.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.data.entity.User
import com.gtky.app.util.PhotoStorage
import kotlin.math.abs

@Composable
fun Avatar(
    user: User,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(user.photoPath) {
        user.photoPath?.let { PhotoStorage.loadAvatar(it) }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        InitialsAvatar(name = user.name, size = size, modifier = modifier)
    }
}

@Composable
private fun InitialsAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val initials = remember(name) {
        val parts = name.trim().split(" ")
        val first = parts.getOrNull(0)?.firstOrNull()?.uppercase() ?: ""
        val last = parts.getOrNull(1)?.firstOrNull()?.uppercase() ?: ""
        "$first$last".ifEmpty { "?" }
    }
    val bg = remember(name) { colorForName(name) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun colorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF5B7CBA), Color(0xFFB85BA0), Color(0xFF5BAB6E),
        Color(0xFFC27A3E), Color(0xFF7D5BBA), Color(0xFF3EB3B5),
        Color(0xFFC24E4E), Color(0xFF6B8E23)
    )
    return palette[abs(name.hashCode()) % palette.size]
}
