// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.audify.R
import com.music.audify.sharedui.resources.Res
import com.music.audify.sharedui.resources.ic_google_g
import com.music.audify.sharedui.resources.ic_logo
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
    initialName: String,
    /** Saves the party name. Called on Next, before the second page. */
    onNameChosen: (String) -> Unit,
    /** Opens the Google sign-in. The caller finishes onboarding once it lands. */
    onSignIn: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_WELCOME) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = page != PAGE_WELCOME) {
        page = when (page) {
            PAGE_NAME -> PAGE_WELCOME
            PAGE_CONNECT -> PAGE_NAME
            else -> if (signedIn) PAGE_NAME else PAGE_CONNECT
        }
    }
    // "Sign in with Google" goes through the app's own sign-in WebView, raised
    // over this page; the account landing is what ends the welcome.
    LaunchedEffect(signedIn, page) {
        if (signedIn && page == PAGE_CONNECT) page = PAGE_MUSIC
    }

    // Keep the original animated, multi-colour onboarding mesh. The welcome
    // and setup pages use AUDIFY's warm palette; account connection leans into
    // the original red/pink/violet palette.
    val palette = cyclingMeshPalette(if (page == PAGE_CONNECT) ConnectPalettes else WelcomeMeshPalettes)

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
            continuous = true,
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
                val forward = targetState > initialState
                val slide = tween<IntOffset>(420)
                (slideInHorizontally(slide) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320, 100)))
                    .togetherWith(
                        slideOutHorizontally(slide) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)),
                    )
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
                    PAGE_WELCOME -> WelcomePage(onNext = { page = PAGE_NAME })
                    PAGE_NAME -> NamePage(
                        name = name,
                        onNameChange = { name = it.take(NAME_LIMIT) },
                        onBack = { page = PAGE_WELCOME },
                        onNext = next@{
                            val chosen = name.trim()
                            if (chosen.isEmpty()) return@next
                            focusManager.clearFocus()
                            onNameChosen(chosen)
                            if (signedIn) page = PAGE_MUSIC else page = PAGE_CONNECT
                        },
                    )
                    PAGE_CONNECT -> ConnectPage(
                        onBack = { page = PAGE_NAME },
                        onSignIn = onSignIn,
                        onSkip = { page = PAGE_MUSIC },
                    )
                    else -> MusicSetupPage(onBack = { page = if (signedIn) PAGE_NAME else PAGE_CONNECT }, onFinish = onFinish)
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
    Title("You're all set.")
    Spacer(Modifier.height(14.dp))
    Text(
        text = "Find something new, play audio from your device, or start a listening session with friends. You can connect music services whenever you need them.",
        style = MaterialTheme.typography.bodyLarge,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        color = Color.White.copy(alpha = 0.78f),
    )
    Spacer(Modifier.height(40.dp))
    PillButton(label = "Start listening", onClick = onFinish, primary = true)
    Spacer(Modifier.height(24.dp))
}

/** First-run introduction: a concise product overview before asking for details. */
@Composable
private fun ColumnScope.WelcomePage(onNext: () -> Unit) {
    Spacer(Modifier.height(42.dp))
    Image(
        painter = painterResource(Res.drawable.ic_logo),
        contentDescription = "Audify",
        modifier = Modifier
            .size(width = 76.dp, height = 56.dp),
    )
    Spacer(Modifier.height(36.dp))
    Text(
        text = "Find it.\nFeel it.\nShare it.",
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
        text = "Discover music, play the files you already own, and listen together. Your library stays yours.",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.76f),
        lineHeight = 25.sp,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(52.dp))
    PillButton(label = "Get started", onClick = onNext, primary = true)
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
            OnboardingHeader(page = page, onSkip = onSkip)
            content()
        }
    }
}

@Composable
private fun OnboardingHeader(page: Int, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("AUDIFY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp, color = Color(0xFFF2F3F7))
        Box(
            Modifier
                .height(48.dp)
                .widthIn(min = 64.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Skip", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.82f))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .semantics { contentDescription = "Step ${page + 1} of 4" },
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
    Title(stringResource(R.string.onboarding_title))

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
    PillButton(
        label = stringResource(R.string.onboarding_next),
        onClick = onNext,
        enabled = name.isNotBlank(),
        primary = true,
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ColumnScope.ConnectPage(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
) {
    Spacer(Modifier.height(32.dp))
    BackButton(onBack)
    Spacer(Modifier.height(32.dp))
    Title("Your library,\nwithin reach.")
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Connect YouTube Music to access your saved playlists, liked songs, and supported account features. This is optional — local audio works without signing in.",
        style = MaterialTheme.typography.bodyLarge,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        color = Color.White.copy(alpha = 0.72f),
    )

    Spacer(Modifier.height(30.dp))
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "Your sign-in is handled by Google. Audify never asks for your Google password.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.82f),
            lineHeight = 21.sp,
        )
    }

    Spacer(Modifier.height(42.dp))

    PillButton(
        label = "Continue with Google",
        onClick = onSignIn,
        primary = true,
        leading = {
            Image(
                painter = painterResource(Res.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
    )
    Spacer(Modifier.height(14.dp))
    PillButton(
        label = stringResource(R.string.onboarding_skip_sign_in),
        onClick = onSkip,
        primary = false,
    )
    Spacer(Modifier.height(24.dp))
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

private const val PAGE_WELCOME = 0
private const val PAGE_NAME = 1
private const val PAGE_CONNECT = 2
private const val PAGE_MUSIC = 3

/** Long enough for a full name; the party server keeps the first 80. */
private const val NAME_LIMIT = 40

/** The account page keeps leaning into YouTube's red while it moves. */
private val ConnectPalettes = listOf(
    MeshPalette(listOf(Color(0xFFFF1E3C), Color(0xFFC2185B), Color(0xFFFF8F3D), Color(0xFF4A1C8C))),
    MeshPalette(listOf(Color(0xFFE53935), Color(0xFFFF6E40), Color(0xFFAD1457), Color(0xFF311B92))),
    MeshPalette(listOf(Color(0xFFFF3D00), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFFB71C1C))),
    MeshPalette(listOf(Color(0xFFF50057), Color(0xFFFF9100), Color(0xFFC51162), Color(0xFF4527A0))),
)
