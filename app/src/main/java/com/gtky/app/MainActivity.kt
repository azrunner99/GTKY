package com.gtky.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gtky.app.ui.IdleTimeout
import com.gtky.app.ui.LocalAppLanguage
import com.gtky.app.ui.navigation.GTKYNavGraph
import com.gtky.app.ui.navigation.Routes
import com.gtky.app.ui.theme.GTKYTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            GTKYTheme {
                val app = application as GTKYApplication
                val language by app.language.collectAsState()
                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentEntry?.destination?.route
                val idleTimeoutMs: () -> Long = {
                    when (currentRoute) {
                        Routes.SURVEY, Routes.QUIZ -> 180_000L
                        Routes.ADMIN -> 300_000L
                        else -> 90_000L
                    }
                }
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    IdleTimeout(
                        timeoutMs = idleTimeoutMs,
                        onIdle = {
                            app.handleIdleTimeout {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            }
                        }
                    ) {
                        GTKYNavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
