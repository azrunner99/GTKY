package com.gtky.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

@Composable
fun IdleTimeout(
    timeoutMs: () -> Long = { 90_000L },
    onIdle: () -> Unit,
    content: @Composable () -> Unit
) {
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val currentTimeout by rememberUpdatedState(timeoutMs)

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            if (System.currentTimeMillis() - lastInteraction >= currentTimeout()) {
                lastInteraction = System.currentTimeMillis()
                onIdle()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(pass = PointerEventPass.Initial)
                        lastInteraction = System.currentTimeMillis()
                    }
                }
            }
    ) {
        content()
    }
}
