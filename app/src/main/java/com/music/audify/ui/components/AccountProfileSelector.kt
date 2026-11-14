package com.music.audify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import com.music.audify.ui.icons.SfIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.audify.R
import com.music.audify.auth.GoogleAccountSession
import com.music.audify.auth.YouTubeProfile
import com.music.audify.data.settings.AppSettings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Shared compact selector for every visible YouTube account avatar. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AccountProfileSelector(
    accounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    hazeState: HazeState,
    onSelect: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (GoogleAccountSession) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    /** Called the moment the exit starts, so what the card covered can come back with it. */
    onClosing: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var managing by remember { mutableStateOf(false) }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val profileOverride by AppSettings.profileOverride.collectAsStateWithLifecycle()
    val touch = rememberIosMenuTouch(reduceMotion)
    val shape = RoundedCornerShape(22.dp)
    val surface = MaterialTheme.colorScheme.surface
    val scrim = MaterialTheme.colorScheme.scrim
    val dark = surface.luminance() < 0.5f
    // The thin material lets too much of the page through behind the text. Dark
    // mode gets a black wash over it; light mode a white one, which keeps the
    // dark text readable over a busy cover instead of muddying it.
    val material = HazeMaterials.thin(surface)
    val style = remember(material, dark) {
        HazeStyle(
            backgroundColor = material.backgroundColor,
            tints = material.tints + HazeTint(
                if (dark) Color.Black.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.40f),
            ),
            blurRadius = material.blurRadius,
            noiseFactor = material.noiseFactor,
            fallbackTint = material.fallbackTint,
        )
    }

    // pop: the card's scale, which may overshoot — that is the bounce.
    // fade: its alpha and the scrim's. reveal: the avatars' own, a beat behind.
    val pop = remember { Animatable(if (reduceMotion) 1f else 0.55f) }
    val fade = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            reveal.snapTo(1f)
            fade.animateTo(1f, tween(120))
        } else {
            launch { fade.animateTo(1f, tween(160, easing = FastOutSlowInEasing)) }
            launch { pop.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 460f)) }
            launch {
                delay(70)
                if (!closing) reveal.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 520f))
            }
        }
    }

    // Every way out goes through here: the exit plays first, then the caller's
    // own action runs, so nothing removes the card while it is still leaving.
    val close: (() -> Unit) -> Unit = { after ->
        if (!closing) {
            closing = true
            onClosing()
            scope.launch {
                if (reduceMotion) {
                    fade.animateTo(0f, tween(110))
                } else {
                    launch { reveal.animateTo(0f, tween(130, easing = FastOutLinearInEasing)) }
                    launch { fade.animateTo(0f, tween(210, delayMillis = 60, easing = FastOutLinearInEasing)) }
                    // A small swell first, then it drops away: the bounce of the exit.
                    pop.animateTo(1.045f, tween(90, easing = FastOutSlowInEasing))
                    pop.animateTo(0.82f, tween(200, easing = FastOutLinearInEasing))
                }
                after()
            }
        }
    }

    // Registered after the caller's own BackHandler, so it wins and Back
    // plays the exit instead of cutting the card.
    BackHandler(enabled = !closing) { close(onDismiss) }

    Column(
        modifier = modifier.fillMaxSize()
            .drawBehind { drawRect(scrim.copy(alpha = 0.40f * fade.value.coerceIn(0f, 1f))) }
            .clickable(onClick = { close(onDismiss) }),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = shape,
            modifier = Modifier
                .padding(top = 56.dp, start = 20.dp, end = 12.dp)
                .widthIn(max = 276.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = if (reduceMotion) 1f else pop.value.coerceAtLeast(0f)
                    scaleX = s
                    scaleY = s
                    alpha = fade.value.coerceIn(0f, 1f)
                    // Grows out of the corner under the profile button.
                    transformOrigin = TransformOrigin(0.92f, 0f)
                    if (!reduceMotion) applyIosMenuJelly(touch, s, 0.92f, 0f)
                }
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = style,
                        )
                    },
                )
                // The same hairline the mini player and the nav bar wear.
                .border(
                    0.5.dp,
                    // White on dark like the bars; a light surface has nothing
                    // for a white hairline to show against.
                    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f),
                    shape,
                )
                .clickable(onClick = {}),
        ) {
            // Room under the last row to match the space between rows, so the
            // list doesn't stop short against the card's edge.
            IosMenuScrollEnvironment(touch) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp).iosMenuTouchHost(touch, iosMenuColors().pressed),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item { Text(stringResource(R.string.switch_account), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(18.dp, 14.dp, 18.dp, 4.dp)) }
                accounts.forEach { account ->
                item {
                    Text(account.email.ifBlank { account.name.ifBlank { stringResource(R.string.accounts) } },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp, 8.dp, 18.dp, 2.dp))
                }
                items(account.profiles.size) { index ->
                    val raw = account.profiles[index]
                    val selected = account.accountId == activeAccountId && raw.profileId == activeProfileId
                    // Nama dan foto buatan pengguna berlaku untuk profil yang sedang aktif.
                    val profile = if (selected && profileOverride.owner == AppSettings.profileOwnerOf(account.email)) {
                        val photo = profileOverride.photoPath
                            .takeIf { it.isNotEmpty() && java.io.File(it).exists() }
                            ?.let { "file://$it" }
                        raw.copy(
                            name = profileOverride.name.ifBlank { raw.name },
                            avatar = photo ?: raw.avatar,
                        )
                    } else {
                        raw
                    }
                    ProfileRow(
                        profile = profile,
                        selected = selected,
                        managing = managing,
                        reveal = { reveal.value },
                        onClick = { onSelect(account, profile); close(onDismiss) },
                        onRemove = { close { onRemoveAccount(account) } },
                    )
                }
                }
                item { SelectorAction(SfIcons.AddAccount, stringResource(R.string.add_account), onClick = { close(onAddAccount) }) }
                // A wide glyph — two people and a gear — drawn a size up to match the others.
                item { SelectorAction(SfIcons.ManageAccounts, stringResource(R.string.manage_accounts), iconSize = 26.dp) { managing = !managing } }
                item { SelectorAction(SfIcons.Settings, stringResource(R.string.settings), onClick = { close(onOpenSettings) }) }
            }
            }
        }
    }
}

