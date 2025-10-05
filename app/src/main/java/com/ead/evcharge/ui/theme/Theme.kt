package com.ead.evcharge.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ead.evcharge.R

private val AppColorScheme = lightColorScheme(
    primary = Color(0xFF0D2141),       // navy
    secondary = Color(0xFF6E7989),     // slate
    background = Color(0xFFF8F9FA),    // light-grey
    onPrimary = Color.White,
    onBackground = Color(0xFF0D2141)
)

private val AppTypography = Typography(
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily(Font(R.font.montserrat_regular))
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily(Font(R.font.montserrat_regular))
    )
)

@Composable
fun EVChargeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
