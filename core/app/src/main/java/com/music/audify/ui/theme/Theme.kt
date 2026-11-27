package com.music.audify.ui.theme

import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable
import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.music.audify.R
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.music.audify.data.settings.AppFont
import com.music.audify.data.settings.AppBackgroundStyle
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
private fun String.toThemeColor(): Color = Color(this.toColorInt())

private fun Color.withAccent(accent: Color, amount: Float): Color = Color(
    red = red * (1f - amount) + accent.red * amount,
    green = green * (1f - amount) + accent.green * amount,
    blue = blue * (1f - amount) + accent.blue * amount,
    alpha = 1f,
)



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
    val amoledDisplayColors by AppSettings.amoledDisplayColors.collectAsStateWithLifecycle()
    val activeThemePack = remember(themePack, selectedThemePackId, customThemePacks) {
        themePack ?: ThemePacks.resolve(selectedThemePackId, customThemePacks)
    }
    val scheme = remember(darkTheme, activeThemePack, amoledDisplayColors) {
        val primaryHex = if (activeThemePack.id == ThemePacks.CLASSIC_ID) activeThemePack.tertiaryHex else activeThemePack.primaryHex
        val tertiaryHex = if (activeThemePack.id == ThemePacks.CLASSIC_ID) activeThemePack.primaryHex else activeThemePack.tertiaryHex
        // Bright palette accents can disappear on the light canvas. Preserve
        // the exact swatches in the theme picker, but darken only rendered
        // light-mode accents enough to remain visible on white surfaces.
        val renderedPrimaryHex = if (darkTheme) primaryHex else ThemePacks.accessibleLightAccentHex(primaryHex)
        val renderedSecondaryHex = if (darkTheme) activeThemePack.secondaryHex else ThemePacks.accessibleLightAccentHex(activeThemePack.secondaryHex)
        val renderedTertiaryHex = if (darkTheme) tertiaryHex else ThemePacks.accessibleLightAccentHex(tertiaryHex)
        val primary = renderedPrimaryHex.toThemeColor()
        val secondary = renderedSecondaryHex.toThemeColor()
        val tertiary = renderedTertiaryHex.toThemeColor()
        // Restore the pre-1.9.3 separation: the palette colours controls and
        // component accents; it does not recolour the entire app canvas.
        // Keep the original neutral light surfaces and the theme pack's
        // deliberately chosen dark surface family (Midnight/etc.).
        val baseColors = if (!darkTheme) {
            LightColors.copy(
                background = ThemePacks.lightBackgroundHex(activeThemePack).toThemeColor(),
                surface = Color(0xFFF1EEF5).withAccent(primary, 0.10f),
                surfaceVariant = Color(0xFFE9E8F0).withAccent(primary, 0.12f),
                outline = Color(0xFFD2CFDA).withAccent(secondary, 0.14f),
                onSurfaceVariant = Color(0xFF55515C),
            )
        } else {
            if (amoledDisplayColors) {
                DarkColors.copy(
                    background = Color.Black,
                    onBackground = Color.White,
                    surface = Color.Black,
                    onSurface = Color.White,
                    surfaceVariant = Color.Black,
                    onSurfaceVariant = Color(0xFFBDBDBD),
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color.Black,
                    surfaceContainer = Color.Black,
                    surfaceContainerHigh = Color.Black,
                    surfaceContainerHighest = Color.Black,
                    outline = Color(0xFF202020),
                    outlineVariant = Color(0xFF181818),
                )
            } else when (activeThemePack.surfaceStyle) {
                ThemeSurfaceStyle.DEFAULT -> DarkColors.copy(
                    background = ThemePacks.darkBackgroundHex(activeThemePack).toThemeColor(),
                )
                ThemeSurfaceStyle.MIDNIGHT -> DarkColors.copy(
                    background = Color(0xFF080D18),
                    surface = Color(0xFF101827),
                    surfaceVariant = Color(0xFF1B2940),
                    outline = Color(0xFF30415D),
                )
                ThemeSurfaceStyle.GRAPHITE -> DarkColors.copy(
                    background = Color(0xFF15171B),
                    surface = Color(0xFF1D2026),
                    surfaceVariant = Color(0xFF292D35),
                    outline = Color(0xFF3B404A),
                )
                ThemeSurfaceStyle.DEEP_PURPLE -> DarkColors.copy(
                    background = Color(0xFF120D1D),
                    surface = Color(0xFF1B1428),
                    surfaceVariant = Color(0xFF2B1E3B),
                    outline = Color(0xFF49365F),
                )
                ThemeSurfaceStyle.WARM -> DarkColors.copy(
                    background = Color(0xFF19130F),
                    surface = Color(0xFF241B15),
                    surfaceVariant = Color(0xFF35271F),
                    outline = Color(0xFF514035),
                )
                ThemeSurfaceStyle.SLATE -> DarkColors.copy(
                    background = Color(0xFF101820),
                    surface = Color(0xFF18232D),
                    surfaceVariant = Color(0xFF263541),
                    outline = Color(0xFF3A4D5B),
                )
            }
        }
        val primaryContainer = if (darkTheme) primary.copy(alpha = 0.24f)
            else Color(0xFFF7F5FA).withAccent(primary, 0.22f)
        val secondaryContainer = if (darkTheme) secondary.copy(alpha = 0.20f)
            else Color(0xFFF7F5FA).withAccent(secondary, 0.22f)
        // A palette must remain perceptible on the canvas and on the surfaces
        // users spend time looking at. Keep Classic exactly neutral, but give
        // every other pack a restrained accent wash across Material surfaces
        // in both modes; this avoids a coloured selector sitting on an entirely
        // unrelated black/white page.
        val themedSurfaces = if (amoledDisplayColors || activeThemePack.id == ThemePacks.CLASSIC_ID) {
            baseColors
        } else {
            val lift = if (darkTheme) 0.045f else 0.075f
            baseColors.copy(
                surface = baseColors.surface.withAccent(primary, lift),
                surfaceVariant = baseColors.surfaceVariant.withAccent(primary, lift * 1.7f),
                surfaceContainerLowest = baseColors.surfaceContainerLowest.withAccent(primary, lift * 0.35f),
                surfaceContainerLow = baseColors.surfaceContainerLow.withAccent(primary, lift * 0.65f),
                surfaceContainer = baseColors.surfaceContainer.withAccent(primary, lift),
                surfaceContainerHigh = baseColors.surfaceContainerHigh.withAccent(primary, lift * 1.35f),
                surfaceContainerHighest = baseColors.surfaceContainerHighest.withAccent(primary, lift * 1.7f),
                outline = baseColors.outline.withAccent(secondary, lift * 1.5f),
                outlineVariant = baseColors.outlineVariant.withAccent(secondary, lift * 1.8f),
            )
        }
        themedSurfaces.copy(
            primary = primary,
            onPrimary = ThemePacks.contentColorHex(renderedPrimaryHex).toThemeColor(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = if (primaryContainer.luminance() > 0.5f) Color.Black else Color.White,
            secondary = secondary,
            onSecondary = ThemePacks.contentColorHex(renderedSecondaryHex).toThemeColor(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = if (secondaryContainer.luminance() > 0.5f) Color.Black else Color.White,
            tertiary = tertiary,
            onTertiary = ThemePacks.contentColorHex(renderedTertiaryHex).toThemeColor(),
            surfaceTint = if (amoledDisplayColors && darkTheme) Color.Black else primary,
            inversePrimary = secondary,
        )
    }
    val selection = remember(scheme.primary) {
        TextSelectionColors(
            handleColor = scheme.primary,
            backgroundColor = scheme.primary.copy(alpha = 0.3f),
        )
    }
    val backgroundStyle by AppSettings.backgroundStyle.collectAsStateWithLifecycle()
    // Match the original 1.9.3 built-in wallpaper treatment: restrained,
    // theme-derived vertical gradients without the later radial bloom overlays.
    val appBackgroundBrush = remember(
        backgroundStyle, scheme.background, scheme.primary, scheme.secondary, scheme.tertiary, darkTheme, amoledDisplayColors,
    ) {
        val base = scheme.background
        when (backgroundStyle) {
            AppBackgroundStyle.PLAIN -> Brush.verticalGradient(listOf(base, base))
            AppBackgroundStyle.OCEAN -> Brush.verticalGradient(
                if (darkTheme) listOf(base.withAccent(scheme.secondary, .32f), base.withAccent(scheme.primary, .27f), base.withAccent(scheme.tertiary, .20f), base)
                else listOf(base.withAccent(scheme.secondary, .48f), base.withAccent(scheme.primary, .40f), base.withAccent(scheme.tertiary, .31f), base),
            )
        }
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
            // Keep the selected wallpaper as an ordinary screen background.
            // The 1.9.3 glass pipeline records page content separately; capturing
            // this wallpaper into a second GraphicsLayer caused it to be
            // composited over the live wallpaper when Liquid Glass was enabled.
            Box(
                Modifier.fillMaxSize().drawBehind { drawRect(appBackgroundBrush) },
            ) {
                if (!backgroundUri.isNullOrBlank()) {
                    AsyncImage(
                        model = backgroundUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = 1f,
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            (if (darkTheme) Color.Black else Color.White).copy(
                                alpha = if (darkTheme) .44f else .70f,
                            ),
                        ),
                    )
                }
                CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
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
        // Keep the gesture-navigation region transparent. The app's edge-to-edge
        // Compose backdrop already paints the active theme/background there; an
        // opaque decor drawable creates a second, flat strip below the glass bar
        // and hides wallpapers/gradients. The theme XML background is only a
        // pre-content launch fallback, not the steady-state navigation surface.
        window.setBackgroundDrawable(
            android.graphics.Color.TRANSPARENT.toDrawable(),
        )
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}

