// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import com.music.audify.R
import com.music.audify.data.settings.AppSettings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.sharedui.resources.Res
import com.music.audify.sharedui.resources.ic_logo
import com.music.audify.sharedui.resources.spotify_logo
import org.jetbrains.compose.resources.painterResource
import com.music.audify.ui.icons.AudifyIcons
import com.music.audify.ui.player.MeshGradientBackground
import com.music.audify.ui.player.MESH_CYCLE_MILLIS
import com.music.audify.ui.player.MeshPalette
import com.music.audify.ui.player.WelcomeMeshPalettes
import com.music.audify.ui.player.cyclingMeshPalette

/**
 * Welcome, profile name, optional Google sign-in, and a short music setup guide.
 *
 * Shown once — to new installs and, because the flag it sets is new, to anyone
 * updating from a build without it. The second page is skipped for an account
 * that is already signed in; asking somebody to connect what is connected
 * reads as the app having forgotten them.
 *
 * A single dark listening lane connects the four steps. Sign-in remains
 * optional, and the user can leave setup from any step.
 */
@Composable
fun OnboardingScreen(
    signedIn: Boolean,
    spotifyConnected: Boolean,
    initialName: String,
    /** Saves the party name. Called on Next, before the second page. */
    onNameChosen: (String) -> Unit,
    /** Opens the Google sign-in; a successful return advances to the music setup page. */
    onSignIn: () -> Unit,
    onSpotifyConnect: () -> Unit,
    onListenTogether: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_WELCOME) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val appReduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val systemAnimationsOff = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val reduceMotion = appReduceMotion || systemAnimationsOff
    BackHandler(enabled = previousOnboardingPage(page) != null) {
        previousOnboardingPage(page)?.let { page = it }
    }
    // Keep the original animated, multi-colour onboarding mesh. The welcome
    // and setup pages use AUDIFY's warm palette; account connection leans into
    // the original red/pink/violet palette.
    val palette = cyclingMeshPalette(if (page == PAGE_DISCOVERY) ConnectPalettes else WelcomeMeshPalettes)

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF090B12))
            // Swallows every touch the pages don't, so nothing reaches the app
            // laid out underneath — and a tap on the backdrop puts the keyboard
            // away, which is where a thumb goes to dismiss one.
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = page,
            continuous = !reduceMotion,
            animated = !reduceMotion,
            driftMillis = 6_000,
            colorFadeMillis = MESH_CYCLE_MILLIS,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.05f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f),
                    ),
                ),
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val forward = targetState > initialState
                    val slide = tween<IntOffset>(420)
                    (slideInHorizontally(slide) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320, 100)))
                        .togetherWith(
                            slideOutHorizontally(slide) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)),
                        )
                }
            },
            label = "onboardingPage",
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) { shown ->
            OnboardingPage(
                page = shown,
                onSkip = onFinish,
            ) {
                when (shown) {
                    PAGE_WELCOME -> WelcomePage(onNext = { page = PAGE_DISCOVERY })
                    PAGE_DISCOVERY -> DiscoveryPage(
                        youtubeConnected = signedIn,
                        spotifyConnected = spotifyConnected,
                        onBack = { page = PAGE_WELCOME },
                        onYouTubeConnect = onSignIn,
                        onSpotifyConnect = onSpotifyConnect,
                        onListenTogether = onListenTogether,
                        onContinue = { page = PAGE_NAME },
                    )
                    PAGE_NAME -> NamePage(
                        name = name,
                        onNameChange = { name = it.take(NAME_LIMIT) },
                        onBack = { page = PAGE_DISCOVERY },
                        onNext = next@{
                            val chosen = name.trim()
                            focusManager.clearFocus()
                            if (chosen.isNotEmpty()) onNameChosen(chosen)
                            page = PAGE_MUSIC
                        },
                        onSkip = { page = PAGE_MUSIC },
                    )
                    else -> MusicSetupPage(onBack = { page = PAGE_NAME }, onFinish = onFinish)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MusicSetupPage(onBack: () -> Unit, onFinish: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    BackButton(onBack)
    Spacer(Modifier.height(32.dp))
    Title(stringResource(R.string.onboarding_music_heading))
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.onboarding_music_description),
        style = MaterialTheme.typography.bodyLarge,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        color = Color.White.copy(alpha = 0.78f),
    )
    Spacer(Modifier.height(40.dp))
    PillButton(label = stringResource(R.string.onboarding_explore_music), onClick = onFinish, primary = true)
    Spacer(Modifier.height(24.dp))
}

/** First-run introduction: a concise product overview before asking for details. */
@Composable
private fun ColumnScope.WelcomePage(onNext: () -> Unit) {
    Spacer(Modifier.height(30.dp))
    Text(
        text = stringResource(R.string.onboarding_welcome_heading),
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 52.sp,
            lineHeight = 52.sp,
            letterSpacing = (-1.8).sp,
        ),
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.onboarding_welcome_description),
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.76f),
        lineHeight = 25.sp,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(52.dp))
    PillButton(label = stringResource(R.string.onboarding_get_started), onClick = onNext, primary = true)
    Spacer(Modifier.height(24.dp))
}

