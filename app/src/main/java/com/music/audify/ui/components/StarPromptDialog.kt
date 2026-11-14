package com.music.audify.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

private const val STAR_REPO_URL = "https://github.com/sachit1751-art/Audify"

/**
 * When to ask for a GitHub star. GitHub can't tell the app who has already
 * starred, so the prompt is a polite nudge: it waits for a few launches and a
 * few days of use, backs off after "Maybe later", and stops for good once the
 * person taps Star or has put it off a few times.
 */
private object StarPrompt {
    private const val PREFS = "star_prompt"
    private const val KEY_DONE = "done"
    private const val KEY_LAUNCHES = "launches"
    private const val KEY_FIRST_AT = "first_at"
    private const val KEY_SNOOZE_UNTIL = "snooze_until"
    private const val KEY_SNOOZES = "snoozes"
    private const val KEY_LAST_OPEN = "last_open"

    private const val MIN_LAUNCHES = 5
    private const val MIN_AGE_MS = 2L * 24 * 60 * 60 * 1000
    private const val MIN_OPEN_GAP_MS = 30L * 60 * 1000
    private const val SNOOZE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_SNOOZES = 2

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * One count per time the app comes to the front, not per process start: the
     * player service keeps the process alive, so process starts are rare. Opens
     * closer together than [MIN_OPEN_GAP_MS] count as one.
     */
    fun registerOpen(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val editor = p.edit()
        if (p.getLong(KEY_FIRST_AT, 0L) == 0L) {
            editor.putLong(KEY_FIRST_AT, now)
        }
        if (now - p.getLong(KEY_LAST_OPEN, 0L) >= MIN_OPEN_GAP_MS) {
            editor.putInt(KEY_LAUNCHES, p.getInt(KEY_LAUNCHES, 0) + 1)
            editor.putLong(KEY_LAST_OPEN, now)
        }
        editor.apply()
    }

    fun shouldShow(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean(KEY_DONE, false)) return false
        val now = System.currentTimeMillis()
        return p.getInt(KEY_LAUNCHES, 0) >= MIN_LAUNCHES &&
            now - p.getLong(KEY_FIRST_AT, now) >= MIN_AGE_MS &&
            now >= p.getLong(KEY_SNOOZE_UNTIL, 0L)
    }

    fun snooze(context: Context) {
        val p = prefs(context)
        val snoozes = p.getInt(KEY_SNOOZES, 0) + 1
        p.edit()
            .putInt(KEY_SNOOZES, snoozes)
            .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
            .putBoolean(KEY_DONE, snoozes >= MAX_SNOOZES)
            .apply()
    }

    fun finish(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, true).apply()
    }
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

/** Mount once near the app root; shows the star prompt when it is time. */
@Composable
internal fun StarPromptHost(isPlaying: Boolean) {
    val context = LocalContext.current
    var show by rememberSaveable { mutableStateOf(false) }

    val owner = remember(context) { context.findLifecycleOwner() }

    // Checked every time the app comes to the front: the open is counted, then a
    // few seconds later the prompt is shown if it qualifies. Playing music does
    // not block it; the dialog never touches playback.
    LaunchedEffect(owner) {
        val lifecycleOwner = owner ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            StarPrompt.registerOpen(context)
            if (show) return@repeatOnLifecycle
            delay(6_000)
            if (StarPrompt.shouldShow(context)) show = true
        }
    }

    if (show) {
        StarPromptDialog(
            onStar = {
                StarPrompt.finish(context)
                show = false
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(STAR_REPO_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure {
                    Toast.makeText(context, STAR_REPO_URL, Toast.LENGTH_SHORT).show()
                }
            },
            onLater = {
                StarPrompt.snooze(context)
                show = false
            },
        )
    }
}

/**
 * The star in the prompt: it spins and springs in, then keeps a slow tilt and
 * breath going with a soft halo spreading out behind it.
 */
@Composable
private fun AnimatedStarBadge() {
    val primary = MaterialTheme.colorScheme.primary
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }
    val loop = rememberInfiniteTransition(label = "starLoop")
    val pulse by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "starPulse",
    )
    val tilt by loop.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "starTilt",
    )
    Box(
        modifier = Modifier.size(68.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    val s = 1f + 0.28f * pulse
                    scaleX = s
                    scaleY = s
                    alpha = 0.3f * (1f - pulse) * entrance.value.coerceIn(0f, 1f)
                }
                .clip(CircleShape)
                .background(primary),
        )
        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = entrance.value
                    scaleY = entrance.value
                }
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = primary,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        val settled = entrance.value.coerceIn(0f, 1f)
                        val s = (0.6f + 0.4f * entrance.value) * (1f + 0.07f * pulse)
                        scaleX = s
                        scaleY = s
                        rotationZ = (1f - entrance.value) * -140f + tilt * settled
                    },
            )
        }
    }
}

@Composable
private fun StarPromptDialog(
    onStar: () -> Unit,
    onLater: () -> Unit,
) {
    GlassAlertDialog(onDismiss = onLater) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedStarBadge()
            Text(
                text = "Enjoying Audify?",
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "A star on GitHub is a free way to support the project and helps others find it.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        AlertRule()
        AlertAction(label = "Star on GitHub", emphasised = true, onClick = onStar)
        AlertRule()
        AlertAction(label = "Maybe later", emphasised = false, onClick = onLater)
    }
}
