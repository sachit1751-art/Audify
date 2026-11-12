// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import android.net.Uri
import java.io.File
import com.music.audify.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.music.audify.data.settings.AppFontChoice
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.settings.AppBackgroundStyle
import com.music.audify.data.settings.ThemePacks
import com.music.audify.data.settings.ThemeSurfaceStyle

// Apple Music's signature red. No longer the primary accent, but kept for the
// spots (Replay's rank badge) that want that specific red regardless of theme.
val AccentRed = Color(0xFFFA2D48)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
)

private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE5E5EA),
)

private val AmoledColors = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF080808), onSurfaceVariant = Color(0xFFB0B0B5),
    outline = Color(0xFF252525),
)

private val MidnightColors = DarkColors.copy(
    background = Color(0xFF080D18),
    surface = Color(0xFF101827),
    surfaceVariant = Color(0xFF1B2940),
    outline = Color(0xFF30415D),
)

private val GraphiteColors = DarkColors.copy(
    background = Color(0xFF15171B),
    surface = Color(0xFF1D2026),
    surfaceVariant = Color(0xFF292D35),
    outline = Color(0xFF3B404A),
)

private val DeepPurpleColors = DarkColors.copy(
    background = Color(0xFF120D1D),
    surface = Color(0xFF1B1428),
    surfaceVariant = Color(0xFF2B1E3B),
    outline = Color(0xFF49365F),
)

private val WarmColors = DarkColors.copy(
    background = Color(0xFF19130F),
    surface = Color(0xFF241B15),
    surfaceVariant = Color(0xFF35271F),
    outline = Color(0xFF514035),
)

private val SlateColors = DarkColors.copy(
    background = Color(0xFF101820),
    surface = Color(0xFF18232D),
    surfaceVariant = Color(0xFF263541),
    outline = Color(0xFF3A4D5B),
)

private fun Color.withAccent(accent: Color, amount: Float): Color = Color(
    red = red * (1f - amount) + accent.red * amount,
    green = green * (1f - amount) + accent.green * amount,
    blue = blue * (1f - amount) + accent.blue * amount,
    alpha = 1f,
)

/** A theme-coloured icon accent with enough neutral mixing to stay legible. */
fun accessibleThemeAccent(primary: Color, onSurface: Color): Color = Color(
    red = primary.red * .55f + onSurface.red * .45f,
    green = primary.green * .55f + onSurface.green * .45f,
    blue = primary.blue * .55f + onSurface.blue * .45f,
    alpha = 1f,
)

/**
 * SF Pro Display, the face Apple Music itself is set in. Only the weights the
 * type scale actually asks for are bundled; Compose synthesises nothing, so a
 * missing weight would silently fall back to the nearest one shipped.
 */
val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_medium, FontWeight.W500),
    Font(R.font.sf_pro_display_semibold, FontWeight.W600),
    Font(R.font.sf_pro_display_bold, FontWeight.W700),
    Font(R.font.sf_pro_display_heavy, FontWeight.W800),
)

// Heavy, tight typography — the backbone of the Apple Music look.
private val AudifyTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp),
)

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