/** The column every page is laid out in, capped so a tablet doesn't stretch it. */
@Composable
private fun OnboardingPage(page: Int, onSkip: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            if (page != PAGE_DISCOVERY) OnboardingHeader(page = page, onSkip = onSkip)
            content()
        }
    }
}

@Composable
private fun OnboardingHeader(page: Int, onSkip: () -> Unit) {
    val progressDescription = stringResource(R.string.onboarding_step_progress, page + 1)
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Image(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(width = 27.dp, height = 23.dp),
            )
            Text("AUDIFY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp, color = Color(0xFFF2F3F7))
        }
        Box(
            Modifier
                .height(48.dp)
                .widthIn(min = 64.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.onboarding_skip), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.82f))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .semantics {
                contentDescription = progressDescription
                progressBarRangeInfo = ProgressBarRangeInfo(page + 1f, 1f..4f)
            },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(if (index <= page) Color(0xFFE6C17A) else Color(0xFF343847)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.NamePage(
    name: String,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    // With the keyboard up there is room for the title or the logo, not both;
    // the title is the one that says where you are.
    val keyboardUp = WindowInsets.isImeVisible
    AnimatedVisibility(
        visible = !keyboardUp,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            Spacer(Modifier.height(28.dp))
            Image(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(width = 46.dp, height = 30.dp),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    BackButton(onBack)
    Spacer(Modifier.height(20.dp))
    Title(stringResource(R.string.onboarding_name_heading))

    Spacer(Modifier.height(36.dp))

    Text(
        text = stringResource(R.string.onboarding_name_prompt),
        style = MaterialTheme.typography.titleMedium,
        fontSize = 17.sp,
        color = Color.White.copy(alpha = 0.78f),
        modifier = Modifier.padding(start = 4.dp),
    )
    Spacer(Modifier.height(12.dp))
    NameField(value = name, onValueChange = onNameChange, onDone = onNext)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_name_footer),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Spacer(Modifier.height(24.dp))
    PillButton(label = stringResource(R.string.onboarding_continue), onClick = onNext, primary = true)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_skip_for_now),
        modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable(role = Role.Button, onClick = onSkip).padding(14.dp),
        textAlign = TextAlign.Center,
        color = Color.White.copy(alpha = 0.72f),
        style = MaterialTheme.typography.labelLarge,
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ColumnScope.DiscoveryPage(
    youtubeConnected: Boolean,
    spotifyConnected: Boolean,
    onBack: () -> Unit,
    onYouTubeConnect: () -> Unit,
    onSpotifyConnect: () -> Unit,
    onListenTogether: () -> Unit,
    onContinue: () -> Unit,
) {
    Spacer(Modifier.height(10.dp))
    BackButton(onBack)
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.onboarding_discovery_heading),
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = 30.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.8).sp,
        ),
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_discovery_description),
        style = MaterialTheme.typography.bodyMedium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color.White.copy(alpha = 0.72f),
    )
    Spacer(Modifier.height(20.dp))
    DiscoveryOption(
        title = "YouTube Music",
        description = stringResource(
            if (youtubeConnected) R.string.onboarding_youtube_connected else R.string.onboarding_youtube_connect,
        ),
        actionLabel = stringResource(if (youtubeConnected) R.string.onboarding_connected else R.string.onboarding_connect),
        onClick = { if (!youtubeConnected) onYouTubeConnect() },
        accent = Color(0xFFFF5252),
        leading = {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x33FF5252)), contentAlignment = Alignment.Center) {
                Icon(AudifyIcons.Play, contentDescription = null, tint = Color(0xFFFF7777), modifier = Modifier.size(22.dp))
            }
        },
    )
    Spacer(Modifier.height(10.dp))
    DiscoveryOption(
        title = "Spotify",
        description = stringResource(
            if (spotifyConnected) R.string.onboarding_spotify_connected else R.string.onboarding_spotify_connect,
        ),
        actionLabel = stringResource(if (spotifyConnected) R.string.onboarding_connected else R.string.onboarding_connect),
        onClick = { if (!spotifyConnected) onSpotifyConnect() },
        accent = Color(0xFF1DB954),
        leading = {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x331DB954)), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.spotify_logo), contentDescription = null, modifier = Modifier.size(25.dp))
            }
        },
    )
    Spacer(Modifier.height(10.dp))
    DiscoveryOption(
        title = "Listen Together",
        description = stringResource(R.string.onboarding_listen_together_description),
        actionLabel = stringResource(R.string.onboarding_explore),
        onClick = onListenTogether,
        accent = Color(0xFFE6C17A),
        leading = {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x33E6C17A)), contentAlignment = Alignment.Center) {
                Text("♫", color = Color(0xFFE6C17A), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        },
    )
    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.onboarding_local_music_no_account), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    PillButton(label = stringResource(R.string.onboarding_continue_without_connecting), onClick = onContinue, primary = true)
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun DiscoveryOption(
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit,
    accent: Color,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.lerp(Color(0xFF182333), accent, 0.14f))
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Color.White.copy(alpha = 0.66f), style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(actionLabel, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** A restrained headline that remains legible at the user's chosen font scale. */
@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = (-1.0).sp,
        ),
        color = Color.White,
    )
}

