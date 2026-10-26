package com.example.aiprgate.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun AiPrGateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    management: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val typography = if (management) {
        val locale = LocaleList("ko-KR")
        val koreanBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Normal,
            LineBreak.WordBreak.Phrase)
        Typography.copy(
            bodyLarge = Typography.bodyLarge.copy(fontSize = 17.sp, lineBreak = koreanBreak, localeList = locale),
            bodyMedium = Typography.bodyMedium.copy(fontSize = 15.sp, lineBreak = koreanBreak, localeList = locale),
            titleLarge = Typography.titleLarge.copy(lineBreak = koreanBreak, localeList = locale),
            titleMedium = Typography.titleMedium.copy(lineBreak = koreanBreak, localeList = locale),
            headlineSmall = Typography.headlineSmall.copy(lineBreak = koreanBreak, localeList = locale),
        )
    } else Typography

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