@Composable
fun AudifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    windowWidth: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val baseDensity = LocalDensity.current
    val fontChoice by AppSettings.fontChoice.collectAsStateWithLifecycle()
    val paletteId by AppSettings.themePaletteId.collectAsStateWithLifecycle()
    val customPacks by AppSettings.customThemePacks.collectAsStateWithLifecycle()
    val backgroundUri by AppSettings.themeBackgroundUri.collectAsStateWithLifecycle()
    val backgroundStyle by AppSettings.backgroundStyle.collectAsStateWithLifecycle()
    val palette = remember(paletteId, customPacks) { ThemePacks.resolve(paletteId, customPacks) }
    val primary = Color(android.graphics.Color.parseColor(palette.primaryHex))
    val secondary = Color(android.graphics.Color.parseColor(palette.secondaryHex))
    val tertiary = Color(android.graphics.Color.parseColor(palette.tertiaryHex))
    // Light mode must still express the selected palette. Previously every
    // palette fell back to the same pure-white LightColors and the background
    // gradients only mixed 10–22% accent into white, making several styles
    // effectively invisible. Keep reading surfaces white, but tint the app
    // canvas, variants and outlines with the active palette.
    val baseColors = if (!darkTheme) LightColors.copy(
        background = Color(0xFFFFFCFE).withAccent(tertiary, 0.055f),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF8F7FA).withAccent(primary, 0.075f),
        outline = Color(0xFFE4E1E8).withAccent(secondary, 0.16f),
        onSurfaceVariant = Color(0xFF55515C),
    ) else when (palette.surfaceStyle) {
        ThemeSurfaceStyle.DEFAULT -> DarkColors
        ThemeSurfaceStyle.AMOLED -> AmoledColors
        ThemeSurfaceStyle.MIDNIGHT -> MidnightColors
        ThemeSurfaceStyle.GRAPHITE -> GraphiteColors
        ThemeSurfaceStyle.DEEP_PURPLE -> DeepPurpleColors
        ThemeSurfaceStyle.WARM -> WarmColors
        ThemeSurfaceStyle.SLATE -> SlateColors
    }
    val themedColors = baseColors.copy(
        primary = primary,
        onPrimary = Color(android.graphics.Color.parseColor(ThemePacks.contentColorHex(palette.primaryHex))),
        secondary = secondary,
        onSecondary = Color(android.graphics.Color.parseColor(ThemePacks.contentColorHex(palette.secondaryHex))),
        tertiary = tertiary,
        onTertiary = Color(android.graphics.Color.parseColor(ThemePacks.contentColorHex(palette.tertiaryHex))),
        primaryContainer = if (darkTheme) primary.copy(alpha = .24f) else Color(0xFFF7F5FA).withAccent(primary, .22f),
        onPrimaryContainer = if (darkTheme) Color.White else Color.Black,
        secondaryContainer = if (darkTheme) secondary.copy(alpha = .20f) else Color(0xFFF7F5FA).withAccent(secondary, .22f),
        onSecondaryContainer = if (darkTheme) Color.White else Color.Black,
        surfaceTint = primary,
        inversePrimary = secondary,
    )
    val appBackgroundBrush = remember(backgroundStyle, themedColors.background, primary, secondary, tertiary) {
        val base = themedColors.background
        when (backgroundStyle) {
            AppBackgroundStyle.PLAIN -> Brush.verticalGradient(listOf(base, base))
            AppBackgroundStyle.AURORA -> Brush.verticalGradient(
                if (darkTheme) listOf(base.withAccent(primary, .30f), base.withAccent(secondary, .25f), base.withAccent(tertiary, .20f), base)
                else listOf(base.withAccent(primary, .43f), base.withAccent(secondary, .36f), base.withAccent(tertiary, .30f), base),
            )
            AppBackgroundStyle.OCEAN -> Brush.verticalGradient(
                if (darkTheme) listOf(base.withAccent(secondary, .32f), base.withAccent(primary, .27f), base.withAccent(tertiary, .20f), base)
                else listOf(base.withAccent(secondary, .48f), base.withAccent(primary, .40f), base.withAccent(tertiary, .31f), base),
            )
            AppBackgroundStyle.SUNSET -> Brush.verticalGradient(
                if (darkTheme) listOf(base.withAccent(tertiary, .30f), base.withAccent(primary, .26f), base.withAccent(secondary, .22f), base)
                else listOf(base.withAccent(tertiary, .46f), base.withAccent(primary, .39f), base.withAccent(secondary, .33f), base),
            )
            AppBackgroundStyle.NEBULA -> Brush.verticalGradient(
                if (darkTheme) listOf(base.withAccent(tertiary, .32f), base.withAccent(secondary, .26f), base.withAccent(primary, .21f), base)
                else listOf(base.withAccent(tertiary, .48f), base.withAccent(secondary, .40f), base.withAccent(primary, .34f), base),
            )
        }
    }
    val selectedFont = when (fontChoice) {
        AppFontChoice.AUDIFY -> SFProDisplay
        AppFontChoice.SYSTEM -> FontFamily.Default
        AppFontChoice.SERIF -> FontFamily.Serif
    }
    val adaptiveFontScale = when {
        windowWidth >= 840.dp -> 1.24f
        windowWidth >= 600.dp -> 1.18f
        windowWidth >= 480.dp -> 1.12f
        windowWidth >= 360.dp -> 1.06f
        else -> 1f
    }
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * adaptiveFontScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = themedColors,
            typography = AudifyTypography.withFamily(selectedFont),
        ) {
            Box(Modifier.fillMaxSize().background(appBackgroundBrush)) {
                if (backgroundUri.isNotBlank()) {
                    AsyncImage(
                        model = if (backgroundUri.startsWith("content://")) Uri.parse(backgroundUri) else File(backgroundUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            // In light mode the overlay must wash the photo
                            // toward white (not darken it), preserving dark
                            // foreground text over custom backgrounds.
                            (if (darkTheme) Color.Black else Color.White).copy(alpha = if (darkTheme) .44f else .70f),
                        ),
                    )
                }
                // The app intentionally draws translucent/gradient/photo
                // backgrounds behind most screens instead of wrapping every
                // destination in an opaque Material Surface. Without an
                // explicit root content colour, Material3's default
                // LocalContentColor can remain black, making Text/Icon calls
                // that rely on their inherited colour unreadable in dark mode.
                // Surface components still provide their own on-surface colour
                // and override this value locally where appropriate.
                CompositionLocalProvider(
                    LocalContentColor provides themedColors.onBackground,
                ) {
                    content()
                }
            }
        }
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

