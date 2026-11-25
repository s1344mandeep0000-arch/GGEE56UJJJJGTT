package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = AndroidEmerald,
    onPrimary = AndroidEmeraldDark,
    primaryContainer = AndroidEmeraldContainer,
    onPrimaryContainer = AndroidEmeraldOnContainer,
    secondary = CompilerBlue,
    onSecondary = Color(0xFF061E3E),
    secondaryContainer = CompilerBlueContainer,
    onSecondaryContainer = CompilerBlueOnContainer,
    tertiary = GradleAmber,
    onTertiary = Color(0xFF271900),
    tertiaryContainer = GradleAmberContainer,
    onTertiaryContainer = GradleAmberOnContainer,
    error = ErrorCoral,
    onError = Color(0xFF3A090B),
    errorContainer = ErrorCoralContainer,
    onErrorContainer = ErrorCoralOnContainer,
    background = ObsidianCanvas,
    onBackground = TextPrimaryDark,
    surface = StudioSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceHigh,
    surfaceContainerHigh = StudioSurfaceHighest,
    surfaceContainerHighest = Color(0xFF283347),
    outline = StudioBorder,
    outlineVariant = StudioBorderSubtle
)

private val StudioLightColorScheme = lightColorScheme(
    primary = AndroidEmeraldLight,
    onPrimary = Color.White,
    primaryContainer = AndroidEmeraldLightContainer,
    onPrimaryContainer = Color(0xFF022C22),
    secondary = CompilerBlueLight,
    onSecondary = Color.White,
    secondaryContainer = CompilerBlueLightContainer,
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = LightCanvas,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceHigh,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0)
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ApkForgeTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> StudioDarkColorScheme
        else -> StudioLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
