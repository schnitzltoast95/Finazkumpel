package de.quartalsmappe.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.quartalsmappe.app.model.AppPalette

internal val AppCardBackground = Color(0xFFFFFEFB)
internal val AppCardBorder = Color(0xFFDADDD1)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D4A2B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8CF),
    onPrimaryContainer = Color(0xFF182815),
    secondary = Color(0xFF58624B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6D7),
    onSecondaryContainer = Color(0xFF2A3023),
    tertiary = Color(0xFFA84F3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD1),
    onTertiaryContainer = Color(0xFF3D0802),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF1B2118),
    surface = AppCardBackground,
    surfaceContainerLow = Color(0xFFF0F1E9),
    surfaceContainer = Color(0xFFE9EDE1),
    surfaceContainerHigh = Color(0xFFE2E8D9),
    onSurface = Color(0xFF1B2118),
    surfaceVariant = Color(0xFFE5E7DC),
    onSurfaceVariant = Color(0xFF454B40),
    outline = Color(0xFF74796C),
    outlineVariant = AppCardBorder,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5CFA5),
    onPrimary = Color(0xFF20351E),
    primaryContainer = Color(0xFF3B5738),
    onPrimaryContainer = Color(0xFFD1EBC5),
    secondary = Color(0xFFC2C9B5),
    onSecondary = Color(0xFF2D3328),
    secondaryContainer = Color(0xFF434A3C),
    onSecondaryContainer = Color(0xFFDEE5D1),
    tertiary = Color(0xFFFFB4A5),
    onTertiary = Color(0xFF641B10),
    tertiaryContainer = Color(0xFF843225),
    onTertiaryContainer = Color(0xFFFFDAD1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF161C17),
    onBackground = Color(0xFFE1E4DB),
    surface = Color(0xFF1D251E),
    surfaceContainerLow = Color(0xFF212A22),
    surfaceContainer = Color(0xFF283229),
    surfaceContainerHigh = Color(0xFF303C2F),
    onSurface = Color(0xFFE1E4DB),
    surfaceVariant = Color(0xFF42483E),
    onSurfaceVariant = Color(0xFFC3C8BC),
    outline = Color(0xFF8D9286),
    outlineVariant = Color(0xFF42483E),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

fun appColorScheme(palette: AppPalette, darkTheme: Boolean): ColorScheme = when (palette) {
    AppPalette.FOREST -> if (darkTheme) DarkColors else LightColors
    else -> if (darkTheme) AdditionalDarkColors.getValue(palette) else AdditionalLightColors.getValue(palette)
}

val LocalAppDarkTheme = staticCompositionLocalOf { false }

@Composable
fun QuartalsmappeTheme(
    palette: AppPalette = AppPalette.FOREST,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = appColorScheme(palette, darkTheme),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

