package com.music.audify.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.music.audify.ui.components.continuous
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.music.audify.R
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import com.music.audify.data.settings.AccentColor
import com.music.audify.data.settings.AppFont

// Apple Music's signature red. No longer the primary accent, but kept for the
// spots (Replay's rank badge) that want that specific red regardless of theme.
val AccentRed = Color(0xFFFA2D48)

// Apple Music's accent: tab bar, active glyphs, sliders, links, selected states.
val AppleRed = Color(0xFFFA243C)

private val DarkColors = darkColorScheme(
    primary = AppleRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1217),
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = Color(0xFF98989F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF0A84FF),
    onTertiary = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF111113),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF2C2C2E),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A),
    onError = Color.White,
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color.Black,
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = AppleRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E6),
    onPrimaryContainer = Color(0xFF5C0A14),
    secondary = Color(0xFF6E6E73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF007AFF),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F9FB),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFD1D1D6),
    outline = Color(0xFFE5E5EA),
    outlineVariant = Color(0xFFC6C6C8),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color.White,
    scrim = Color.Black,
)

/**
 * Corner radii for everything Material draws on its own (dialogs, sheets, menus,
 * text fields, chips), cut as Apple's continuous squircle rather than a circular
 * arc, so these surfaces match the glass bars and cards already drawn that way.
 */
private val AppleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp).continuous(),
    small = RoundedCornerShape(12.dp).continuous(),
    medium = RoundedCornerShape(16.dp).continuous(),
    large = RoundedCornerShape(20.dp).continuous(),
    extraLarge = RoundedCornerShape(28.dp).continuous(),
)

/**
 * This scheme with the user's accent in the places the accent lives: the primary
 * and its container. Everything else keeps Apple's greys. For [AccentColor.RED]
 * the result is the scheme as it always was.
 */
private fun ColorScheme.withAccent(accent: AccentColor, dark: Boolean): ColorScheme {
    val tint = Color(if (dark) accent.dark else accent.light)
    return copy(
        primary = tint,
        onPrimary = Color(accent.onAccent),
        primaryContainer = if (dark) lerp(Color.Black, tint, 0.28f) else lerp(Color.White, tint, 0.16f),
        onPrimaryContainer = if (dark) lerp(tint, Color.White, 0.8f) else lerp(tint, Color.Black, 0.65f),
    )
}

/**
 * SF Pro Display, the face Apple Music itself is set in, drawn one weight
 * lighter than it is asked for.
 *
 * Measured against Apple Music's own screens, every weight here came out a
 * cut heavier than the same text there: its titles are Semibold where these
 * asked for Bold, its metadata Medium where these asked for Semibold. The
 * shift is made once, in the family, so it reaches the type scale and every
 * weight set in place alike and keeps the steps between them: Heavy draws
 * Bold, Bold draws Semibold, Semibold draws Medium, Medium draws Regular.
 * Regular stays Regular — no lighter cut is bundled, and Apple's body text is
 * Regular too. A FontWeight in this app therefore names the step it sits at in
 * the scale, not the cut it draws.
 *
 * Compose synthesises nothing, so a weight outside these falls back to the
 * nearest one listed.
 */
val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_regular, FontWeight.W500),
    Font(R.font.sf_pro_display_medium, FontWeight.W600),
    Font(R.font.sf_pro_display_semibold, FontWeight.W700),
    Font(R.font.sf_pro_display_bold, FontWeight.W800),
)

/*
 * Letter spacing measured off Apple Music's own screens: the spacing the
 * bundled Display cut needs, at the weight it draws, to set a line to the
 * width Apple sets it. Apple sets text under 20pt in SF Pro Text, which is
 * spaced looser than Display, so the small sizes open up — about +0.25 to
 * +0.4 at 12 to 14 — and the spacing closes as the size grows, to just under
 * nothing for semibold titles at 18 to 24. The large titles had nothing to
 * measure against and follow the same trend.
 */
private val AudifyTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.25).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = 0.05.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp, letterSpacing = 0.25.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp, letterSpacing = 0.3.sp),
).withFamily(SFProDisplay)

/**
 * SF Pro Display as the app first shipped it, before [SFProDisplay] moved every
 * weight a cut lighter: each weight draws its own cut, Heavy included. Kept for
 * [AppFont.LEGACY].
 */
val SFProDisplayLegacy = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_medium, FontWeight.W500),
    Font(R.font.sf_pro_display_semibold, FontWeight.W600),
    Font(R.font.sf_pro_display_bold, FontWeight.W700),
    Font(R.font.sf_pro_display_heavy, FontWeight.W800),
)

// Heavy, tight typography — the original scale [AppFont.LEGACY] restores.
private val LegacyTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp),
).withFamily(SFProDisplayLegacy)

/** Applies [family] to every style in the scale, so nothing is left on Roboto. */
private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    font: AppFont = AppFont.DEFAULT,
    accent: AccentColor = AccentColor.RED,
    content: @Composable () -> Unit,
) {
    val scheme = remember(darkTheme, accent) {
        (if (darkTheme) DarkColors else LightColors).withAccent(accent, darkTheme)
    }
    val selection = remember(scheme.primary) {
        TextSelectionColors(
            handleColor = scheme.primary,
            backgroundColor = scheme.primary.copy(alpha = 0.3f),
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = when (font) {
            AppFont.DEFAULT -> AudifyTypography
            AppFont.LEGACY -> LegacyTypography
        },
        shapes = AppleShapes,
    ) {
        // iOS draws no Material ripple. Null switches off the one the remaining
        // Material buttons and menu rows still carried; the app's own controls
        // already pass indication = null and keep their own press behaviour.
        CompositionLocalProvider(
            LocalRippleConfiguration provides null,
            LocalTextSelectionColors provides selection,
            content = content,
        )
    }
}

/**
 * Draws the status and navigation bar glyphs dark or light.
 *
 * `enableEdgeToEdge()` decides this from the *system* dark-mode setting, which
 * is the wrong input the moment the in-app theme disagrees with it: Light theme
 * on a phone in dark mode left white icons on a white bar, invisible. The bars
 * have to follow the content the app is actually painting. The player supplies
 * its own stable light-icon value and paints contrast behind it; every other
 * surface follows the theme. Hence a parameter rather than reading it here.
 */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}