@Composable private fun ProfileRow(profile: YouTubeProfile, selected: Boolean, managing: Boolean,
    reveal: () -> Float, onClick: () -> Unit, onRemove: () -> Unit) {
    val description = stringResource(if (selected) R.string.selected_account else R.string.switch_account)
    // The avatar and the tick scale and fade in a beat after the card, and out
    // before it leaves. The spring's overshoot on [reveal] is what makes it bounce.
    val revealFx = Modifier.graphicsLayer {
        val r = reveal()
        val s = r.coerceAtLeast(0f)
        scaleX = s
        scaleY = s
        alpha = r.coerceIn(0f, 1f)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        .jellyMenuRow(role = Role.RadioButton, onClick = if (managing) onRemove else onClick)
        .heightIn(min = 48.dp)
        .semantics { contentDescription = description }
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (profile.avatar != null) AsyncImage(profile.avatar, null, Modifier.size(32.dp).then(revealFx).clip(MaterialTheme.shapes.extraLarge))
        else Icon(SfIcons.AccountPlaceholder, null, Modifier.size(32.dp).then(revealFx), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
            Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(profile.handle.ifBlank { stringResource(if (profile.isBrandAccount) R.string.brand_account else R.string.personal) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected && !managing) Icon(Icons.Rounded.Check, stringResource(R.string.selected_account), Modifier.then(revealFx), tint = MaterialTheme.colorScheme.primary)
        if (managing) Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
    }
}

@Composable private fun SelectorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        .jellyMenuRow(onClick = onClick)
        .heightIn(min = 44.dp)
        .semantics { contentDescription = label }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        // In the avatars' column, so the labels line up with the names above.
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(iconSize)) }
        Spacer(Modifier.width(12.dp)); Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