/**
 * The name, set in the heading's own face and weight so what you type reads
 * as part of the page rather than a form on top of it.
 */
@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val nameHint = stringResource(R.string.onboarding_name_hint)
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.6).sp,
        color = Color.White,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = nameHint,
                style = style,
                color = Color.White.copy(alpha = 0.38f),
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = nameHint },
        )
    }
}

/**
 * Full-width action with a clear tonal distinction between primary and skip.
 */
@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "pillScale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(180), label = "pillAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(if (primary) Color(0xFFF2F3F7) else Color(0xFF202431))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 17.sp,
            fontWeight = FontWeight.W700,
            color = if (primary) Color(0xFF090B12) else Color(0xFFF2F3F7),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AudifyIcons.ChevronRight,
            contentDescription = stringResource(R.string.onboarding_back),
            tint = Color.White,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { scaleX = -1f },
        )
    }
}

internal const val PAGE_WELCOME = 0
internal const val PAGE_DISCOVERY = 1
internal const val PAGE_NAME = 2
internal const val PAGE_MUSIC = 3

/** The page Back should reveal, or null when the welcome is already at the root. */
internal fun previousOnboardingPage(page: Int): Int? = when (page) {
    PAGE_DISCOVERY -> PAGE_WELCOME
    PAGE_NAME -> PAGE_DISCOVERY
    PAGE_MUSIC -> PAGE_NAME
    else -> null
}

/** Long enough for a full name; the party server keeps the first 80. */
private const val NAME_LIMIT = 40

/** The account page keeps leaning into YouTube's red while it moves. */
private val ConnectPalettes = listOf(
    MeshPalette(listOf(Color(0xFFFF1E3C), Color(0xFFC2185B), Color(0xFFFF8F3D), Color(0xFF4A1C8C))),
    MeshPalette(listOf(Color(0xFFE53935), Color(0xFFFF6E40), Color(0xFFAD1457), Color(0xFF311B92))),
    MeshPalette(listOf(Color(0xFFFF3D00), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFFB71C1C))),
    MeshPalette(listOf(Color(0xFFF50057), Color(0xFFFF9100), Color(0xFFC51162), Color(0xFF4527A0))),
)
