package com.music.audify.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.music.audify.ui.components.continuous
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.music.audify.R
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.music.audify.data.settings.AppFont
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.settings.ThemePack
import com.music.audify.data.settings.ThemePacks
import com.music.audify.data.settings.ThemeSurfaceStyle

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFA243C),
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
    primary = Color(0xFFFA243C),
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
 * Every interactive role is derived from the selected theme pack.
 */
private fun String.toThemeColor(): Color = Color(android.graphics.Color.parseColor(this))



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
    themePack: ThemePack? = null,
    backgroundUri: String? = null,
    content: @Composable () -> Unit,
) {
    val selectedThemePackId by AppSettings.selectedThemePackId.collectAsStateWithLifecycle()
    val customThemePacks by AppSettings.customThemePacks.collectAsStateWithLifecycle()
    val activeThemePack = remember(themePack, selectedThemePackId, customThemePacks) {
        themePack ?: ThemePacks.resolve(selectedThemePackId, customThemePacks)
    }
    val scheme = remember(darkTheme, activeThemePack) {
        val base = if (darkTheme) DarkColors else LightColors
        val primaryHex = if (activeThemePack.id == ThemePacks.CLASSIC_ID) activeThemePack.tertiaryHex else activeThemePack.primaryHex
        val tertiaryHex = if (activeThemePack.id == ThemePacks.CLASSIC_ID) activeThemePack.primaryHex else activeThemePack.tertiaryHex
        val primary = primaryHex.toThemeColor()
        val secondary = activeThemePack.secondaryHex.toThemeColor()
        val tertiary = tertiaryHex.toThemeColor()
        val surface = when (activeThemePack.surfaceStyle) {
            ThemeSurfaceStyle.AMOLED -> Color.Black
            ThemeSurfaceStyle.MIDNIGHT -> Color(0xFF080F20)
            ThemeSurfaceStyle.GRAPHITE -> Color(0xFF17191D)
            ThemeSurfaceStyle.DEEP_PURPLE -> Color(0xFF170F24)
            ThemeSurfaceStyle.WARM -> Color(0xFF211711)
            ThemeSurfaceStyle.SLATE -> Color(0xFF111B25)
            ThemeSurfaceStyle.DEFAULT -> base.background
        }
        val containerSurface = if (darkTheme) surface else base.surface
        val primaryContainer = lerp(containerSurface, primary, if (darkTheme) 0.30f else 0.14f)
        val secondaryContainer = lerp(containerSurface, secondary, if (darkTheme) 0.24f else 0.14f)
        val tertiaryContainer = lerp(containerSurface, tertiary, if (darkTheme) 0.24f else 0.14f)
        base.copy(
            primary = primary,
            onPrimary = ThemePacks.contentColorHex(primaryHex).toThemeColor(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = if (primaryContainer.luminance() > 0.5f) Color.Black else Color.White,
            secondary = secondary,
            onSecondary = ThemePacks.contentColorHex(activeThemePack.secondaryHex).toThemeColor(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = if (secondaryContainer.luminance() > 0.5f) Color.Black else Color.White,
            tertiary = tertiary,
            onTertiary = ThemePacks.contentColorHex(tertiaryHex).toThemeColor(),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = if (tertiaryContainer.luminance() > 0.5f) Color.Black else Color.White,
            background = if (darkTheme) surface else base.background,
            surface = if (darkTheme) surface else base.surface,
            surfaceContainerLowest = if (darkTheme) surface else base.surfaceContainerLowest,
            surfaceContainerLow = if (darkTheme) lerp(surface, Color.White, 0.035f) else base.surfaceContainerLow,
            surfaceContainer = if (darkTheme) lerp(surface, Color.White, 0.065f) else base.surfaceContainer,
            surfaceContainerHigh = if (darkTheme) lerp(surface, Color.White, 0.10f) else base.surfaceContainerHigh,
            surfaceContainerHighest = if (darkTheme) lerp(surface, Color.White, 0.15f) else base.surfaceContainerHighest,
            outline = lerp(base.outline, primary, if (darkTheme) 0.20f else 0.12f),
            outlineVariant = lerp(base.outlineVariant, primary, if (darkTheme) 0.14f else 0.08f),
            onSurfaceVariant = lerp(base.onSurfaceVariant, primary, 0.08f),
        )
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
            AppFont.SYSTEM -> Typography()
            AppFont.SERIF -> Typography().withFamily(FontFamily.Serif)
            AppFont.LEGACY -> LegacyTypography
        },
        shapes = AppleShapes,
    ) {
        // iOS draws no Material ripple. Null switches off the one the remaining
        // Material buttons and menu rows still carried; the app's own controls
        // already pass indication = null and keep their own press behaviour.
        // MaterialTheme supplies a ColorScheme, but it does not set the default
        // text/content colour for arbitrary screen roots. Screens such as
        // Podcasts use LazyColumn directly (rather than a Surface), so Text
        // without an explicit colour otherwise falls back to black in dark
        // mode. Establish the correct foreground once at the app theme root;
        // Material surfaces, buttons and menus still provide their own local
        // content colours as usual.
        CompositionLocalProvider(
            LocalRippleConfiguration provides null,
            LocalTextSelectionColors provides selection,
            LocalContentColor provides scheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (!backgroundUri.isNullOrBlank()) {
                    AsyncImage(
                        model = backgroundUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = 0.22f,
                    )
                }
                content()
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

