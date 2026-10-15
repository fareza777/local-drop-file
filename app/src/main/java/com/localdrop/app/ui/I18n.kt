package com.localdrop.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/** "System" follows the phone's language; "English" and "Indonesia" force one. */
val LocalAppLanguage = compositionLocalOf { "System" }
val AppLanguages = listOf("English", "Indonesia", "System")

@Composable
@ReadOnlyComposable
fun isIndonesian(): Boolean = when (LocalAppLanguage.current) {
    "Indonesia" -> true
    "English" -> false
    else -> LocalConfiguration.current.locales[0].language.let { it == "in" || it == "id" }
}

/** Picks the English or Indonesian text for the active app language. */
@Composable
@ReadOnlyComposable
fun tr(en: String, id: String): String = if (isIndonesian()) id else en
