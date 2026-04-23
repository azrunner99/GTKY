package com.gtky.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtky.app.GTKYApplication

val LocalAppLanguage = compositionLocalOf { "en" }

@Composable
fun t(en: String, es: String): String {
    val lang = LocalAppLanguage.current
    return if (lang == "es") es else en
}

@Composable
fun plural(count: Int, singularEn: String, pluralEn: String, singularEs: String, pluralEs: String): String {
    val lang = LocalAppLanguage.current
    return if (lang == "es") {
        if (count == 1) singularEs else pluralEs
    } else {
        if (count == 1) singularEn else pluralEn
    }
}

@Composable
fun LanguageToggle() {
    val app = LocalContext.current.applicationContext as GTKYApplication
    val language = LocalAppLanguage.current
    FilledTonalButton(
        onClick = { app.toggleLanguage() },
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Text(
            text = if (language == "en") "Español" else "English",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
