package com.music.audify.data.settings

import com.music.audify.data.webdav.update
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.storage.StorageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.media3.common.Player
import androidx.core.content.edit
import com.music.audify.BuildConfig
import com.music.audify.auth.AuthStore
import com.music.audify.data.canvas.SpotifyToken
import com.music.audify.data.lyrics.LyricsSource
import com.music.audify.data.model.HOME_SLOT_MOODS
import com.music.audify.data.model.homeShelfKey
import com.music.audify.data.model.HOME_SLOT_TOP_PICKS
import com.music.audify.data.model.HomeLayout
import com.music.audify.data.sources.SourceKind
import com.music.audify.playback.EqLayout
import com.music.audify.playback.EqualizerPreset
import com.music.audify.ui.components.VideoThumbnails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


/**
 * Whether a stream started under this ceiling may be served by [kind].
 *
 * Asked per stream rather than written into
 * [SourceConfig.enabled][com.music.audify.data.sources.SourceConfig.enabled],
 * which is what this used to do — an `applyQualityPreset` call flipped the
 * module and JioSaavn switches the moment a rung was picked. Two things
 * were wrong with that and both were reported together: picking a rung for
 * *mobile data* turned the sources off while sitting on Wi-Fi, and nothing
 * turned them back on when the connection changed, so a Wi-Fi ceiling of
 * Lossless still had no lossless source to reach. A ceiling is a property
 * of the connection in force; the switches on the Sources screen are the
 * user's standing choice. Storing the first in the second lost the second.
 *
 * [SourceKind.YOUTUBE] is permitted on every rung: it is what [maxKbps]
 * caps, and it is the only source that can answer at all when the ones
 * above it are skipped.
 */
fun AudioQuality.permits(kind: SourceKind): Boolean = when (this) {
    AudioQuality.LOSSLESS -> true
    // No lossless answer is wanted here, and a source that can serve one is
    // the slow half of the list: an addon fronting several catalogues walks
    // all of them before it answers, which is seconds spent to land on a
    // transcode JioSaavn already has at 320.
    AudioQuality.HIGH -> !kind.canServeLossless
    AudioQuality.MEDIUM, AudioQuality.LOW -> kind == SourceKind.YOUTUBE
}

/**
 * PCM format requested from Media3's AudioTrack sink.
 *
 * FLOAT_32 is not a cosmetic "hi-res" switch: it makes Media3 convert
 * high-resolution integer PCM to IEEE-754 float and configure AudioTrack for
 * PCM_FLOAT. Android may still route/resample it according to the selected
 * output device, which is why the player exposes the negotiated format.
 */
enum class OutputPcmMode(val label: String) {
    PCM_16("16-bit PCM"),
    FLOAT_32("32-bit float"),
}

/**
 * Seberapa jernih Liquid Glass sebagai posisi slider [level] 0..1:
 * 0 = Clear, 0.5 = Balanced (tampilan bawaan), 1 = Frosted. Posisi di antaranya
 * diinterpolasi linear antar titik. [tintAlpha] adalah lapisan warna di atas
 * kaca, [blurScale] pengali radius blur. Lensa, highlight, dan shadow
 * mengikuti [lensScale] saja.
 */
data class GlassClarity(val level: Float) {
    private val t = level.coerceIn(0f, 1f)

    // [soft]: sisi Clear -> Balanced dilandaikan (kuadrat) supaya perubahan blur dan
    // resolusi dekat Clear bertahap, bukan melompat. Titik 0, 0.5 dan 1 tidak berubah.
    private fun mix(clear: Float, balanced: Float, frosted: Float, soft: Boolean = false): Float =
        if (t <= 0.5f) {
            val u = t / 0.5f
            clear + (balanced - clear) * (if (soft) u * u else u)
        } else {
            balanced + (frosted - balanced) * ((t - 0.5f) / 0.5f)
        }

    val tintAlpha: Float = mix(0.12f, 0.30f, 0.46f)
    val blurScale: Float = mix(0.1f, 1f, 1.35f, soft = true)
    val saturation: Float = mix(1.4f, 1.5f, 1.5f)
    val resolutionScale: Float = mix(0.75f, 0.33f, 0.33f, soft = true)
    val lensScale: Float = mix(0.6f, 1f, 1f)

    // Refraksi tepi halus tanpa efek pelangi di sisi jernih.
    val chromatic: Boolean = t >= 0.25f

    companion object {
        const val CLEAR_LEVEL = 0f
        const val BALANCED_LEVEL = 0.5f
        const val FROSTED_LEVEL = 1f

        val BALANCED = GlassClarity(BALANCED_LEVEL)

        /** Pilihan lama (enum) yang tersimpan sebagai nama, dipetakan ke posisi slider. */
        fun levelForLegacyName(name: String?): Float = when (name) {
            "CLEAR" -> CLEAR_LEVEL
            "FROSTED" -> FROSTED_LEVEL
            else -> BALANCED_LEVEL
        }
    }
}

/**
 * Pengaturan manual tiap efek Liquid Glass, ditumpuk di atas [GlassClarity].
 * Semua pengali bernilai 1f = tampilan bawaan, jadi pengguna yang tidak
 * menyentuhnya tidak melihat perubahan apa pun.
 *
 * [rainbow] adalah kekuatan efek pelangi di tepi (chromatic aberration);
 * [RAINBOW_AUTO] mengikuti preset kejernihan seperti sebelumnya.
 */
data class GlassCustom(
    val blur: Float = 1f,
    val saturation: Float = 1f,
    val tint: Float = 1f,
    val refraction: Float = 1f,
    val refractionWidth: Float = 1f,
    val depth: Boolean = true,
    val rainbow: Float = RAINBOW_AUTO,
    val rim: Float = 1f,
    val shadow: Float = 1f,
    val quality: Float = 1f,
) {
    val isDefault: Boolean get() = this == DEFAULT

    /** Kekuatan pelangi yang dipakai: nilai pengguna, atau ikut preset saat Auto. */
    fun rainbowStrength(autoOn: Boolean): Float =
        if (rainbow < 0f) (if (autoOn) 1f else 0f) else rainbow

    fun coerced(): GlassCustom = copy(
        blur = blur.coerceIn(0f, 2.5f),
        saturation = saturation.coerceIn(0f, 2.5f),
        tint = tint.coerceIn(0f, 2.5f),
        refraction = refraction.coerceIn(0f, 2.5f),
        refractionWidth = refractionWidth.coerceIn(0.2f, 2.5f),
        rainbow = if (rainbow < 0f) RAINBOW_AUTO else rainbow.coerceIn(0f, 3f),
        rim = rim.coerceIn(0f, 1f),
        shadow = shadow.coerceIn(0f, 3f),
        quality = quality.coerceIn(0.5f, 1.5f),
    )

    companion object {
        const val RAINBOW_AUTO = -1f
        val DEFAULT = GlassCustom()
    }
}

/**
 * Pengaturan indikator pilihan (pil) pada tab bar Liquid Glass. Semua pengali
 * bernilai 1f = tampilan bawaan. [restLens] menjaga lengkung lensa pada
 * indikator saat diam, [restRefraction] dan [restRefractionWidth] mengatur
 * kekuatan dan lebarnya.
 */
data class GlassIndicator(
    val restLens: Boolean = true,
    val restRefraction: Float = 0.6f,
    val restRefractionWidth: Float = 0.8f,
    val liftRefraction: Float = 1f,
    val liftRefractionWidth: Float = 1f,
    val rainbow: Float = 1f,
    val depth: Boolean = true,
    val blur: Float = 1f,
    val saturation: Float = 1f,
    val brightness: Float = 1f,
    val fill: Float = 1f,
    val rim: Float = 1f,
    val innerShadow: Float = 1f,
    val innerRadius: Float = 1f,
    val shadow: Float = 1f,
    val lift: Float = 1f,
    val iconLift: Float = 1f,
    val squash: Float = 1f,
) {
    val isDefault: Boolean get() = this == DEFAULT

    fun coerced(): GlassIndicator = copy(
        restRefraction = restRefraction.coerceIn(0f, 2.5f),
        restRefractionWidth = restRefractionWidth.coerceIn(0.2f, 2.5f),
        liftRefraction = liftRefraction.coerceIn(0f, 2.5f),
        liftRefractionWidth = liftRefractionWidth.coerceIn(0.2f, 2.5f),
        rainbow = rainbow.coerceIn(0f, 3f),
        blur = blur.coerceIn(0f, 3f),
        saturation = saturation.coerceIn(0f, 2.5f),
        brightness = brightness.coerceIn(0f, 4f),
        fill = fill.coerceIn(0f, 3f),
        rim = rim.coerceIn(0f, 2f),
        innerShadow = innerShadow.coerceIn(0f, 2f),
        innerRadius = innerRadius.coerceIn(0f, 3f),
        shadow = shadow.coerceIn(0f, 3f),
        lift = lift.coerceIn(0f, 2f),
        iconLift = iconLift.coerceIn(0f, 2f),
        squash = squash.coerceIn(0f, 3f),
    )

    companion object {
        val DEFAULT = GlassIndicator()
    }
}

/**
 * Preset Liquid Glass siap pakai: [level], [custom] dan [frosted] diterapkan
 * sekaligus lewat [AppSettings.applyGlassPreset].
 */
enum class GlassPreset(
    val level: Float,
    val custom: GlassCustom,
    val indicator: GlassIndicator,
    val darkCustom: GlassCustom,
    val darkIndicator: GlassIndicator,
    val frosted: Boolean = false,
) {
    /** Tampilan bawaan Audify. */
    DEFAULT(
        level = 0.13f,
        custom = GlassCustom(
            blur = 0.66f,
            saturation = 1f,
            tint = 1.67f,
            refraction = 0.99f,
            refractionWidth = 0.36f,
            depth = true,
            rainbow = 0f,
            rim = 1f,
            shadow = 1f,
            quality = 1f,
        ),
        indicator = GlassIndicator(
            restLens = true,
            restRefraction = 2.5f,
            restRefractionWidth = 2.13f,
            liftRefraction = 0.93f,
            liftRefractionWidth = 0.46f,
            rainbow = 1f,
            depth = true,
            blur = 1f,
            saturation = 2.16f,
            brightness = 1f,
            fill = 1f,
            rim = 1f,
            innerShadow = 1f,
            innerRadius = 1f,
            shadow = 1f,
            lift = 0.79f,
            iconLift = 0.19f,
            squash = 0.39f,
        ),
        darkCustom = GlassCustom(
            blur = 0.66f,
            saturation = 1f,
            tint = 1.67f,
            refraction = 0.99f,
            refractionWidth = 0.36f,
            depth = true,
            rainbow = 0f,
            rim = 0.57f,
            shadow = 1.68f,
            quality = 1f,
        ),
        darkIndicator = GlassIndicator(
            restLens = true,
            restRefraction = 1.13f,
            restRefractionWidth = 0.66f,
            liftRefraction = 0.93f,
            liftRefractionWidth = 0.48f,
            rainbow = 1.3f,
            depth = true,
            blur = 0.43f,
            saturation = 0.63f,
            brightness = 1.57f,
            fill = 0.45f,
            rim = 0.75f,
            innerShadow = 2f,
            innerRadius = 1.01f,
            shadow = 1.01f,
            lift = 0.79f,
            iconLift = 0.19f,
            squash = 0.39f,
        ),
    ),

    /** Low-overhead glass: frosted surface, no lens sampling, restrained shadows. */
    PERFORMANCE(
        level = 0.32f,
        custom = GlassCustom(
            blur = 0.55f, saturation = 0.9f, tint = 1.15f,
            refraction = 0f, refractionWidth = 0.8f, depth = false,
            rainbow = 0f, rim = 0.65f, shadow = 0.35f, quality = 0.65f,
        ),
        indicator = GlassIndicator(
            restLens = false, restRefraction = 0f, restRefractionWidth = 0.8f,
            liftRefraction = 0f, liftRefractionWidth = 0.8f, rainbow = 0f,
            depth = false, blur = 0.45f, saturation = 1f, brightness = 1f,
            fill = 0.85f, rim = 0.65f, innerShadow = 0.35f, innerRadius = 1f,
            shadow = 0.35f, lift = 0.55f, iconLift = 0.12f, squash = 0.25f,
        ),
        darkCustom = GlassCustom(
            blur = 0.5f, saturation = 0.9f, tint = 1.05f,
            refraction = 0f, refractionWidth = 0.8f, depth = false,
            rainbow = 0f, rim = 0.55f, shadow = 0.45f, quality = 0.65f,
        ),
        darkIndicator = GlassIndicator(
            restLens = false, restRefraction = 0f, restRefractionWidth = 0.8f,
            liftRefraction = 0f, liftRefractionWidth = 0.8f, rainbow = 0f,
            depth = false, blur = 0.35f, saturation = 0.9f, brightness = 1.2f,
            fill = 0.65f, rim = 0.55f, innerShadow = 0.5f, innerRadius = 1f,
            shadow = 0.45f, lift = 0.55f, iconLift = 0.12f, squash = 0.25f,
        ),
        frosted = true,
    ),

    /** Full visual treatment: crisp refraction, rich colour, depth and edge highlights. */
    BEST_EXPERIENCE(
        level = 0.58f,
        custom = GlassCustom(
            blur = 1.12f, saturation = 1.2f, tint = 0.95f,
            refraction = 1.25f, refractionWidth = 1.15f, depth = true,
            rainbow = 0.7f, rim = 1f, shadow = 1.25f, quality = 1.25f,
        ),
        indicator = GlassIndicator(
            restLens = true, restRefraction = 1.45f, restRefractionWidth = 1.1f,
            liftRefraction = 1.15f, liftRefractionWidth = 1.05f, rainbow = 0.8f,
            depth = true, blur = 1.05f, saturation = 1.25f, brightness = 1.08f,
            fill = 1.05f, rim = 1.15f, innerShadow = 1.05f, innerRadius = 1.1f,
            shadow = 1.2f, lift = 0.85f, iconLift = 0.18f, squash = 0.4f,
        ),
        darkCustom = GlassCustom(
            blur = 1.05f, saturation = 1.15f, tint = 0.85f,
            refraction = 1.2f, refractionWidth = 1.1f, depth = true,
            rainbow = 0.55f, rim = 0.9f, shadow = 1.45f, quality = 1.2f,
        ),
        darkIndicator = GlassIndicator(
            restLens = true, restRefraction = 1.25f, restRefractionWidth = 1f,
            liftRefraction = 1.1f, liftRefractionWidth = 1f, rainbow = 0.65f,
            depth = true, blur = 0.85f, saturation = 1.1f, brightness = 1.3f,
            fill = 0.85f, rim = 1f, innerShadow = 1.35f, innerRadius = 1.05f,
            shadow = 1.35f, lift = 0.85f, iconLift = 0.18f, squash = 0.4f,
        ),
    );

    /** Nilai custom preset ini untuk tema terang atau gelap. */
    fun customFor(dark: Boolean): GlassCustom = if (dark) darkCustom else custom

    /** Nilai indikator preset ini untuk tema terang atau gelap. */
    fun indicatorFor(dark: Boolean): GlassIndicator = if (dark) darkIndicator else indicator

    /** True bila pengaturan saat ini sama dengan preset ini, di tema terang maupun gelap. */
    fun matches(level: Float, custom: GlassCustom, indicator: GlassIndicator, frosted: Boolean): Boolean =
        this.level == level && this.frosted == frosted &&
            ((custom == this.custom && indicator == this.indicator) ||
                (custom == darkCustom && indicator == darkIndicator))
}

/**
 * Which exclusive output, if any, a track is sent to unchanged. Declared in
 * the order the settings picker lists them; persisted by name, so the order
 * can change freely.
 *
 * Bit-perfect output bypasses the equalizer, loudness normalization,
 * crossfade, Automix, playback speed and software volume: any of them would
 * change the samples. Volume belongs to the DAC.
 *
 * See [com.music.audify.playback.audio.bitperfect.BitPerfectOutput].
 */
enum class BitPerfectMode {
    OFF,

    /**
     * Audify's own USB Audio Class driver on libusb: any Android version,
     * volume set on the DAC itself. Requires USB permission.
     */
    USB_DIRECT,

    /** An AAudio exclusive stream to a built-in DAC, for digital audio players. */
    DAP_EXCLUSIVE,

    /** Android 14's bit-perfect mixer for a USB DAC. */
    USB_ANDROID,
}

/**
 * What to keep when a track is saved to the device.
 *
 * Deliberately not [AudioQuality]. That enum budgets a *stream*, and is priced
 * per hour because the same bytes are spent again on every replay. A download is
 * the opposite trade — paid for once, kept, played from disk forever after — so
 * the figure that decides it is what one track costs, and the rung worth
 * defaulting to is the top one rather than the cheap one.
 *
 * The rungs themselves differ too. On the YouTube fallback path a download is
 * Opus-in-WebM (see
 * [StreamResolver.resolveForDownload][com.music.audify.data.innertube.StreamResolver.resolveForDownload]),
 * while configured sources can supply their own AAC or lossless copy. And
 * [LOSSLESS] has no streaming counterpart at all: it is the only rung that lets
 * a configured source's bit-exact file end up as a file on disk.
 */
enum class DownloadQuality(
    /** Ceiling for the YouTube Opus ladder. [Int.MAX_VALUE] means "whichever rung is best". */
    val maxKbps: Int,
    val label: String,
    val detail: String,
    /** Roughly what one four-minute track costs at this rung, sans unit context. */
    val perTrack: String,
    /** Whether a source's bit-exact file is worth keeping, or a transcode will do. */
    val keepsLossless: Boolean,
) {
    STANDARD(128, "Standard", "~128 kbps Opus · fits more on the device", "~4 MB", false),
    HIGH(Int.MAX_VALUE, "High", "Best audio on offer; source quality first", "~8 MB", false),
    LOSSLESS(
        Int.MAX_VALUE,
        "Lossless",
        "Bit-exact if a source has it, best Opus if not",
        "~35 MB",
        true,
    ),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}

/** Built-in app-wide backgrounds; a custom photo takes precedence. */
enum class AppBackgroundStyle(val label: String) {
    PLAIN("Plain"), OCEAN("Ocean")
}

/**
 * How the app's SF Pro Display is set. [DEFAULT] draws each weight one cut
 * lighter and spaces it to Apple Music's own screens; [LEGACY] is the cut the
 * app shipped with before that, every weight its own and spaced tight.
 */
enum class AppFont { DEFAULT, SYSTEM, SERIF, LEGACY }

/**
 * The accent the user picked for the app: tab bar, active glyphs, sliders,
 * links and selected states. [dark] and [light] are Apple's system colours for
 * each appearance, as ARGB; [onAccent] is what text or a tick on top of the
 * accent is drawn in. [RED] is Apple Music's own and the default.
 */
enum class AccentColor(val dark: Long, val light: Long, val onAccent: Long = 0xFFFFFFFF) {
    RED(0xFFFA243C, 0xFFFA243C),
    ORANGE(0xFFFF9F0A, 0xFFFF9500),
    YELLOW(0xFFFFD60A, 0xFFE6B800, 0xFF000000),
    GREEN(0xFF30D158, 0xFF34C759),
    TEAL(0xFF40C8E0, 0xFF30B0C7),
    BLUE(0xFF0A84FF, 0xFF007AFF),
    INDIGO(0xFF5E5CE6, 0xFF5856D6),
    PURPLE(0xFFBF5AF2, 0xFFAF52DE),
    PINK(0xFFFF375F, 0xFFFF2D55),
    GRAY(0xFF98989D, 0xFF8E8E93),
}

/**
 * Which of the equaliser's two tabs is driving the sound.
 *
 * One at a time rather than both at once: they are two ways of describing the
 * same curve, and summing them would mean a tone pad sitting at dead centre
 * still quietly altering whatever the sliders said.
 */
enum class EqualizerMode {
    /** The tone pad: tilt, contour, and how wide each is. */
    DYNAMIC,

    /** Seven sliders and a preset list. */
    MANUAL,
}

/** Stable persisted ordering for each on-device music library. */
enum class LocalMusicSort {
    TITLE_ASC,
    TITLE_DESC,
    DATE_ADDED,
    DATE_MODIFIED,
}

/**
 * Ordering for the track list on an album or playlist page — the same idea as
 * the Downloads folder's sort, with a date option for the one thing a
 * catalogue row can still be dated by: the position it sits at. A playlist's
 * running order is the order songs were added in — YouTube Music appends each
 * addition at the foot — so read backwards it *is* a date order, newest first.
 * DetailScreen.kt holds the sort itself. Persisted app-wide rather than per
 * page: one choice, kept until the user makes another.
 */
enum class SongSort {
    DEFAULT,
    TITLE_ASC,
    TITLE_DESC,
    DATE_ADDED_ASC,
    DATE_ADDED_DESC,
}

/**
 * App settings, backed by SharedPreferences and exposed as flows.
 *
 * PlaybackService runs in the same process as the UI, so it observes these
 * same flows and applies changes to the live ExoPlayer instance immediately —
 * no restart, no rebinding.
 */
object AppSettings {
    private val startupIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var prefs: SharedPreferences
    private lateinit var wallpaperPrefs: SharedPreferences

    /** Only for the Discord, WebDAV and SMB secrets — everything else on here is plain prefs. */
    private lateinit var authStore: AuthStore

    /**
     * Quality ceilings, one per kind of connection — the point of the split is
     * that Wi-Fi can stay on Lossless while mobile data is capped. Both
     * default to Lossless; the mobile plan is the user's to budget, not ours
     * to assume.
     */
    val audioQualityWifi = MutableStateFlow(AudioQuality.LOSSLESS)
    val audioQualityCellular = MutableStateFlow(AudioQuality.LOSSLESS)

    /** Allowed duration difference when replacing a playing stream with one from a source. */
    val upgradeLengthSlackSeconds = MutableStateFlow(DEFAULT_UPGRADE_LENGTH_SLACK_SECONDS)

    /**
     * What a saved file should be, answered on its own terms.
     *
     * Kept apart from the two ceilings above on purpose. Those are about what
     * this minute's connection costs, and a download outlives the minute it was
     * started in — capping a permanent file at whichever network happened to be
     * in hand bakes a temporary decision into a lasting artefact, and the
     * reverse (a High ceiling on Wi-Fi implying 35MB FLACs of everything) is
     * just as wrong in the other direction.
     *
     * Data spend on a download is [wifiOnlyDownloads]' problem, not this
     * setting's, which is what lets this one be purely about the file.
     *
     * Defaults to [DownloadQuality.LOSSLESS] because that is what the download
     * path already did on an uncapped connection, and [migrateDownloadQuality]
     * keeps it that way for the people it didn't.
     */
    val downloadQuality = MutableStateFlow(DownloadQuality.LOSSLESS)

    /**
     * Refuse to start a download while the connection charges for data.
     *
     * Metered rather than literally-Wi-Fi, the same test [effectiveAudioQuality]
     * makes, because the thing worth protecting is the bill and not the radio: a
     * tethered hotspot is Wi-Fi that costs money, and an unmetered home
     * connection is worth using whether or not it arrives over Wi-Fi.
     *
     * On by default for fresh installs. [downloadQuality] defaulting to Lossless means a tap that used
     * to spend four megabytes of mobile data can now spend thirty-five, and of
     * the two ways to get that wrong — silently overspending a data plan, or
     * refusing with a sentence naming the switch that would allow it — only the
     * second is recoverable by the person it happens to.
     */
    val wifiOnlyDownloads = MutableStateFlow(true)

    /** Background update discovery stays enabled by default; installing remains user-confirmed. */
    val automaticUpdateChecks = MutableStateFlow(true)

    /** Stable is the default; opt in explicitly to GitHub pre-releases. */
    val includePreReleaseUpdates = MutableStateFlow(false)

    /** Include GitHub pre-releases in update checks and download their APKs without installing them. */
    val betaUpdatesEnabled = MutableStateFlow(false)

    /**
     * Keep ordinary downloads in Music/BitChord where other music apps can see
     * them. Off (the default) keeps downloads in this app's private storage.
     * HLS downloads always stay private because they are a playlist package,
     * not one portable audio file.
     */
    val exportDownloads = MutableStateFlow(false)

    /** Keep the account's Liked Music downloaded; disabled until explicitly enabled. */
    val downloadLikedSongs = MutableStateFlow(false)

    /** Automatically keep tracks downloaded when the user likes them. */
    val autoDownloadLikedSongs = MutableStateFlow(false)

    /**
     * Check the playlists downloaded whole for newly added songs and download
     * them, in the background. On for fresh installs; users can disable it to
     * avoid background data and storage use. See [com.music.audify.download.PlaylistSync].
     */
    val syncDownloadedPlaylists = MutableStateFlow(false)

    /** Whether the active network charges for data. `null` while offline. */
    val meteredConnection = MutableStateFlow<Boolean?>(null)

    // `losslessAudio` used to live here, behind a "Prefer lossless" switch on
    // the Sources screen. It is gone: sources are asked for their best and each
    // degrades on its own terms, so the switch's only real effect was to ask a
    // module for a worse file than it was holding. See
    // [SourceResolver.requestForNow][com.music.audify.data.sources.SourceResolver.requestForNow],
    // which now reads [effectiveAudioQuality] and nothing else.

    val crossfadeSeconds = MutableStateFlow(0)

    /** Crossfades manual next/previous actions when the listener enables it. */
    val crossfadeOnSkip = MutableStateFlow(false)

    /**
     * Lama crossfade skip yang sedang berjalan, dalam ms; 0 selain saat itu.
     * Dibaca player untuk menyamakan durasi dissolve cover dengan audionya.
     */
    val skipCrossfadeMs = MutableStateFlow(0L)

    /**
     * Lama mix/crossfade yang sedang berjalan, dalam ms; 0 selain saat itu.
     * Dibaca UI agar transisi cover berjalan sepanjang mix.
     */
    val mixFadeMs = MutableStateFlow(0L)

    /**
     * Cover lagu tujuan selama mix/crossfade berjalan; null selain saat itu.
     * Sleeve pindah ke cover ini sejak mix mulai, bukan menunggu lagu berganti.
     */
    val mixIncomingArtworkUrl = MutableStateFlow<String?>(null)

    /**
     * Lets Automix's analyzer decide the transition's timing and length
     * from each track's tempo, energy and structure, replacing the fixed
     * [crossfadeSeconds] window rather than needing it set to anything first
     * — [crossfadeSeconds] only matters here as a fallback while a pair is
     * still being analysed. On for fresh installs; analysis costs a background
     * decode per track and can be disabled in settings.
     *
     * See [com.music.audify.playback.smart.TransitionPlanner].
     */
    val smartFadeEnabled = MutableStateFlow(false)

    /** The CPU budget used by Beat This! and vocal analysis for Automix. */
    val automixPerformanceMode = MutableStateFlow(AutomixPerformanceMode.BALANCED)
    val skipSilence = MutableStateFlow(false)
    /** Pause playback when the system music volume transitions to zero. */
    val pauseOnMute = MutableStateFlow(false)
    /** Resume only after a Bluetooth route reconnects following a noisy pause. */
    val resumeOnBluetooth = MutableStateFlow(false)

    /** Requested PCM representation at the Android AudioTrack boundary. */
    val outputPcmMode = MutableStateFlow(OutputPcmMode.PCM_16)

    /** Prefer an attached USB audio output over the system's normal route. */
    val preferUsbDac = MutableStateFlow(false)

    /** Unprocessed output to a DAC; see [BitPerfectMode]. */
    val bitPerfectMode = MutableStateFlow(BitPerfectMode.OFF)

    /**
     * Lets direct USB play to a DAC with no volume control Audify can set:
     * full scale, with the DAC's own knob (if any) the only volume. Off unless
     * the listener accepts a second warning.
     */
    val usbAllowFixedVolume = MutableStateFlow(false)

    /**
     * Lets the exclusive output convert a track its DAC can't take as it is, to
     * the DAC's highest format. Off, such a track goes to normal output and no
     * sample on the exclusive path is ever changed.
     */
    val convertForLimitedDac = MutableStateFlow(false)

    /**
     * Level every track to the same loudness, using YouTube's own
     * normalization figure for it — see
     * [com.music.audify.playback.PlaybackService.setupLoudnessEnhancer].
     *
     * On by default, which is the one genuinely contentious thing about it.
     * The case for it: a queue drawn from several sources is a queue of
     * several mastering eras, and the gap between a 1980s CD transfer and a
     * modern master is routinely fifteen decibels — loud enough that the
     * listener's own volume control is the wrong tool, because the setting
     * that suits one track hurts at the next. Every streaming service
     * normalizes by default for the same reason.
     *
     * The case against it is that a constant gain is still a multiplication,
     * so this is the first thing in Audify that is *on* out of the box and
     * alters samples. Rather than hide that, the Audio Pipeline readout names
     * it: its Bit-exact row reports the first stage in the chain that is
     * altering samples, and switching this off is the first thing a listener
     * chasing an untouched signal would do.
     */
    val loudnessNormalization = MutableStateFlow(true)

    /** Skip loudness normalization while the active output is the phone's own speaker. */
    val loudnessOffOnSpeaker = MutableStateFlow(true)

    /**
     * Whether a source offering a Dolby Atmos rendition is allowed to serve it.
     *
     * On by default: where the device can decode it, Atmos is the premium
     * rendition the catalogue holds and the one most people are paying a
     * subscription for.
     *
     * Off is a real preference and not just a safety valve. Atmos is E-AC-3,
     * which is *lossy* — a track with an Atmos master is frequently also held
     * as a FLAC, and someone listening on wired headphones may well prefer the
     * bit-exact stereo copy to a spatial mix their output can't render. Turning
     * this off is how they say so; see
     * [ModuleSource.unplayable][com.music.audify.data.sources.ModuleSource],
     * which is where the refusal is applied.
     *
     * Independent of whether the device *can* decode it — that question is
     * [DeviceCodecs.playsDolbyAtmos][com.music.audify.data.sources.DeviceCodecs],
     * and the two are deliberately not folded together: this one is the
     * listener's answer, is persisted, and must survive being read on a phone
     * that cannot honour it (a restored backup, a swapped device) without
     * quietly rewriting itself.
     */
    val dolbyAtmos = MutableStateFlow(true)

    /**
     * Play a YouTube-queued track straight from an addon ranked above YouTube,
     * rather than starting it on YouTube and swapping up mid-song.
     *
     * Also narrows read-ahead to the next track only, and only once the
     * playing one is within [DirectSourceGate.WINDOW_MS][com.music.audify.playback.DirectSourceGate.WINDOW_MS]
     * of its transition — see [DirectSourceGate][com.music.audify.playback.DirectSourceGate].
     * Automix analysis is untouched: it keeps reading its own YouTube Opus copy.
     */
    val directSourceStream = MutableStateFlow(false)

    /**
     * Widens stereo output via [com.music.audify.playback.SpatialAudioProcessor],
     * a stereo widening + cross-feed effect running inside ExoPlayer's own
     * pipeline. Not true object-based spatial audio — YouTube only ever hands
     * us a stereo stream, so there's no Atmos-style source to render.
     */
    val spatialAudio = MutableStateFlow(false)

    /**
     * Per-track override of [spatialAudio], keyed by media id and saved the
     * moment the player menu's row is tapped. A track without an entry
     * follows the global switch.
     */
    val spatialAudioByTrack = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /**
     * The app's own equaliser, master switch.
     *
     * Separate from the system equaliser row beside it, which is still there and
     * still opens the device's panel. The two stack rather than compete — this
     * one runs inside ExoPlayer before the sink, that one hangs off the audio
     * session after it — so someone who prefers their OEM's can leave this off
     * and lose nothing.
     */
    val equalizerEnabled = MutableStateFlow(false)

    /** Which tab is driving it. */
    val equalizerMode = MutableStateFlow(EqualizerMode.DYNAMIC)

    /** Tone pad, horizontal: warm at -5, bright at +5. */
    val equalizerToneX = MutableStateFlow(0)

    /** Tone pad, vertical: scooped at -5, mid-forward at +5. */
    val equalizerToneY = MutableStateFlow(0)

    /** Tone pad bandwidth: Broad when false, Focused when true. */
    val equalizerFocused = MutableStateFlow(false)

    /** Left/right trim, -1 hard left to +1 hard right. Applies to both tabs. */
    val equalizerBalance = MutableStateFlow(0f)

    /** The manual tab's seven gains, in decibels, low to high. */
    val equalizerBands = MutableStateFlow(EqualizerPreset.FLAT.bands)

    /**
     * Which preset the bands currently are, or [EqualizerPreset.CUSTOM].
     *
     * Derived from [equalizerBands] rather than independent of it — see
     * [setEqualizerBands] — so a slider dragged back to where a preset left it
     * makes the row say that preset's name again instead of "Custom" forever.
     */
    val equalizerPreset = MutableStateFlow(EqualizerPreset.FLAT)

    /**
     * Laju efektif yang dibaca mesin pemutar (= [playbackRate] x [playbackTempo]).
     * Jangan diubah langsung dari UI: pakai [setPlaybackRate] / [setPlaybackTempo].
     */
    val playbackSpeed = MutableStateFlow(1.0f)

    /** Speed: laju DAN pitch berubah bersama (seperti piringan hitam). 1.0 = normal. */
    val playbackRate = MutableStateFlow(1.0f)

    /** Tempo: hanya laju yang berubah, pitch tetap. 1.0 = normal. */
    val playbackTempo = MutableStateFlow(1.0f)
    val themeMode = MutableStateFlow(ThemeMode.DARK)
    val appFont = MutableStateFlow(AppFont.DEFAULT)
    val accentColor = MutableStateFlow(AccentColor.RED)
    val selectedThemePackId = MutableStateFlow(ThemePacks.CLASSIC_ID)
    val customThemePacks = MutableStateFlow<List<ThemePack>>(emptyList())
    val themeBackgroundUri = MutableStateFlow<String?>(null)
    val backgroundStyle = MutableStateFlow(AppBackgroundStyle.PLAIN)
    val amoledDisplayColors = MutableStateFlow(false)

    /** Keep playing similar music once the queue runs out. */
    val autoplay = MutableStateFlow(true)

    /** Whether the queue is held in shuffled order (Mix button). */
    val shuffleEnabled = MutableStateFlow(false)

    /** Repeat mode for the player — Off, All, or One. */
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)

    /** Put the playing track's codec, bitrate and sample rate on the player. */
    val showNerdStats = MutableStateFlow(false)

    /** Show the output / Listen Together pill under the player's transport. */
    val showListenTogetherButton = MutableStateFlow(false)

    /** Show a Listen Together tab in the bottom navigation bar when explicitly enabled. */
    val showListenTogetherTab = MutableStateFlow(false)

    /** Freezes the main player's mesh gradient instead of letting it drift/crossfade. */
    val reduceAnimation = MutableStateFlow(false)

    /** Cover pemutar bergeser/menyapu ke lagu baru saat ganti lagu; mati = cover langsung berganti. */
    val coverSlideAnimation = MutableStateFlow(true)

    /** Efek cover "cair" (morph shader AGSL) menggantikan sapuan geser; hanya aktif di API 33+ dan bila [coverSlideAnimation] hidup. */
    val coverFluidAnimation = MutableStateFlow(false)

    /**
     * Efek penuh (cair/geser) hanya menyala saat Automix atau crossfade berjalan.
     * Ganti lagu manual (next/prev) memakai transisi ringan. Hanya bermakna bila
     * [coverSlideAnimation] hidup.
     */
    val coverMixOnlyEffects = MutableStateFlow(false)

    /**
     * Whether the welcome pages (party name, then YouTube sign-in) have been
     * through. Absent rather than true for anyone updating from a build that
     * had no onboarding, so they get it once too — they have no party name yet.
     */
    val onboardingComplete = MutableStateFlow(false)

    /** Requests a sustained high-refresh UI. Off keeps Android's automatic policy. */
    val highPerformanceMode = MutableStateFlow(false)

    /** Preferred UI refresh rate while [highPerformanceMode] is enabled. */
    val performanceRefreshRate = MutableStateFlow(DEFAULT_PERFORMANCE_REFRESH_RATE)

    /** Stop playback when the app is swiped away from the recent apps screen. */
    val stopOnTaskRemoved = MutableStateFlow(true)

    /** Hides the volume slider on the main player, leaving the rest of the layout to reflow. */
    val hideVolumeBar = MutableStateFlow(false)

    /** Hides the device status bar (clock, battery, notifications) while the main player is open. */
    val hideStatusBarInPlayer = MutableStateFlow(false)

    /** Hides the "Playing from" / "Played by" caption at the top of the main player. */
    val hideSongStatus = MutableStateFlow(false)

    val showQueueHistory = MutableStateFlow(false)
    val hapticFeedbackEnabled = MutableStateFlow(true)

    /** Swiping a song row plays it next instead of adding it to the end of the queue. */
    val swipeToPlayNext = MutableStateFlow(false)

    /** Once a song has been suggested or played this session, AutoPlay won't offer it again. */
    val dontRepeatSuggestions = MutableStateFlow(false)

    /** Analyzes audio waveform/envelope to align matching playback moment between versions. */
    val smartVersionAlignment = MutableStateFlow(true)

    /** Music video diputar sebagai versi audionya — lihat [com.music.audify.playback.MusicOnlyPlayback]. */
    val autoMusicOnly = MutableStateFlow(true)

    /** Removes the Recents row from Home. */
    val hideHomeRecents = MutableStateFlow(false)

    /** Replaces the Home logo and wordmark with a plain "Home" title. */
    val hideHomeBranding = MutableStateFlow(false)

    /** Removes music videos from Home, plus any row left with nothing else in it. */
    val hideHomeVideos = MutableStateFlow(false)

    /** Removes the Top Picks for You row from Home. */
    val hideHomeTopPicks = MutableStateFlow(false)

    /** Removes the mood row from Home. The Moods page in Explore stays. */
    val hideHomeMoods = MutableStateFlow(false)

    /** Shows every music video as a square, see [VideoThumbnails]. */
    val squareVideoCovers = MutableStateFlow(false)

    /** Never plays the music video in the player: a video track shows its cover instead. */
    val stillVideoCovers = MutableStateFlow(false)

    /** The listener's own order and hidden rows on Home. Empty keeps the built-in order. */
    val homeLayout = MutableStateFlow(HomeLayout())

    /** The Artists shelf in Library: false is the two-across grid, true is one row that scrolls sideways. */
    val libraryArtistsRow = MutableStateFlow(false)

    /** The listener's own order and hidden rows on the Library tab. Empty keeps the built-in order. */
    val libraryLayout = MutableStateFlow(HomeLayout())

    /** Drops haze blur (status bar, mini player, bottom fade, lyrics focus) for a solid-fill look. */
    val reduceDynamicBlur = MutableStateFlow(false)

    /** Real backdrop-sampled glass (blur, lens refraction) on the floating nav bar, Android 12+ only. */
    val liquidGlass = MutableStateFlow(false)

    /**
     * Menurunkan Liquid Glass menjadi frosted glass: tetap blur + tint + rim,
     * tetapi tanpa lensa refraksi, chromatic aberration, dan shadow. Jauh lebih
     * ringan di HP spek rendah. Hanya berlaku saat [liquidGlass] aktif.
     */
    val frostedGlass = MutableStateFlow(false)

    /** Kejernihan Liquid Glass sebagai slider (Clear ... Balanced ... Frosted). Hanya berlaku saat [liquidGlass] aktif. */
    val glassClarity = MutableStateFlow(GlassClarity(GlassPreset.DEFAULT.level))

    /** Pengaturan manual tiap efek Liquid Glass (blur, refraksi, pelangi, tepi, bayangan, kualitas). */
    val glassCustom = MutableStateFlow(GlassPreset.DEFAULT.custom)

    /** Pengaturan indikator pilihan pada tab bar (lengkung saat diam, refraksi, tepi, bayangan, gerak). */
    val glassIndicator = MutableStateFlow(GlassPreset.DEFAULT.indicator)

    /** Progressive blur at the top and bottom scroll edges. Independent of Liquid Glass. */
    // New installs start with the GPU-expensive edge blur disabled. Existing
    // installations retain their explicitly saved preference during upgrades.
    val progressiveBlur = MutableStateFlow(false)

    /** Blurs unfocused lyric lines, keeping the active line sharp. */
    val lyricsBlur = MutableStateFlow(true)

    /**
     * Efek lirik LiquidFlow (sapuan kata). Nyala: efek Motion dipakai. Mati:
     * efek lirik bawaan. Tidak dipengaruhi oleh Reduce animation.
     */
    val liquidFlow = MutableStateFlow(true)

    /**
     * Partikel berkilau di sekitar huruf yang ditahan pada efek LiquidFlow.
     * Hanya berlaku saat [liquidFlow] nyala.
     */
    val lyricsSparkle = MutableStateFlow(true)

    /**
     * Positive values delay synced lyrics; negative values bring them forward.
     * This is the offset of the track now playing, saved for that track alone;
     * [setLyricsOffsetTrack] points it at whichever track the player is on.
     */
    val lyricsOffsetMs = MutableStateFlow(0)
    private var lyricsOffsetByTrack: Map<String, Int> = emptyMap()
    private var lyricsOffsetTrackId: String? = null
    /** Playback-time multiplier used only by the lyrics clock, saved per track. */
    val lyricsSpeed = MutableStateFlow(1f)
    private var lyricsSpeedByTrack: Map<String, Float> = emptyMap()

    /** Automatic intro/middle-section skips saved independently for each track. */
    val skipSegments = MutableStateFlow<List<SkipSegment>>(emptyList())
    private var skipSegmentsByTrack: Map<String, List<SkipSegment>> = emptyMap()
    private var skipSegmentsTrackId: String? = null
    val trackPlaybackWindow = MutableStateFlow<TrackPlaybackWindow?>(null)
    private var playbackWindowsByTrack: Map<String, TrackPlaybackWindow> = emptyMap()
    private var playbackWindowTrackId: String? = null

    /**
     * Which language the lyrics translate button translates *into*.
     *
     * Blank — the default — means "whatever the app is set to", and is stored
     * as blank rather than resolved once: someone who has never touched this
     * has expressed no preference, and switching the app to Spanish should
     * carry their lyrics with it rather than leaving them on the English they
     * happened to be reading the day the setting was written.
     */
    val translationLanguage = MutableStateFlow("")

    /**
     * Plays a looping video behind the cover art on the player when one is
     * published for the track — Spotify's Canvas, Apple's motion artwork.
     *
     * Costs a video stream on top of the audio one and reaches three
     * services that have nothing to do with playback, so it stays a switch —
     * but it is the better default, and most tracks resolve to no canvas at
     * all. See [CanvasRepository][com.music.audify.data.canvas.CanvasRepository].
     */
    val animatedCanvas = MutableStateFlow(true)

    /**
     * Whether [animatedCanvas] is allowed to actually stream on a metered
     * connection, as distinct from the switch that turns the feature off
     * altogether.
     *
     * Off by default. A canvas clip loops for as long as its track plays,
     * and every loop past the first re-fetches the same few seconds of video
     * — see [CanvasCache][com.music.audify.data.canvas.CanvasCache] for why
     * that costs network at all rather than being answered from a buffer —
     * so a few-second clip behind a four-minute track on cellular is not a
     * flat video cost, it is that cost repeated dozens of times per song.
     * That is the shape of the reported 8GB day: still art costs nothing
     * here and stays up regardless of this setting.
     */
    val canvasOverCellular = MutableStateFlow(false)

    /** Automatically collapses the lower controls after Spotify Canvas settles. */
    val spotifyCanvasAutoHide = MutableStateFlow(true)

    /** Tries Spotify before Apple Music and the other animated-art providers. */
    val prioritizeSpotifyCanvas = MutableStateFlow(false)

    /**
     * Secondary artist art: a user-hosted artist-signature API used only when
     * Apple Music has no title logo. Blank URL means unconfigured and the
     * lookup is skipped entirely — there is no hosted default.
     */
    val signatureApiEnabled = MutableStateFlow(false)
    val signatureApiUrl = MutableStateFlow("")

    /**
     * Blows the player's cover art out to a full-bleed banner running off the
     * top of the screen, rather than sitting it in a square card.
     *
     * The treatment motion artwork has always had, applied to still sleeves too.
     * Off restores the card: the sleeve keeps its corners, its shadow and its
     * shrink-while-paused, and only a clip goes full-bleed. Phones only either
     * way — see the hero notes in
     * [NowPlayingScreen][com.music.audify.ui.player.NowPlayingScreen].
     */
    val fullBleedArtwork = MutableStateFlow(true)

    /**
     * Puts v1.5's backdrop back on the player: four quantised blobs drifting
     * behind the whole screen, rather than the artwork's own colours hung off
     * the sleeve's bottom edge.
     *
     * Off by default, because the current backdrop replaced it for two reasons
     * that have not gone away — see [ArtworkMesh][com.music.audify.ui.player.ArtworkMesh]
     * for the colour one (a cover that is nine-tenths black with a red stripe
     * comes back from the quantiser as a red screen) and
     * [ArtworkMeshBackdrop][com.music.audify.ui.player.ArtworkMeshBackdrop]
     * for the cost one (blobs that drift are a full-screen blur redrawn while
     * they move, where a mesh is drawn once per track and then composited).
     * Kept as a switch because people asked for the old look back, and neither
     * reason is one a listener has to agree with.
     */
    val legacyMeshGradient = MutableStateFlow(false)

    /** Restores the expanded player to the surface the listener left open. */
    val lastPlayerScreen = MutableStateFlow(LastPlayerScreen.MAIN)

    /**
     * Time-synced lyrics on the player, lit up as they are sung.
     *
     * On by default — it is most of the point of the player screen — but it
     * reaches third-party lyric databases for every track played, so it stays
     * a switch, and [lyricsSources] narrows which of them get asked.
     */
    val syncedLyrics = MutableStateFlow(true)

    /** Baris lirik aktif di bawah judul dan artis pada pemutar utama. */
    val lyricStrip = MutableStateFlow(true)

    /** The databases [syncedLyrics] may ask. Empty is the same as off. */
    val lyricsSources = MutableStateFlow(LyricsSource.offered.toSet())

    /**
     * The order [lyricsSources] are asked in — see [LyricsRepository][com.music.audify.data.lyrics.LyricsRepository]:
     * every enabled source is asked at once, but a higher-priority one still
     * pending is never preempted by a lower one that happened to answer first.
     * Reordered from Settings, so this is a full permutation of
     * [LyricsSource.offered] rather than a subset — enabling and ordering are
     * independent choices.
     */
    val lyricsSourceOrder = MutableStateFlow<List<LyricsSource>>(LyricsSource.offered)

    /**
     * Off, the highest-priority source to answer at all is taken as the
     * lyrics, word-synced or not. On, a merely line-synced answer is held as
     * a fallback while the rest of [lyricsSourceOrder] is still checked for a
     * word-synced one — worth the extra network calls to some, not to others,
     * which is why it defaults off rather than being how [LyricsRepository]
     * always behaved.
     */
    val prioritizeSyllableSync = MutableStateFlow(false)

    /** User-issued credential required by api.paxsenix.org. */
    val paxSenixApiKey = MutableStateFlow("")

    /**
     * Disk budget for cached audio. [AudioCache][com.music.audify.playback.AudioCache] evicts past it.
     * [UNLIMITED_CACHE_LIMIT_BYTES] means no ceiling of the app's own.
     */
    val audioCacheLimitBytes = MutableStateFlow(DEFAULT_CACHE_LIMIT_BYTES)

    /**
     * Disk budget for animated cover (canvas) clips, separate from the song
     * cache. [CanvasCache][com.music.audify.data.canvas.CanvasCache] evicts
     * the least recently used clips past it.
     */
    val canvasCacheLimitBytes = MutableStateFlow(DEFAULT_CANVAS_CACHE_LIMIT_BYTES)

    /**
     * Whether Library's "On device" shelf carries the Cached songs folder —
     * the YouTube and JioSaavn tracks the song cache is holding. Off by
     * default: the cache is an implementation detail most people never need
     * to look inside.
     */
    val showCacheFolder = MutableStateFlow(false)

    // ── Replay ──────────────────────────────────────────────────────────────

    /**
     * Whether Replay may work out a genre chart.
     *
     * Its own switch because it is the one part of Replay that isn't purely
     * local: everything else on that page is counted on this device and never
     * leaves it, while a genre has to be looked up by artist name — see
     * [ArtistFacts][com.music.audify.data.stats.ArtistFacts]. On by default,
     * since it sends a name and nothing else and the answer is what makes a
     * quarter of the page exist; off, the genre chart simply isn't drawn.
     */
    val replayGenres = MutableStateFlow(true)
    /** Show the private weekly listening recap on Home. */
    val showWeeklyRecapCard = MutableStateFlow(true)
    /** Show discovery shelves adapted to the listener local time. */
    val showTimeAwareShelf = MutableStateFlow(true)
    /** Monday epoch-day of the last recap opened or dismissed. */
    val weeklyRecapSeenWeek = MutableStateFlow(0L)

    // ── Library ─────────────────────────────────────────────────────────────

    /** Hides short clips, recorder output and non-music formats from Local Music. */
    val filterNonMusicAudio = MutableStateFlow(true)
    /** Hide catalogue entries positively marked explicit; unknown entries remain visible. */
    val hideExplicitContent = MutableStateFlow(false)

    val localMusicSort = MutableStateFlow(LocalMusicSort.TITLE_ASC)
    val downloadedMusicSort = MutableStateFlow(LocalMusicSort.TITLE_ASC)
    val localMusicViewType = MutableStateFlow(LibraryViewType.LIST)
    val downloadedMusicViewType = MutableStateFlow(LibraryViewType.LIST)
    /** Layout used by the Recents shelf on Play; compact tracks are the default. */
    val librarySort = MutableStateFlow(LibrarySort.DEFAULT)

    /**
     * Each album/playlist page's track-list order, keyed by browse id —
     * Spotify-style, every page keeps its own. A page never touched reads as
     * [SongSort.DEFAULT].
     */
    val detailSongSorts = MutableStateFlow<Map<String, SongSort>>(emptyMap())

    /** Empty means every MediaStore folder; otherwise this is a persisted SAF tree URI. */
    val localMusicFolderUri = MutableStateFlow("")

    /** Empty means the default destination; otherwise a persisted SAF tree URI downloads are written into. */
    val downloadFolderUri = MutableStateFlow("")

    // ── WebDAV ────────────────────────────────────────────────────────────

    /**
     * Remote music library over WebDAV (e.g. Nextcloud's Music folder).
     *
     * The URL and username live in plain prefs like every other setting; the
     * password is mirrored out of [AuthStore] so it stays encrypted at rest
     * and out of backup exports — see [exportPrefs]. Empty URL means
     * unconfigured, and the library simply reads as empty.
     */
    val webdavUrl = MutableStateFlow("")
    val webdavUsername = MutableStateFlow("")
    val webdavPassword = MutableStateFlow("")

    // ── SMB ───────────────────────────────────────────────────────────────

    /**
     * Remote music library on an SMB file share (a NAS, a Windows box).
     *
     * Stored like the WebDAV settings: host, share, base folder and username
     * in plain prefs, the password mirrored out of [AuthStore] so it stays
     * encrypted at rest and out of backup exports. Empty host or share means
     * unconfigured, and the library simply reads as empty.
     */
    val smbHost = MutableStateFlow("")
    val smbShare = MutableStateFlow("")
    val smbBasePath = MutableStateFlow("")
    val smbUsername = MutableStateFlow("")
    val smbPassword = MutableStateFlow("")

    /**
     * Browse ids of the playlists pinned to the top of the Library tab, in the
     * order they were pinned.
     *
     * A [List] rather than a [Set]: pin order is part of what a pin means here —
     * the whole point is a small, hand-picked front row, and a set would leave
     * that order to hash iteration. Capped at [MAX_PINNED_PLAYLISTS] by
     * [togglePinnedPlaylist], the only way this is ever written.
     */
    val pinnedPlaylists = MutableStateFlow<List<String>>(emptyList())

    /** How many playlists [pinnedPlaylists] can hold at once. */
    const val MAX_PINNED_PLAYLISTS = 6

    /**
     * What a pinned playlist's Library card is drawn from when no Library shelf
     * holds that playlist — a Spotify account's, which lives on its own page.
     */
    data class PinnedCard(val title: String, val subtitle: String, val thumbnailUrl: String?)

    /** The [PinnedCard] of each pin that needs one, keyed by browse id. */
    val pinnedCards = MutableStateFlow<Map<String, PinnedCard>>(emptyMap())

    // ── Scrobbling ──────────────────────────────────────────────────────

    /** One release gate shared by the settings UI and the playback service. */
    val scrobblingAvailable = true

    val lastfmEnabled = MutableStateFlow(false)
    val lastfmUsername = MutableStateFlow("")
    val lastfmSessionKey = MutableStateFlow("")
    val lastfmApiKey = MutableStateFlow("")
    val lastfmSecret = MutableStateFlow("")
    val lastfmEndpoint = MutableStateFlow("")
    val lastfmScrobbleEnabled = MutableStateFlow(false)
    val lastfmNowPlaying = MutableStateFlow(false)
    val lastfmPrimaryArtistOnly = MutableStateFlow(false)
    val scrobbleMinDuration = MutableStateFlow(30)
    val scrobbleDelayPercent = MutableStateFlow(0.5f)
    val scrobbleDelaySeconds = MutableStateFlow(180)
    val listenBrainzEnabled = MutableStateFlow(false)
    val listenBrainzToken = MutableStateFlow("")
    val listenBrainzPrimaryArtistOnly = MutableStateFlow(false)
    val spotifySpdcToken = MutableStateFlow("")

    // ── Discord Rich Presence ───────────────────────────────────────────

    /**
     * Nama dan foto profil buatan pengguna, hanya berlaku di aplikasi ini dan
     * menggantikan milik Google. [owner] mengikat override ke satu akun
     * (lihat [profileOwnerOf]) supaya tidak muncul di akun lain.
     */
    data class ProfileOverride(val owner: String = "", val name: String = "", val photoPath: String = "")

    val profileOverride = MutableStateFlow(ProfileOverride())

    fun profileOwnerOf(email: String?): String = email.orEmpty().ifBlank { "default" }

    // Fresh-install default is seeded explicitly; legacy installs retain their saved/default state.
    val discordRpcEnabled = MutableStateFlow(false)

    /** Put the track title on the bold profile line, in place of the artist. */
    val discordUseDetails = MutableStateFlow(false)

    /** Show measured Hi-Res, Lossless, or Dolby specs on the presence card. */
    val discordShowAudioQuality = MutableStateFlow(true)

    val discordShowTrackTitle = MutableStateFlow(true)
    val discordShowArtist = MutableStateFlow(true)
    val discordShowAlbum = MutableStateFlow(false)

    /** Include the current track artwork in the published activity. */
    val discordShowArtwork = MutableStateFlow(true)

    /** Include elapsed/remaining playback timestamps in the published activity. */
    val discordShowElapsedTime = MutableStateFlow(true)

    /** Keep the last activity visible while playback is paused. */
    val discordHideWhenPaused = MutableStateFlow(false)

    /** Reveals the presence-shape controls: status, activity type/name, buttons. */
    val discordAdvancedMode = MutableStateFlow(false)

    val discordActivityType = MutableStateFlow("listening")

    val discordButton1Text = MutableStateFlow("")
    val discordButton1Visible = MutableStateFlow(true)
    val discordButton2Text = MutableStateFlow("")
    val discordButton2Visible = MutableStateFlow(true)


    /** Published by PlaybackService so the UI can open the system equalizer. */
    val audioSessionId = MutableStateFlow(0)

    /**
     * True only while a Automix transition that is actually *mixing* is
     * audible — one that beat-matched, cued the incoming track into its
     * arrangement, or rode a filter.
     *
     * Deliberately not "a crossfade is running". The fallback case, where
     * neither track was analysed in time and the incoming one starts from 0:00
     * under a plain equal-power fade, is exactly what this must stay dark for:
     * the whole point is that seeing it means the analysis landed and did
     * something a plain crossfade could not.
     */
    val smartMixInProgress = MutableStateFlow(false)

    /**
     * True selama sebuah transisi berjalan, termasuk crossfade biasa yang
     * sengaja tidak menyalakan [smartMixInProgress]. Dipakai UI untuk
     * memadamkan clip animasi cover sebelum cover berganti.
     */
    val crossfadeRunning = MutableStateFlow(false)

    /**
     * The Automix blend in flight — its progress and the beat it runs on — or
     * null between blends. Published by the crossfade controller every fade
     * tick; read it in draw, not in composition.
     */
    val smartMixBlend = MutableStateFlow<MixBlend?>(null)

    /** The Automix blend in flight laid out for the mix popup, or null between blends. */
    val smartMixView = MutableStateFlow<MixTransitionView?>(null)

    /** How far through [smartMixView]'s blend playback is, 0..1. Read it in draw. */
    val smartMixProgress = MutableStateFlow(0f)

    /**
     * True while a version switch is fetching and analysing the other cut
     * before playback actually moves. Drains into the loading bar drawn along
     * the scrubber itself — `ThinSlider.loading` — so the wait reads as work
     * in progress rather than as a player frozen on a version that is about to
     * change, and lights the toggle button's spinner through the half of the
     * switch that has nothing else showing.
     */
    val versionAlignmentInProgress = MutableStateFlow(false)

    /**
     * How much of the *upcoming* transition has been analysed, for stats for
     * nerds. Published by the crossfade controller, which is the only thing
     * that knows which two tracks the next transition is between.
     */
    val smartAnalysis = MutableStateFlow(SmartAnalysis())

    /**
     * Automix's detailed line for stats for nerds: each song's analysis while
     * it waits, then the planned mix (a loop mix's loops and tempo ramp, a
     * blend's length and stretch, or why the pair isn't beatmatched). Published
     * by the crossfade controller; null while Automix is off.
     */
    val automixDetail = MutableStateFlow<String?>(null)

    /**
     * Where on the *playing* track the next transition is planned to happen, as
     * fractions of its duration, or null when there is nothing worth drawing.
     *
     * Only published once both tracks are measured. Before that the planner is
     * still working from a fallback window that moves as evidence arrives, and
     * a marker that slides around the bar would be worse than no marker.
     */
    val smartTransitionWindow = MutableStateFlow<TransitionWindow?>(null)

    /**
     * A temporary cap on [effectiveAudioQuality], for as long as something other
     * than this phone's own speaker is playing — the Audify watch app, whose
     * audio goes over a Bluetooth link that cannot keep up with lossless. Never
     * persisted, never shown: the listener's own settings are untouched, and
     * apply again the moment the phone takes the music back.
     */
    val streamCeilingOverride = MutableStateFlow<AudioQuality?>(null)

    /** The ceiling that applies to a stream started right now. */
    val effectiveAudioQuality: AudioQuality
        get() = ceilingFor(
            wifi = audioQualityWifi.value,
            cellular = audioQualityCellular.value,
            metered = meteredConnection.value,
            override = streamCeilingOverride.value,
        )

    /**
     * [effectiveAudioQuality] from values a caller already holds — a screen
     * that collected the four flows so it recomposes when any of them moves.
     */
    fun ceilingFor(
        wifi: AudioQuality,
        cellular: AudioQuality,
        metered: Boolean?,
        override: AudioQuality?,
    ): AudioQuality {
        val chosen = if (metered == true) cellular else wifi
        val cap = override ?: return chosen
        return if (chosen.ordinal > cap.ordinal) cap else chosen
    }

    /**
     * Whether a download may start on the connection in hand.
     *
     * A null [meteredConnection] means there is no active network, and that is
     * deliberately allowed through: a download with nothing to download over
     * fails on the network and says so, which is true, where refusing it here
     * would blame a Wi-Fi setting for an outage.
     */
    val downloadsAllowedNow: Boolean
        get() = !wifiOnlyDownloads.value || meteredConnection.value != true

    /**
     * [authStore] is the application's own, passed in rather than opened again:
     * each open of the encrypted store is a keystore round trip, and a second
     * one here was a measurable slice of cold start.
     */
    fun init(context: Context, authStore: AuthStore) {
        val currentPrefs = context.getSharedPreferences("audify_settings", Context.MODE_PRIVATE)
        migrateAudifyPreferences(context, currentPrefs)
        prefs = currentPrefs
        wallpaperPrefs = context.getSharedPreferences("audify_wallpaper", Context.MODE_PRIVATE)
        // Move any pre-existing wallpaper URI out of the general settings file.
        // The dedicated preferences file is excluded from Android backup/transfer.
        if (!wallpaperPrefs.contains(KEY_THEME_BACKGROUND_URI) && currentPrefs.contains(KEY_THEME_BACKGROUND_URI)) {
            wallpaperPrefs.edit { putString(KEY_THEME_BACKGROUND_URI, currentPrefs.getString(KEY_THEME_BACKGROUND_URI, null)) }
        }
        currentPrefs.edit { remove(KEY_THEME_BACKGROUND_URI) }
        seedFirstInstallDefaults(context)
        this.authStore = authStore
        // Publish the complete settings snapshot before stores and schedulers
        // consult their switches. Migration apply() has already updated the
        // in-memory preference map atomically, so this read sees the full copy.
        readAll()
        startupIoScope.launch {
            runCatching {
                ThemeBackgroundStorage.removeOrphans(
                    java.io.File(context.noBackupFilesDir, "theme-backgrounds"),
                    themeBackgroundUri.value,
                )
            }.onFailure { android.util.Log.w("Audify", "Wallpaper orphan cleanup failed", it) }
        }
        com.music.audify.data.spotify.LocalPlaylistStore.init(context)
        com.music.audify.data.playlist.SmartPlaylistStore.init(context)
        com.music.audify.data.stats.LocalLibraryStore.init(context)
        startupIoScope.launch {
            runCatching { com.music.audify.data.stats.AutomaticBackupScheduler.schedule(context) }
                .onFailure { android.util.Log.e("Audify", "Automatic backup scheduling failed", it) }
        }
        com.music.audify.data.OfflinePlaylists.init(
            context,
            likedTitle = context.getString(com.music.audify.R.string.auto_liked),
        )
        watchConnection(context)
    }

    /**
     * The migration changed the SharedPreferences file name. Merge the old
     * Audify file into the new store before defaults are seeded or settings
     * are read. Existing values in the new store always win; the old file is
     * deliberately retained as a rollback-safe copy.
     */
    private fun migrateAudifyPreferences(context: Context, target: SharedPreferences) {
        val legacy = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        val legacyValues = legacy.all
        val completeExistingOnboarding = shouldCompleteOnboardingForExistingInstall(
            currentPreferenceKeys = target.all.keys,
            legacyPreferenceKeys = legacyValues.keys,
            onboardingKey = KEY_ONBOARDING_COMPLETE,
            migrationKey = KEY_AUDIFY_PREFS_MIGRATED,
        )
        if (target.getBoolean(KEY_AUDIFY_PREFS_MIGRATED, false)) {
            if (completeExistingOnboarding) target.edit { putBoolean(KEY_ONBOARDING_COMPLETE, true) }
            return
        }
        // A clean install has neither a target preference nor legacy values.
        // Do not write the migration marker in that case: seedFirstInstallDefaults()
        // must see an empty file and populate the complete first-run defaults.
        if (target.all.isEmpty() && legacyValues.isEmpty()) return

        // SharedPreferences.apply() updates the in-memory map atomically before
        // returning and persists it on the framework's disk writer. readAll()
        // therefore sees the complete migration without waiting for an fsync on
        // the main thread. The legacy file remains intact as a rollback copy.
        target.edit {
            fun put(key: String, value: Any?) {
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }

            // Keep every original key, including keys whose UI is being restored
            // in follow-up work. This prevents a migration from silently discarding
            // preferences that the current version does not yet read.
            val keysToImport = legacyPreferenceKeysToImport(target.all.keys, legacyValues.keys)
            keysToImport.forEach { key -> put(key, legacyValues[key]) }

            // Keys whose names changed during the copied-project integration.
            val renamedKeys = mapOf(
                "font_choice" to "app_font",
                "prefer_music_only" to "auto_music_only",
                "home_layout_v1" to "home_layout",
                "lyrics_liquid_flow" to "liquid_flow_motion",
                "lyrics_offset_ms" to "lyrics_offset_by_track",
                "progressive_scroll_edge_blur" to "progressive_blur",
                "lightweight_liquid_glass" to "frosted_glass",
            )
            renamedKeys.forEach { (oldKey, newKey) ->
                if (!target.contains(newKey) && oldKey in legacyValues) {
                    put(newKey, legacyValues[oldKey])
                }
            }

            putBoolean(KEY_AUDIFY_PREFS_MIGRATED, true)
        }
        if (completeExistingOnboarding) target.edit { putBoolean(KEY_ONBOARDING_COMPLETE, true) }
    }

    /**
     * Re-reads every setting off disk.
     *
     * The one caller is an import ([Backup][com.music.audify.data.stats.Backup]),
     * which writes the whole preference file underneath these flows. Nothing
     * else in the app changes a preference without going through the setter
     * beside it, so nothing else has a reason to ask.
     *
     * Deliberately not re-registering the network callback: that watches the
     * device, not the preferences, and a second one would have both firing.
     */
    fun reload() {
        if (!this::prefs.isInitialized) return
        readAll()
    }

    /**
     * First-run defaults for a genuinely empty preference store. Existing installs
     * and restored backups are never seeded or overwritten. readAll() deliberately
     * retains legacy fallbacks for keys absent from an older install's store.
     */
    private fun seedFirstInstallDefaults(context: Context) {
        if (prefs.all.isNotEmpty() && !shouldSeedFirstInstallDefaults(prefs.all.keys, KEY_AUDIFY_PREFS_MIGRATED)) return
        val lowRam = runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        }.getOrDefault(false)
        // HP kuat: cukup core dan RAM untuk analisis Automix di latar belakang.
        val capable = !lowRam && Runtime.getRuntime().availableProcessors() >= 6
        // Cache 1 GB hanya jika penyimpanan lega; selain itu pakai batas bawaan.
        val roomy = runCatching {
            context.getSystemService(StorageManager::class.java)
                ?.getAllocatableBytes(StorageManager.UUID_DEFAULT)
                ?.let { it >= 4L * 1024 * 1024 * 1024 }
                ?: false
        }.getOrDefault(false)
        val language = java.util.Locale.getDefault().language
            .let { if (it == "in") "id" else it }
            .takeIf { it.length in 2..3 }
            .orEmpty()
        prefs.edit {
                putInt(KEY_CROSSFADE, 3)
                .putBoolean(KEY_SMART_FADE, true)
                .putString(KEY_AUTOMIX_PERFORMANCE_MODE, if (capable) "PERFORMANCE" else "BALANCED")
                .putBoolean(KEY_SKIP_SILENCE, false)
                .putString(KEY_OUTPUT_PCM_MODE, "PCM_16")
                .putBoolean(KEY_DIRECT_SOURCE_STREAM, true)
                .putBoolean(KEY_SPATIAL_AUDIO, true)
                .putString(KEY_QUALITY_WIFI, "LOSSLESS")
                .putString(KEY_QUALITY_CELLULAR, "LOSSLESS")
                .putBoolean(KEY_WIFI_ONLY_DOWNLOADS, true)
                .putBoolean(KEY_EXPORT_DOWNLOADS, true)
                .putBoolean(KEY_SYNC_DOWNLOADED_PLAYLISTS, true)
                .putBoolean(KEY_STOP_ON_TASK_REMOVED, false)
                .putBoolean(KEY_HIDE_VOLUME_BAR, false)
                .putBoolean(KEY_HIDE_SONG_STATUS, true)
                .putBoolean(KEY_SHOW_QUEUE_HISTORY, false)
                .putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)
                .putBoolean(KEY_SWIPE_TO_PLAY_NEXT, false)
                .putBoolean(KEY_DONT_REPEAT_SUGGESTIONS, true)
                .putBoolean(KEY_AUTO_MUSIC_ONLY, true)
                .putBoolean(KEY_HIDE_HOME_RECENTS, false)
                .putBoolean(KEY_HIDE_HOME_BRANDING, false)
                .putBoolean(KEY_HIDE_HOME_VIDEOS, false)
                .putBoolean(KEY_HIDE_HOME_TOP_PICKS, false)
                .putBoolean(KEY_HIDE_HOME_MOODS, false)
                .putBoolean(KEY_REPLAY_GENRES, true)
                .putBoolean(KEY_SHOW_WEEKLY_RECAP_CARD, true)
                .putBoolean(KEY_SHOW_TIME_AWARE_SHELF, true)
                .putBoolean(KEY_FILTER_NON_MUSIC_AUDIO, true)
                .putBoolean(KEY_HIDE_EXPLICIT_CONTENT, false)
                .putBoolean(KEY_NERD_STATS, false)
                .putBoolean(KEY_LISTEN_TOGETHER_BUTTON, false)
                .putBoolean(KEY_LISTEN_TOGETHER_TAB, false)
                .putBoolean(KEY_SIGNATURE_API_ENABLED, false)
                .putBoolean(KEY_SHOW_CACHE_FOLDER, false)
                .putBoolean(KEY_PRIORITIZE_SYLLABLE_SYNC, false)
                .putBoolean(KEY_DISCORD_RPC_ENABLED, true)
                .putBoolean(KEY_LIQUID_GLASS, false)
                .putBoolean(KEY_FULL_BLEED_ARTWORK, true)
                .putBoolean(KEY_COVER_SLIDE_ANIMATION, true)
                .putBoolean(KEY_COVER_FLUID_ANIMATION, false)
                .putBoolean(KEY_COVER_MIX_ONLY_EFFECTS, false)
                .putString(KEY_LOCAL_MUSIC_SORT, "DATE_ADDED")
                .putLong(KEY_CACHE_LIMIT, if (roomy) 1024L * 1024 * 1024 else DEFAULT_CACHE_LIMIT_BYTES)
                .putLong(KEY_CANVAS_CACHE_LIMIT, if (roomy) 1024L * 1024 * 1024 else DEFAULT_CANVAS_CACHE_LIMIT_BYTES)
                .putString(
                    KEY_LYRICS_SOURCE_ORDER,
                    "BETTER_LYRICS,BETTER_LYRICS_PORTATO,LRC_RED,BINI_LYRICS,PAXSENIX,PAXSENIX_SPOTIFY,PAXSENIX_MUSIXMATCH,LYRICS_PLUS,UNISON,YOUTUBE_TRANSCRIPT,YOUTUBE_MUSIC,KUGOU,LRCLIB,MUSIXMATCH,GENIUS",
                )
                .putString(
                    KEY_LYRICS_SOURCES,
                    "LRC_RED,BINI_LYRICS,BETTER_LYRICS,PAXSENIX,PAXSENIX_SPOTIFY,PAXSENIX_MUSIXMATCH,LYRICS_PLUS,UNISON,YOUTUBE_TRANSCRIPT,YOUTUBE_MUSIC,KUGOU,LRCLIB,MUSIXMATCH,GENIUS,BETTER_LYRICS_PORTATO",
                )
                .putString(
                    KEY_LYRICS_SOURCES_SEEN,
                    "LRC_RED,BINI_LYRICS,BETTER_LYRICS,BETTER_LYRICS_PORTATO,PAXSENIX,PAXSENIX_SPOTIFY,PAXSENIX_MUSIXMATCH,LYRICS_PLUS,UNISON,YOUTUBE_TRANSCRIPT,YOUTUBE_MUSIC,KUGOU,LRCLIB,MUSIXMATCH,GENIUS",
                )
                .putString(KEY_TRANSLATION_LANGUAGE, language)
                .putString(KEY_THEME, "SYSTEM")
                .putString(KEY_THEME_PALETTE, "ocean")
                .putString(KEY_BACKGROUND_STYLE, "OCEAN")
                .putString(KEY_ACCENT_COLOR, "GREEN")
                .putString(KEY_APP_FONT, "DEFAULT")
                .putBoolean(KEY_HIGH_PERFORMANCE_MODE, true)
                .putBoolean(KEY_HIGH_PERFORMANCE_DEFAULT_APPLIED, true)
                .putInt(KEY_PERFORMANCE_REFRESH_RATE, DEFAULT_PERFORMANCE_REFRESH_RATE)
                .putBoolean(KEY_REDUCE_ANIMATION, false)
                .putBoolean(KEY_REDUCE_BLUR, false)
                .putBoolean(KEY_PROGRESSIVE_BLUR, false)
                .putBoolean(KEY_FROSTED_GLASS, lowRam)
                .putBoolean(KEY_LEGACY_MESH_GRADIENT, false)
                .putBoolean(KEY_LYRICS_BLUR, true)
                .putBoolean(KEY_LIQUID_FLOW, true)
                .putBoolean(KEY_LYRICS_SPARKLE, true)
                .putBoolean(KEY_SYNCED_LYRICS, true)
                .putBoolean(KEY_LYRIC_STRIP, true)
                .putBoolean(KEY_ANIMATED_CANVAS, true)
            }
    }

    private fun readAll() {
        migrateSingleQuality()
        audioQualityWifi.value = readQuality(KEY_QUALITY_WIFI)
        audioQualityCellular.value = readQuality(KEY_QUALITY_CELLULAR)
        upgradeLengthSlackSeconds.value = prefs.getInt(
            KEY_UPGRADE_LENGTH_SLACK_SECONDS,
            DEFAULT_UPGRADE_LENGTH_SLACK_SECONDS,
        ).coerceIn(MIN_UPGRADE_LENGTH_SLACK_SECONDS, MAX_UPGRADE_LENGTH_SLACK_SECONDS)
        migrateDownloadQuality()
        downloadQuality.value = readDownloadQuality()
        wifiOnlyDownloads.value = prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, true)
        automaticUpdateChecks.value = prefs.getBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, true)
        includePreReleaseUpdates.value = prefs.getBoolean(KEY_INCLUDE_PRERELEASE_UPDATES, false)
        betaUpdatesEnabled.value = prefs.getBoolean(KEY_BETA_UPDATES_ENABLED, false)
        // These fallback values are intentionally the pre-default-change values.
        // New installs have explicit values from seedFirstInstallDefaults(); an
        // upgrade with a missing key must not silently acquire a new preference.
        exportDownloads.value = prefs.getBoolean(KEY_EXPORT_DOWNLOADS, false)
        autoDownloadLikedSongs.value = prefs.getBoolean(KEY_AUTO_DOWNLOAD_LIKED_SONGS, false)
        syncDownloadedPlaylists.value = prefs.getBoolean(KEY_SYNC_DOWNLOADED_PLAYLISTS, false)
        downloadLikedSongs.value = prefs.getBoolean(KEY_DOWNLOAD_LIKED_SONGS, false)
        crossfadeSeconds.value = prefs.getInt(KEY_CROSSFADE, 0)
        crossfadeOnSkip.value = prefs.getBoolean(KEY_CROSSFADE_ON_SKIP, false)
        smartFadeEnabled.value = prefs.getBoolean(KEY_SMART_FADE, false)
        automixPerformanceMode.value = runCatching {
            AutomixPerformanceMode.valueOf(
                prefs.getString(KEY_AUTOMIX_PERFORMANCE_MODE, null) ?: AutomixPerformanceMode.BALANCED.name,
            )
        }.getOrDefault(AutomixPerformanceMode.BALANCED)
        skipSilence.value = prefs.getBoolean(KEY_SKIP_SILENCE, false)
        pauseOnMute.value = prefs.getBoolean(KEY_PAUSE_ON_MUTE, false)
        resumeOnBluetooth.value = prefs.getBoolean(KEY_RESUME_ON_BLUETOOTH, false)
        outputPcmMode.value = runCatching {
            OutputPcmMode.valueOf(
                prefs.getString(KEY_OUTPUT_PCM_MODE, OutputPcmMode.PCM_16.name)
                    ?: OutputPcmMode.PCM_16.name,
            )
        }.getOrDefault(OutputPcmMode.PCM_16)
        preferUsbDac.value = prefs.getBoolean(KEY_PREFER_USB_DAC, false)
        bitPerfectMode.value = runCatching {
            BitPerfectMode.valueOf(prefs.getString(KEY_BIT_PERFECT_MODE, null) ?: BitPerfectMode.OFF.name)
        }.getOrDefault(BitPerfectMode.OFF)
        usbAllowFixedVolume.value = prefs.getBoolean(KEY_USB_ALLOW_FIXED_VOLUME, false)
        convertForLimitedDac.value = prefs.getBoolean(KEY_CONVERT_FOR_LIMITED_DAC, false)
        loudnessNormalization.value = prefs.getBoolean(KEY_LOUDNESS_NORMALIZATION, true)
        loudnessOffOnSpeaker.value = prefs.getBoolean(KEY_LOUDNESS_OFF_ON_SPEAKER, true)
        dolbyAtmos.value = prefs.getBoolean(KEY_DOLBY_ATMOS, true)
        directSourceStream.value = prefs.getBoolean(KEY_DIRECT_SOURCE_STREAM, false)
        spatialAudio.value = prefs.getBoolean(KEY_SPATIAL_AUDIO, false)
        spatialAudioByTrack.value = readSpatialAudioByTrack()
        equalizerEnabled.value = prefs.getBoolean(KEY_EQ_ENABLED, false)
        equalizerMode.value = runCatching {
            EqualizerMode.valueOf(prefs.getString(KEY_EQ_MODE, null) ?: EqualizerMode.DYNAMIC.name)
        }.getOrDefault(EqualizerMode.DYNAMIC)
        equalizerToneX.value = prefs.getInt(KEY_EQ_TONE_X, 0).coerceIn(-EqLayout.TONE_STEPS, EqLayout.TONE_STEPS)
        equalizerToneY.value = prefs.getInt(KEY_EQ_TONE_Y, 0).coerceIn(-EqLayout.TONE_STEPS, EqLayout.TONE_STEPS)
        equalizerFocused.value = prefs.getBoolean(KEY_EQ_FOCUSED, false)
        equalizerBalance.value = prefs.getFloat(KEY_EQ_BALANCE, 0f).coerceIn(-1f, 1f)
        equalizerBands.value = readEqualizerBands()
        equalizerPreset.value = EqualizerPreset.matching(equalizerBands.value)
        // KEY_SPEED lama adalah laju tanpa pitch (= tempo): dipindah jadi tempo
        // bila kunci tempo baru belum pernah ditulis.
        playbackRate.value = prefs.getFloat(KEY_PLAYBACK_RATE, 1.0f)
        playbackTempo.value = if (prefs.contains(KEY_PLAYBACK_TEMPO)) {
            prefs.getFloat(KEY_PLAYBACK_TEMPO, 1.0f)
        } else {
            prefs.getFloat(KEY_SPEED, 1.0f)
        }
        playbackSpeed.value = effectivePlaybackSpeed()
        themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.DARK.name)
        }.getOrDefault(ThemeMode.DARK)
        appFont.value = runCatching {
            AppFont.valueOf(prefs.getString(KEY_APP_FONT, null) ?: AppFont.DEFAULT.name)
        }.getOrDefault(AppFont.DEFAULT)
        accentColor.value = runCatching {
            AccentColor.valueOf(prefs.getString(KEY_ACCENT_COLOR, null) ?: AccentColor.RED.name)
        }.getOrDefault(AccentColor.RED)
        customThemePacks.value = ThemePacks.decodeLibrary(prefs.getString(KEY_CUSTOM_THEME_PACKS, null))
        selectedThemePackId.value = prefs.getString(KEY_THEME_PALETTE, "ocean")
            ?.takeIf { id -> ThemePacks.builtIns.any { it.id == id } || customThemePacks.value.any { it.id == id } }
            ?: "ocean"
        themeBackgroundUri.value = wallpaperPrefs.getString(KEY_THEME_BACKGROUND_URI, null)
        backgroundStyle.value = runCatching {
            AppBackgroundStyle.valueOf(
                prefs.getString(KEY_BACKGROUND_STYLE, AppBackgroundStyle.PLAIN.name)
                    ?: AppBackgroundStyle.PLAIN.name,
            )
        }.getOrDefault(AppBackgroundStyle.PLAIN)
        amoledDisplayColors.value = prefs.getBoolean(KEY_AMOLED_DISPLAY_COLORS, false)
        autoplay.value = prefs.getBoolean(KEY_AUTOPLAY, true)
        shuffleEnabled.value = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false)
        repeatMode.value = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF)
        showNerdStats.value = prefs.getBoolean(KEY_NERD_STATS, false)
        showListenTogetherButton.value = prefs.getBoolean(KEY_LISTEN_TOGETHER_BUTTON, false)
        showListenTogetherTab.value = prefs.getBoolean(KEY_LISTEN_TOGETHER_TAB, false)
        reduceAnimation.value = prefs.getBoolean(KEY_REDUCE_ANIMATION, false)
        coverSlideAnimation.value = prefs.getBoolean(KEY_COVER_SLIDE_ANIMATION, true)
        coverFluidAnimation.value = prefs.getBoolean(KEY_COVER_FLUID_ANIMATION, false)
        coverMixOnlyEffects.value = prefs.getBoolean(KEY_COVER_MIX_ONLY_EFFECTS, false)
        onboardingComplete.value = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        // Existing installs stored `false` as the former default. Apply the
        // requested high-performance default once, then respect every explicit
        // change made by the listener from this build onward.
        if (!prefs.getBoolean(KEY_HIGH_PERFORMANCE_DEFAULT_APPLIED, false)) {
            highPerformanceMode.value = true
            performanceRefreshRate.value = DEFAULT_PERFORMANCE_REFRESH_RATE
            prefs.edit {
                putBoolean(KEY_HIGH_PERFORMANCE_MODE, true)
                putInt(KEY_PERFORMANCE_REFRESH_RATE, DEFAULT_PERFORMANCE_REFRESH_RATE)
                putBoolean(KEY_HIGH_PERFORMANCE_DEFAULT_APPLIED, true)
            }
        } else {
            highPerformanceMode.value = prefs.getBoolean(KEY_HIGH_PERFORMANCE_MODE, true)
            performanceRefreshRate.value = normalizePerformanceRefreshRate(
                prefs.getInt(KEY_PERFORMANCE_REFRESH_RATE, DEFAULT_PERFORMANCE_REFRESH_RATE),
            )
        }
        stopOnTaskRemoved.value = prefs.getBoolean(KEY_STOP_ON_TASK_REMOVED, true)
        hideVolumeBar.value = prefs.getBoolean(KEY_HIDE_VOLUME_BAR, false)
        hideStatusBarInPlayer.value = prefs.getBoolean(KEY_HIDE_STATUS_BAR_IN_PLAYER, false)
        hideSongStatus.value = prefs.getBoolean(KEY_HIDE_SONG_STATUS, false)
        showQueueHistory.value = prefs.getBoolean(KEY_SHOW_QUEUE_HISTORY, false)
        hapticFeedbackEnabled.value = prefs.getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)
        swipeToPlayNext.value = prefs.getBoolean(KEY_SWIPE_TO_PLAY_NEXT, false)
        dontRepeatSuggestions.value = prefs.getBoolean(KEY_DONT_REPEAT_SUGGESTIONS, false)
        smartVersionAlignment.value = prefs.getBoolean(KEY_SMART_VERSION_ALIGNMENT, true)
        autoMusicOnly.value = prefs.getBoolean(KEY_AUTO_MUSIC_ONLY, true)
        hideHomeRecents.value = prefs.getBoolean(KEY_HIDE_HOME_RECENTS, false)
        hideHomeBranding.value = prefs.getBoolean(KEY_HIDE_HOME_BRANDING, false)
        hideHomeVideos.value = prefs.getBoolean(KEY_HIDE_HOME_VIDEOS, false)
        hideHomeTopPicks.value = prefs.getBoolean(KEY_HIDE_HOME_TOP_PICKS, false)
        hideHomeMoods.value = prefs.getBoolean(KEY_HIDE_HOME_MOODS, false)
        // Earlier builds defaulted video thumbnails to square. Move only installs
        // with no saved choice to the portrait 9:16 default; an explicit value
        // from an older build is still the user's preference and must win.
        if (!prefs.getBoolean(KEY_VIDEO_THUMBNAIL_RATIO_MIGRATED, false)) {
            squareVideoCovers.value = prefs.getBoolean(KEY_SQUARE_VIDEO_COVERS, false)
            prefs.edit {
                if (!prefs.contains(KEY_SQUARE_VIDEO_COVERS)) putBoolean(KEY_SQUARE_VIDEO_COVERS, false)
                putBoolean(KEY_VIDEO_THUMBNAIL_RATIO_MIGRATED, true)
            }
        } else {
            squareVideoCovers.value = prefs.getBoolean(KEY_SQUARE_VIDEO_COVERS, false)
        }
        VideoThumbnails.square = squareVideoCovers.value
        stillVideoCovers.value = prefs.getBoolean(KEY_STILL_VIDEO_COVERS, false)
        homeLayout.value = HomeLayout.decode(prefs.getString(KEY_HOME_LAYOUT, null))
        libraryArtistsRow.value = prefs.getBoolean(KEY_LIBRARY_ARTISTS_ROW, false)
        libraryLayout.value = HomeLayout.decode(prefs.getString(KEY_LIBRARY_LAYOUT, null))
        reduceDynamicBlur.value = prefs.getBoolean(KEY_REDUCE_BLUR, false)
        liquidGlass.value = prefs.getBoolean(KEY_LIQUID_GLASS, false)
        frostedGlass.value = prefs.getBoolean(KEY_FROSTED_GLASS, false)
        val glassDefault = GlassPreset.DEFAULT
        glassClarity.value = GlassClarity(
            if (prefs.contains(KEY_GLASS_CLARITY_LEVEL)) {
                prefs.getFloat(KEY_GLASS_CLARITY_LEVEL, glassDefault.level)
            } else {
                prefs.getString(KEY_GLASS_CLARITY, null)
                    ?.let { GlassClarity.levelForLegacyName(it) }
                    ?: glassDefault.level
            },
        )
        glassCustom.value = GlassCustom(
            blur = prefs.getFloat(KEY_GLASS_BLUR, glassDefault.custom.blur),
            saturation = prefs.getFloat(KEY_GLASS_SATURATION, glassDefault.custom.saturation),
            tint = prefs.getFloat(KEY_GLASS_TINT, glassDefault.custom.tint),
            refraction = prefs.getFloat(KEY_GLASS_REFRACTION, glassDefault.custom.refraction),
            refractionWidth = prefs.getFloat(KEY_GLASS_REFRACTION_WIDTH, glassDefault.custom.refractionWidth),
            depth = prefs.getBoolean(KEY_GLASS_DEPTH, glassDefault.custom.depth),
            rainbow = prefs.getFloat(KEY_GLASS_RAINBOW, glassDefault.custom.rainbow),
            rim = prefs.getFloat(KEY_GLASS_RIM, glassDefault.custom.rim),
            shadow = prefs.getFloat(KEY_GLASS_SHADOW, glassDefault.custom.shadow),
            quality = prefs.getFloat(KEY_GLASS_QUALITY, glassDefault.custom.quality),
        ).coerced()
        val gi = glassDefault.indicator
        glassIndicator.value = GlassIndicator(
            restLens = prefs.getBoolean(KEY_GIND_REST_LENS, gi.restLens),
            restRefraction = prefs.getFloat(KEY_GIND_REST_REFRACTION, gi.restRefraction),
            restRefractionWidth = prefs.getFloat(KEY_GIND_REST_WIDTH, gi.restRefractionWidth),
            liftRefraction = prefs.getFloat(KEY_GIND_LIFT_REFRACTION, gi.liftRefraction),
            liftRefractionWidth = prefs.getFloat(KEY_GIND_LIFT_WIDTH, gi.liftRefractionWidth),
            rainbow = prefs.getFloat(KEY_GIND_RAINBOW, gi.rainbow),
            depth = prefs.getBoolean(KEY_GIND_DEPTH, gi.depth),
            blur = prefs.getFloat(KEY_GIND_BLUR, gi.blur),
            saturation = prefs.getFloat(KEY_GIND_SATURATION, gi.saturation),
            brightness = prefs.getFloat(KEY_GIND_BRIGHTNESS, gi.brightness),
            fill = prefs.getFloat(KEY_GIND_FILL, gi.fill),
            rim = prefs.getFloat(KEY_GIND_RIM, gi.rim),
            innerShadow = prefs.getFloat(KEY_GIND_INNER_SHADOW, gi.innerShadow),
            innerRadius = prefs.getFloat(KEY_GIND_INNER_RADIUS, gi.innerRadius),
            shadow = prefs.getFloat(KEY_GIND_SHADOW, gi.shadow),
            lift = prefs.getFloat(KEY_GIND_LIFT, gi.lift),
            iconLift = prefs.getFloat(KEY_GIND_ICON_LIFT, gi.iconLift),
            squash = prefs.getFloat(KEY_GIND_SQUASH, gi.squash),
        ).coerced()
        progressiveBlur.value = prefs.getBoolean(KEY_PROGRESSIVE_BLUR, false)
        lyricsBlur.value = prefs.getBoolean(KEY_LYRICS_BLUR, true)
        liquidFlow.value = prefs.getBoolean(KEY_LIQUID_FLOW, true)
        lyricsSparkle.value = prefs.getBoolean(KEY_LYRICS_SPARKLE, true)
        lyricsOffsetByTrack = readLyricsOffsetByTrack()
        lyricsSpeedByTrack = readLyricsSpeedByTrack()
        skipSegmentsByTrack = readSkipSegmentsByTrack()
        playbackWindowsByTrack = readPlaybackWindowsByTrack()
        translationLanguage.value = prefs.getString(KEY_TRANSLATION_LANGUAGE, "").orEmpty()
        if (highPerformanceMode.value) {
            reduceAnimation.value = false
            reduceDynamicBlur.value = false
        }
        animatedCanvas.value = prefs.getBoolean(KEY_ANIMATED_CANVAS, true)
        canvasOverCellular.value = prefs.getBoolean(KEY_CANVAS_OVER_CELLULAR, false)
        spotifyCanvasAutoHide.value = prefs.getBoolean(KEY_SPOTIFY_CANVAS_AUTO_HIDE, true)
        prioritizeSpotifyCanvas.value = prefs.getBoolean(KEY_PRIORITIZE_SPOTIFY_CANVAS, false)
        signatureApiEnabled.value = prefs.getBoolean(KEY_SIGNATURE_API_ENABLED, false)
        signatureApiUrl.value = normalizeSignatureApiUrl(prefs.getString(KEY_SIGNATURE_API_URL, "").orEmpty())
        fullBleedArtwork.value = prefs.getBoolean(KEY_FULL_BLEED_ARTWORK, true)
        legacyMeshGradient.value = prefs.getBoolean(KEY_LEGACY_MESH_GRADIENT, false)
        lastPlayerScreen.value = runCatching {
            LastPlayerScreen.valueOf(
                prefs.getString(KEY_LAST_PLAYER_SCREEN, null) ?: LastPlayerScreen.MAIN.name,
            )
        }.getOrDefault(LastPlayerScreen.MAIN)
        syncedLyrics.value = prefs.getBoolean(KEY_SYNCED_LYRICS, true)
        lyricStrip.value = prefs.getBoolean(KEY_LYRIC_STRIP, true)
        lyricsSources.value = readLyricsSources()
        lyricsSourceOrder.value = readLyricsSourceOrder()
        prioritizeSyllableSync.value = prefs.getBoolean(KEY_PRIORITIZE_SYLLABLE_SYNC, false)
        paxSenixApiKey.value = prefs.getString(KEY_PAXSENIX_API_KEY, "").orEmpty()
        com.music.audify.data.lyrics.PaxSenix.setApiKey(paxSenixApiKey.value)
        audioCacheLimitBytes.value = clampCacheLimit(prefs.getLong(KEY_CACHE_LIMIT, DEFAULT_CACHE_LIMIT_BYTES))
        canvasCacheLimitBytes.value = prefs.getLong(KEY_CANVAS_CACHE_LIMIT, DEFAULT_CANVAS_CACHE_LIMIT_BYTES)
            .coerceIn(MIN_CANVAS_CACHE_LIMIT_BYTES, MAX_CANVAS_CACHE_LIMIT_BYTES)
        showCacheFolder.value = prefs.getBoolean(KEY_SHOW_CACHE_FOLDER, false)
        lastfmEnabled.value = prefs.getBoolean(KEY_LASTFM_ENABLED, false)
        lastfmUsername.value = prefs.getString(KEY_LASTFM_USERNAME, "").orEmpty()
        lastfmSessionKey.value = prefs.getString(KEY_LASTFM_SESSION_KEY, "").orEmpty()
        lastfmApiKey.value = prefs.getString(KEY_LASTFM_API_KEY, "").orEmpty().ifBlank { BuildConfig.LASTFM_API_KEY }
        lastfmSecret.value = prefs.getString(KEY_LASTFM_SECRET, "").orEmpty().ifBlank { BuildConfig.LASTFM_SECRET }
        lastfmEndpoint.value = prefs.getString(KEY_LASTFM_ENDPOINT, "").orEmpty()
        lastfmScrobbleEnabled.value = prefs.getBoolean(KEY_LASTFM_SCROBBLE_ENABLED, false)
        lastfmNowPlaying.value = prefs.getBoolean(KEY_LASTFM_NOW_PLAYING, false) && lastfmScrobbleEnabled.value
        lastfmPrimaryArtistOnly.value = prefs.getBoolean(KEY_LASTFM_PRIMARY_ARTIST_ONLY, false)
        scrobbleMinDuration.value = prefs.getInt(KEY_SCROBBLE_MIN_DURATION, 30)
        scrobbleDelayPercent.value = prefs.getFloat(KEY_SCROBBLE_DELAY_PERCENT, 0.5f)
        scrobbleDelaySeconds.value = prefs.getInt(KEY_SCROBBLE_DELAY_SECONDS, 180)
        listenBrainzEnabled.value = prefs.getBoolean(KEY_LISTENBRAINZ_ENABLED, false)
        listenBrainzToken.value = prefs.getString(KEY_LISTENBRAINZ_TOKEN, "").orEmpty()
        listenBrainzPrimaryArtistOnly.value = prefs.getBoolean(KEY_LISTENBRAINZ_PRIMARY_ARTIST_ONLY, false)
        spotifySpdcToken.value = prefs.getString(KEY_SPOTIFY_SPDC_TOKEN, "").orEmpty()
        replayGenres.value = prefs.getBoolean(KEY_REPLAY_GENRES, true)
        showWeeklyRecapCard.value = prefs.getBoolean(KEY_SHOW_WEEKLY_RECAP_CARD, true)
        showTimeAwareShelf.value = prefs.getBoolean(KEY_SHOW_TIME_AWARE_SHELF, true)
        weeklyRecapSeenWeek.value = prefs.getLong(KEY_WEEKLY_RECAP_SEEN_WEEK, 0L)
        filterNonMusicAudio.value = prefs.getBoolean(KEY_FILTER_NON_MUSIC_AUDIO, true)
        hideExplicitContent.value = prefs.getBoolean(KEY_HIDE_EXPLICIT_CONTENT, false)
        localMusicSort.value = readLocalMusicSort(KEY_LOCAL_MUSIC_SORT)
        downloadedMusicSort.value = readLocalMusicSort(KEY_DOWNLOADED_MUSIC_SORT)
        localMusicViewType.value = readLibraryViewType(KEY_LOCAL_MUSIC_VIEW_TYPE)
        downloadedMusicViewType.value = readLibraryViewType(KEY_DOWNLOADED_MUSIC_VIEW_TYPE)
        librarySort.value = prefs.getString(KEY_LIBRARY_SORT, null)
            ?.let { saved -> LibrarySort.entries.firstOrNull { it.name == saved } }
            ?: LibrarySort.DEFAULT
        detailSongSorts.value = readDetailSongSorts()
        localMusicFolderUri.value = prefs.getString(KEY_LOCAL_MUSIC_FOLDER_URI, "").orEmpty()
        downloadFolderUri.value = prefs.getString(KEY_DOWNLOAD_FOLDER_URI, "").orEmpty()
        webdavUrl.value = prefs.getString(KEY_WEBDAV_URL, "").orEmpty()
        webdavUsername.value = prefs.getString(KEY_WEBDAV_USERNAME, "").orEmpty()
        webdavPassword.value = authStore.webdavPassword.orEmpty()
        smbHost.value = prefs.getString(KEY_SMB_HOST, "").orEmpty()
        smbShare.value = prefs.getString(KEY_SMB_SHARE, "").orEmpty()
        smbBasePath.value = prefs.getString(KEY_SMB_BASE_PATH, "").orEmpty()
        smbUsername.value = prefs.getString(KEY_SMB_USERNAME, "").orEmpty()
        smbPassword.value = authStore.smbPassword.orEmpty()
        com.music.audify.data.smb.SmbAuth.update(
            smbHost.value,
            smbShare.value,
            smbBasePath.value,
            smbUsername.value,
            smbPassword.value,
        )
        com.music.audify.data.webdav.WebDavAuth.update(
            webdavUrl.value,
            webdavUsername.value,
            webdavPassword.value,
        )
        pinnedPlaylists.value = readPinnedPlaylists()
        pinnedCards.value = readPinnedCards()
        // Remove stale profile metadata from the retired user-token integration.
        prefs.edit {
            remove("discord_username")
            remove("discord_name")
            remove("discord_avatar")
            remove("discord_status")
            remove("discord_activity_name")
            remove("discord_info_dismissed")
        }
        profileOverride.value = ProfileOverride(
            owner = prefs.getString(KEY_PROFILE_OWNER, "").orEmpty(),
            name = prefs.getString(KEY_PROFILE_NAME, "").orEmpty(),
            photoPath = prefs.getString(KEY_PROFILE_PHOTO, "").orEmpty(),
        )
        discordRpcEnabled.value = prefs.getBoolean(KEY_DISCORD_RPC_ENABLED, false)
        discordUseDetails.value = prefs.getBoolean(KEY_DISCORD_USE_DETAILS, false)
        discordShowAudioQuality.value = prefs.getBoolean(KEY_DISCORD_SHOW_AUDIO_QUALITY, true)
        discordShowTrackTitle.value = prefs.getBoolean(KEY_DISCORD_SHOW_TRACK_TITLE, true)
        discordShowArtist.value = prefs.getBoolean(KEY_DISCORD_SHOW_ARTIST, true)
        discordShowAlbum.value = prefs.getBoolean(KEY_DISCORD_SHOW_ALBUM, false)
        discordShowArtwork.value = prefs.getBoolean(KEY_DISCORD_SHOW_ARTWORK, true)
        discordShowElapsedTime.value = prefs.getBoolean(KEY_DISCORD_SHOW_ELAPSED_TIME, true)
        discordHideWhenPaused.value = prefs.getBoolean(KEY_DISCORD_HIDE_WHEN_PAUSED, false)
        discordAdvancedMode.value = prefs.getBoolean(KEY_DISCORD_ADVANCED_MODE, false)
        discordActivityType.value = prefs.getString(KEY_DISCORD_ACTIVITY_TYPE, "listening").orEmpty()
        discordButton1Text.value = prefs.getString(KEY_DISCORD_BUTTON_1_TEXT, "").orEmpty()
        discordButton1Visible.value = prefs.getBoolean(KEY_DISCORD_BUTTON_1_VISIBLE, true)
        discordButton2Text.value = prefs.getString(KEY_DISCORD_BUTTON_2_TEXT, "").orEmpty()
        discordButton2Visible.value = prefs.getBoolean(KEY_DISCORD_BUTTON_2_VISIBLE, true)
    }

    /**
     * True the first time this is called after [currentVersionCode] rises above
     * whatever was last recorded — i.e. once per update, on the first launch
     * after it installs. A fresh install has nothing to compare against, so
     * the very first call seeds the stored value from [currentVersionCode]
     * rather than reporting an update.
     *
     * Audify ships sideloaded (see [com.music.audify.data.AppUpdateChecker]),
     * so installing a new APK over the old one is the only "update" there is —
     * app data, this pref included, survives it exactly like a Play Store
     * update. Call once per process start, before anything reads a cache that
     * an update should invalidate.
     */
    fun consumeVersionUpdate(currentVersionCode: Int): Boolean {
        val last = prefs.getInt(KEY_LAST_VERSION_CODE, currentVersionCode)
        if (last != currentVersionCode) {
            prefs.edit {putInt(KEY_LAST_VERSION_CODE, currentVersionCode)}
        }
        return currentVersionCode > last
    }

    /**
     * A ceiling saved when there was only one applies to both connections.
     * Someone who picked Low to protect a data plan would not thank us for
     * quietly putting Wi-Fi *and* mobile back on High.
     */
    private fun migrateSingleQuality() {
        val legacy = prefs.getString(KEY_QUALITY_LEGACY, null) ?: return
        prefs.edit {
                putString(KEY_QUALITY_WIFI, legacy)
                .putString(KEY_QUALITY_CELLULAR, legacy)
                .remove(KEY_QUALITY_LEGACY)
            }
    }

    private fun readQuality(key: String): AudioQuality {
        val stored = prefs.getString(key, null) ?: return AudioQuality.LOSSLESS
        return runCatching { AudioQuality.valueOf(stored) }.getOrDefault(AudioQuality.LOSSLESS)
    }

    /**
     * Write down what the download path was already doing, before it starts
     * being asked instead.
     *
     * Download quality used to be derived rather than chosen: a lossless copy
     * was kept when `SourceResolver.requestForNow()` said Lossless, which meant
     * a download quietly turned on the lossless preference and off again with
     * it. Someone who switched that off on the Sources screen was getting AAC
     * downloads on purpose, and defaulting them to Lossless now would answer a
     * question they had already answered — with thirty-five megabytes a track.
     *
     * The ceilings are deliberately *not* consulted. They were only in that
     * derivation because there was nowhere else to say "not on mobile data",
     * and [wifiOnlyDownloads] is now where that is said.
     */
    private fun migrateDownloadQuality() {
        if (prefs.contains(KEY_QUALITY_DOWNLOAD)) return
        // Was derived from the old `losslessAudio` switch, which defaulted to
        // on; LOSSLESS is what that produced for all but the few installs that
        // had turned it off, and is the default a fresh install gets anyway.
        prefs.edit {putString(KEY_QUALITY_DOWNLOAD, DownloadQuality.LOSSLESS.name)}
    }

    private fun readDownloadQuality(): DownloadQuality {
        val stored = prefs.getString(KEY_QUALITY_DOWNLOAD, null) ?: return DownloadQuality.LOSSLESS
        return runCatching { DownloadQuality.valueOf(stored) }.getOrDefault(DownloadQuality.LOSSLESS)
    }

    /**
     * Track the active network so [effectiveAudioQuality] can answer without
     * touching ConnectivityManager. Stream resolution happens off the main
     * thread mid-playback; a callback keeps that lookup off the hot path and
     * lets the settings page show which ceiling is currently in force.
     */
    private fun watchConnection(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val refresh = {
            meteredConnection.value = runCatching {
                if (manager.activeNetwork == null) null else manager.isActiveNetworkMetered
            }.getOrNull()
        }
        refresh()
        runCatching {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = refresh()
                    override fun onLost(network: Network) = refresh()
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) = refresh()
                },
            )
        }
    }

    fun setAutoplay(value: Boolean) {
        autoplay.value = value
        prefs.edit {putBoolean(KEY_AUTOPLAY, value)}
    }

    fun setShuffleEnabled(value: Boolean) {
        shuffleEnabled.value = value
        prefs.edit {putBoolean(KEY_SHUFFLE_ENABLED, value)}
    }

    fun setRepeatMode(value: Int) {
        repeatMode.value = value
        prefs.edit {putInt(KEY_REPEAT_MODE, value)}
    }

    fun setAudioQualityWifi(value: AudioQuality) {
        audioQualityWifi.value = value
        prefs.edit {putString(KEY_QUALITY_WIFI, value.name)}
    }

    fun setAudioQualityCellular(value: AudioQuality) {
        audioQualityCellular.value = value
        prefs.edit {putString(KEY_QUALITY_CELLULAR, value.name)}
    }

    fun setUpgradeLengthSlackSeconds(value: Int) {
        val normalized = value.coerceIn(
            MIN_UPGRADE_LENGTH_SLACK_SECONDS,
            MAX_UPGRADE_LENGTH_SLACK_SECONDS,
        )
        upgradeLengthSlackSeconds.value = normalized
        prefs.edit {putInt(KEY_UPGRADE_LENGTH_SLACK_SECONDS, normalized)}
    }

    fun setDownloadQuality(value: DownloadQuality) {
        downloadQuality.value = value
        prefs.edit {putString(KEY_QUALITY_DOWNLOAD, value.name)}
    }

    fun setWifiOnlyDownloads(value: Boolean) {
        wifiOnlyDownloads.value = value
        prefs.edit {putBoolean(KEY_WIFI_ONLY_DOWNLOADS, value)}
    }

    fun setAutomaticUpdateChecks(value: Boolean) {
        automaticUpdateChecks.value = value
        prefs.edit { putBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, value) }
    }

    fun setIncludePreReleaseUpdates(value: Boolean) {
        includePreReleaseUpdates.value = value
        prefs.edit { putBoolean(KEY_INCLUDE_PRERELEASE_UPDATES, value) }
    }

    fun setBetaUpdatesEnabled(value: Boolean) {
        betaUpdatesEnabled.value = value
        prefs.edit { putBoolean(KEY_BETA_UPDATES_ENABLED, value) }
    }

    fun setCrossfadeSeconds(value: Int) {
        crossfadeSeconds.value = value
        prefs.edit {putInt(KEY_CROSSFADE, value)}
    }

    fun setCrossfadeOnSkip(value: Boolean) {
        crossfadeOnSkip.value = value
        prefs.edit {putBoolean(KEY_CROSSFADE_ON_SKIP, value)}
    }

    fun setSmartFadeEnabled(value: Boolean) {
        smartFadeEnabled.value = value
        prefs.edit {putBoolean(KEY_SMART_FADE, value)}
    }

    fun setAutomixPerformanceMode(value: AutomixPerformanceMode) {
        automixPerformanceMode.value = value
        prefs.edit {putString(KEY_AUTOMIX_PERFORMANCE_MODE, value.name)}
    }

    fun setSkipSilence(value: Boolean) {
        skipSilence.value = value
        prefs.edit {putBoolean(KEY_SKIP_SILENCE, value)}
    }

    fun setPauseOnMute(value: Boolean) {
        pauseOnMute.value = value
        prefs.edit {putBoolean(KEY_PAUSE_ON_MUTE, value)}
    }

    fun setResumeOnBluetooth(value: Boolean) {
        resumeOnBluetooth.value = value
        prefs.edit {putBoolean(KEY_RESUME_ON_BLUETOOTH, value)}
    }

    fun setDolbyAtmos(value: Boolean) {
        dolbyAtmos.value = value
        prefs.edit {putBoolean(KEY_DOLBY_ATMOS, value)}
    }

    fun setDirectSourceStream(value: Boolean) {
        directSourceStream.value = value
        prefs.edit {putBoolean(KEY_DIRECT_SOURCE_STREAM, value)}
    }

    fun setSpatialAudio(value: Boolean) {
        spatialAudio.value = value
        prefs.edit {putBoolean(KEY_SPATIAL_AUDIO, value)}
    }

    /** Spatial audio for [trackId]: its own saved choice, else the global switch. */
    fun spatialAudioFor(trackId: String?): Boolean =
        trackId?.let { spatialAudioByTrack.value[it] } ?: spatialAudio.value

    /** Saves the choice for [trackId] right away; the oldest entries drop past the cap. */
    fun setSpatialAudioForTrack(trackId: String, value: Boolean) {
        if (trackId.isBlank()) return
        val next = LinkedHashMap(spatialAudioByTrack.value)
        next.remove(trackId)
        next[trackId] = value
        while (next.size > MAX_SPATIAL_TRACKS) next.remove(next.keys.first())
        spatialAudioByTrack.value = next
        val json = org.json.JSONObject()
        next.forEach { (id, on) -> json.put(id, on) }
        prefs.edit {putString(KEY_SPATIAL_AUDIO_BY_TRACK, json.toString())}
    }

    private fun readSpatialAudioByTrack(): Map<String, Boolean> {
        val raw = prefs.getString(KEY_SPATIAL_AUDIO_BY_TRACK, null) ?: return emptyMap()
        return runCatching {
            val json = org.json.JSONObject(raw)
            val out = LinkedHashMap<String, Boolean>()
            json.keys().forEach { out[it] = json.getBoolean(it) }
            out as Map<String, Boolean>
        }.getOrDefault(emptyMap())
    }

    fun setEqualizerEnabled(value: Boolean) {
        equalizerEnabled.value = value
        prefs.edit {putBoolean(KEY_EQ_ENABLED, value)}
    }

    fun setEqualizerMode(value: EqualizerMode) {
        equalizerMode.value = value
        prefs.edit {putString(KEY_EQ_MODE, value.name)}
    }

    fun setEqualizerTone(x: Int, y: Int) {
        val steps = EqLayout.TONE_STEPS
        val clampedX = x.coerceIn(-steps, steps)
        val clampedY = y.coerceIn(-steps, steps)
        equalizerToneX.value = clampedX
        equalizerToneY.value = clampedY
        prefs.edit {putInt(KEY_EQ_TONE_X, clampedX).putInt(KEY_EQ_TONE_Y, clampedY)}
    }

    fun setEqualizerFocused(value: Boolean) {
        equalizerFocused.value = value
        prefs.edit {putBoolean(KEY_EQ_FOCUSED, value)}
    }

    fun setEqualizerBalance(value: Float) {
        val clamped = value.coerceIn(-1f, 1f)
        equalizerBalance.value = clamped
        prefs.edit {putFloat(KEY_EQ_BALANCE, clamped)}
    }

    /**
     * Writes the manual tab's seven gains, and renames the preset row to suit.
     *
     * Stored as text rather than seven keys of their own so that a backup
     * carries them: [exportPrefs] copies the preference file as it stands, and
     * one string is one thing to keep in step rather than seven.
     */
    fun setEqualizerBands(values: List<Float>) {
        val clamped = List(EqLayout.MANUAL_COUNT) {
            values.getOrElse(it) { 0f }.coerceIn(-EqLayout.MANUAL_RANGE_DB, EqLayout.MANUAL_RANGE_DB)
        }
        equalizerBands.value = clamped
        equalizerPreset.value = EqualizerPreset.matching(clamped)
        prefs.edit {putString(KEY_EQ_BANDS, clamped.joinToString(","))}
    }

    /** Applies a preset's curve. [EqualizerPreset.CUSTOM] carries none, so it does nothing. */
    fun setEqualizerPreset(preset: EqualizerPreset) {
        if (preset == EqualizerPreset.CUSTOM) return
        setEqualizerBands(preset.bands)
    }

    private fun readEqualizerBands(): List<Float> {
        val stored = prefs.getString(KEY_EQ_BANDS, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toFloatOrNull() }
            .orEmpty()
        // Padded rather than rejected: a backup written by a build with a
        // different number of bands should restore the ones it does have.
        return List(EqLayout.MANUAL_COUNT) {
            stored.getOrElse(it) { 0f }.coerceIn(-EqLayout.MANUAL_RANGE_DB, EqLayout.MANUAL_RANGE_DB)
        }
    }

    fun setPlaybackSpeed(value: Float) {
        playbackSpeed.value = value
        prefs.edit {putFloat(KEY_SPEED, value)}
    }

    /** Dibulatkan agar 0.8 x 1.25 tepat 1.0 dan pengecekan "bukan 1x" tidak salah. */
    private fun effectivePlaybackSpeed(): Float =
        Math.round(playbackRate.value * playbackTempo.value * 1000f) / 1000f

    fun setPlaybackRate(value: Float) {
        playbackRate.value = value
        prefs.edit {putFloat(KEY_PLAYBACK_RATE, value)}
        setPlaybackSpeed(effectivePlaybackSpeed())
    }

    fun setPlaybackTempo(value: Float) {
        playbackTempo.value = value
        prefs.edit {putFloat(KEY_PLAYBACK_TEMPO, value)}
        setPlaybackSpeed(effectivePlaybackSpeed())
    }

    fun resetPlaybackSpeedAndTempo() {
        playbackRate.value = 1.0f
        playbackTempo.value = 1.0f
        prefs.edit {
                putFloat(KEY_PLAYBACK_RATE, 1.0f)
                .putFloat(KEY_PLAYBACK_TEMPO, 1.0f)
            }
        setPlaybackSpeed(1.0f)
    }

    fun setShowNerdStats(value: Boolean) {
        showNerdStats.value = value
        prefs.edit {putBoolean(KEY_NERD_STATS, value)}
    }

    fun setShowListenTogetherTab(value: Boolean) {
        showListenTogetherTab.value = value
        prefs.edit {putBoolean(KEY_LISTEN_TOGETHER_TAB, value)}
    }

    fun setShowListenTogetherButton(value: Boolean) {
        showListenTogetherButton.value = value
        prefs.edit {putBoolean(KEY_LISTEN_TOGETHER_BUTTON, value)}
    }

    fun setThemeMode(value: ThemeMode) {
        themeMode.value = value
        prefs.edit {putString(KEY_THEME, value.name)}
    }

    fun setAppFont(value: AppFont) {
        appFont.value = value
        prefs.edit {putString(KEY_APP_FONT, value.name)}
    }

    fun setCoverSlideAnimation(value: Boolean) {
        coverSlideAnimation.value = value
        prefs.edit {putBoolean(KEY_COVER_SLIDE_ANIMATION, value)}
    }

    fun setCoverFluidAnimation(value: Boolean) {
        coverFluidAnimation.value = value
        prefs.edit {putBoolean(KEY_COVER_FLUID_ANIMATION, value)}
    }

    fun setCoverMixOnlyEffects(value: Boolean) {
        coverMixOnlyEffects.value = value
        prefs.edit {putBoolean(KEY_COVER_MIX_ONLY_EFFECTS, value)}
    }

    fun setAccentColor(value: AccentColor) {
        accentColor.value = value
        prefs.edit {putString(KEY_ACCENT_COLOR, value.name)}
    }

    fun setThemePack(id: String) {
        val pack = ThemePacks.resolve(id, customThemePacks.value)
        selectedThemePackId.value = pack.id
        prefs.edit {putString(KEY_THEME_PALETTE, pack.id)}
    }

    fun saveCustomThemePack(pack: ThemePack) {
        require(ThemePacks.isValidHex(pack.primaryHex) && ThemePacks.isValidHex(pack.secondaryHex) && ThemePacks.isValidHex(pack.tertiaryHex))
        val updated = (customThemePacks.value.filterNot { it.id == pack.id } + pack).takeLast(64)
        customThemePacks.value = updated
        prefs.edit {putString(KEY_CUSTOM_THEME_PACKS, ThemePacks.encodeLibrary(updated))}
        setThemePack(pack.id)
    }

    fun deleteCustomThemePack(id: String) {
        val updated = customThemePacks.value.filterNot { it.id == id }
        if (updated.size == customThemePacks.value.size) return
        customThemePacks.value = updated
        prefs.edit {putString(KEY_CUSTOM_THEME_PACKS, ThemePacks.encodeLibrary(updated))}
        if (selectedThemePackId.value == id) setThemePack(ThemePacks.CLASSIC_ID)
    }

    fun setThemeBackgroundUri(uri: String?) {
        themeBackgroundUri.value = uri
        wallpaperPrefs.edit {putString(KEY_THEME_BACKGROUND_URI, uri)}
    }

    fun setBackgroundStyle(value: AppBackgroundStyle) {
        backgroundStyle.value = value
        prefs.edit {putString(KEY_BACKGROUND_STYLE, value.name)}
    }

    fun setAmoledDisplayColors(value: Boolean) {
        amoledDisplayColors.value = value
        prefs.edit { putBoolean(KEY_AMOLED_DISPLAY_COLORS, value) }
    }

    fun setReduceAnimation(value: Boolean) {
        reduceAnimation.value = value
        if (value) highPerformanceMode.value = false
        prefs.edit { putBoolean(KEY_REDUCE_ANIMATION, value)
            if (value) putBoolean(KEY_HIGH_PERFORMANCE_MODE, false)
        }
    }

    fun setOnboardingComplete() {
        onboardingComplete.value = true
        prefs.edit {putBoolean(KEY_ONBOARDING_COMPLETE, true)}
    }

    fun setStopOnTaskRemoved(value: Boolean) {
        stopOnTaskRemoved.value = value
        prefs.edit {putBoolean(KEY_STOP_ON_TASK_REMOVED, value)}
    }

    fun setHideVolumeBar(value: Boolean) {
        hideVolumeBar.value = value
        prefs.edit {putBoolean(KEY_HIDE_VOLUME_BAR, value)}
    }

    fun setHideStatusBarInPlayer(value: Boolean) {
        hideStatusBarInPlayer.value = value
        prefs.edit {putBoolean(KEY_HIDE_STATUS_BAR_IN_PLAYER, value)}
    }

    fun setHideSongStatus(value: Boolean) {
        hideSongStatus.value = value
        prefs.edit {putBoolean(KEY_HIDE_SONG_STATUS, value)}
    }

    fun setShowQueueHistory(value: Boolean) {
        showQueueHistory.value = value
        prefs.edit {putBoolean(KEY_SHOW_QUEUE_HISTORY, value)}
    }

    fun setHapticFeedbackEnabled(value: Boolean) {
        hapticFeedbackEnabled.value = value
        prefs.edit { putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, value) }
    }
    fun setSwipeToPlayNext(value: Boolean) {
        swipeToPlayNext.value = value
        prefs.edit {putBoolean(KEY_SWIPE_TO_PLAY_NEXT, value)}
    }

    fun setDontRepeatSuggestions(value: Boolean) {
        dontRepeatSuggestions.value = value
        prefs.edit {putBoolean(KEY_DONT_REPEAT_SUGGESTIONS, value)}
    }

    fun setSmartVersionAlignment(value: Boolean) {
        smartVersionAlignment.value = value
        prefs.edit {putBoolean(KEY_SMART_VERSION_ALIGNMENT, value)}
    }

    fun setAutoMusicOnly(value: Boolean) {
        autoMusicOnly.value = value
        prefs.edit {putBoolean(KEY_AUTO_MUSIC_ONLY, value)}
        // Music-only shows covers, not video frames, so squares go with it.
        if (value) setSquareVideoCovers(true)
    }

    fun setHideHomeRecents(value: Boolean) {
        hideHomeRecents.value = value
        prefs.edit {putBoolean(KEY_HIDE_HOME_RECENTS, value)}
    }

    fun setHideHomeBranding(value: Boolean) {
        hideHomeBranding.value = value
        prefs.edit {putBoolean(KEY_HIDE_HOME_BRANDING, value)}
    }

    fun setHideHomeVideos(value: Boolean) {
        hideHomeVideos.value = value
        prefs.edit {putBoolean(KEY_HIDE_HOME_VIDEOS, value)}
    }

    fun setHideHomeTopPicks(value: Boolean) {
        hideHomeTopPicks.value = value
        prefs.edit {putBoolean(KEY_HIDE_HOME_TOP_PICKS, value)}
    }

    fun setHideHomeMoods(value: Boolean) {
        hideHomeMoods.value = value
        prefs.edit {putBoolean(KEY_HIDE_HOME_MOODS, value)}
    }

    fun setSquareVideoCovers(value: Boolean) {
        squareVideoCovers.value = value
        VideoThumbnails.square = value
        prefs.edit {putBoolean(KEY_SQUARE_VIDEO_COVERS, value)}
    }

    fun setStillVideoCovers(value: Boolean) {
        stillVideoCovers.value = value
        prefs.edit {putBoolean(KEY_STILL_VIDEO_COVERS, value)}
    }

    fun setHomeLayout(value: HomeLayout) {
        homeLayout.value = value
        prefs.edit {putString(KEY_HOME_LAYOUT, value.encode())}
        // Top Picks and Find Your Mood are hidden from the layout now: keep their
        // flags in step, since the feed and Home still read them. Resetting the
        // layout shows both again.
        val recentsKey = homeShelfKey("Recents")
        if (!value.isCustom) {
            setHideHomeTopPicks(false)
            setHideHomeMoods(false)
            setHideHomeRecents(false)
        } else {
            if (value.slots.any { it.key == recentsKey }) {
                setHideHomeRecents(value.isHidden(recentsKey))
            }
            if (value.slots.any { it.key == HOME_SLOT_TOP_PICKS }) {
                setHideHomeTopPicks(value.isHidden(HOME_SLOT_TOP_PICKS))
            }
            if (value.slots.any { it.key == HOME_SLOT_MOODS }) {
                setHideHomeMoods(value.isHidden(HOME_SLOT_MOODS))
            }
        }
    }

    fun setLibraryLayout(value: HomeLayout) {
        libraryLayout.value = value
        prefs.edit {putString(KEY_LIBRARY_LAYOUT, value.encode())}
    }

    fun setLibraryArtistsRow(value: Boolean) {
        libraryArtistsRow.value = value
        prefs.edit {putBoolean(KEY_LIBRARY_ARTISTS_ROW, value)}
    }

    fun setReduceDynamicBlur(value: Boolean) {
        reduceDynamicBlur.value = value
        if (value) highPerformanceMode.value = false
        prefs.edit { putBoolean(KEY_REDUCE_BLUR, value)
            if (value) putBoolean(KEY_HIGH_PERFORMANCE_MODE, false)
        }
    }

    fun setLiquidGlass(value: Boolean) {
        liquidGlass.value = value
        prefs.edit {putBoolean(KEY_LIQUID_GLASS, value)}
    }

    fun setFrostedGlass(value: Boolean) {
        frostedGlass.value = value
        prefs.edit {putBoolean(KEY_FROSTED_GLASS, value)}
    }

    fun setGlassClarity(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        glassClarity.value = GlassClarity(clamped)
        prefs.edit {putFloat(KEY_GLASS_CLARITY_LEVEL, clamped)}
    }

    fun setGlassCustom(value: GlassCustom) {
        val v = value.coerced()
        glassCustom.value = v
        prefs.edit {
                putFloat(KEY_GLASS_BLUR, v.blur)
                .putFloat(KEY_GLASS_SATURATION, v.saturation)
                .putFloat(KEY_GLASS_TINT, v.tint)
                .putFloat(KEY_GLASS_REFRACTION, v.refraction)
                .putFloat(KEY_GLASS_REFRACTION_WIDTH, v.refractionWidth)
                .putBoolean(KEY_GLASS_DEPTH, v.depth)
                .putFloat(KEY_GLASS_RAINBOW, v.rainbow)
                .putFloat(KEY_GLASS_RIM, v.rim)
                .putFloat(KEY_GLASS_SHADOW, v.shadow)
                .putFloat(KEY_GLASS_QUALITY, v.quality)
            }
    }

    fun setGlassIndicator(value: GlassIndicator) {
        val v = value.coerced()
        glassIndicator.value = v
        prefs.edit {
                putBoolean(KEY_GIND_REST_LENS, v.restLens)
                .putFloat(KEY_GIND_REST_REFRACTION, v.restRefraction)
                .putFloat(KEY_GIND_REST_WIDTH, v.restRefractionWidth)
                .putFloat(KEY_GIND_LIFT_REFRACTION, v.liftRefraction)
                .putFloat(KEY_GIND_LIFT_WIDTH, v.liftRefractionWidth)
                .putFloat(KEY_GIND_RAINBOW, v.rainbow)
                .putBoolean(KEY_GIND_DEPTH, v.depth)
                .putFloat(KEY_GIND_BLUR, v.blur)
                .putFloat(KEY_GIND_SATURATION, v.saturation)
                .putFloat(KEY_GIND_BRIGHTNESS, v.brightness)
                .putFloat(KEY_GIND_FILL, v.fill)
                .putFloat(KEY_GIND_RIM, v.rim)
                .putFloat(KEY_GIND_INNER_SHADOW, v.innerShadow)
                .putFloat(KEY_GIND_INNER_RADIUS, v.innerRadius)
                .putFloat(KEY_GIND_SHADOW, v.shadow)
                .putFloat(KEY_GIND_LIFT, v.lift)
                .putFloat(KEY_GIND_ICON_LIFT, v.iconLift)
                .putFloat(KEY_GIND_SQUASH, v.squash)
            }
    }

    /** Tema gelap yang sedang aktif, dipakai preset yang punya nilai terang dan gelap. */
    private var glassDarkTheme = false

    fun applyGlassPreset(preset: GlassPreset) {
        setGlassClarity(preset.level)
        setGlassCustom(preset.customFor(glassDarkTheme))
        setGlassIndicator(preset.indicatorFor(glassDarkTheme))
        setFrostedGlass(preset.frosted)
    }

    /** Keep whichever ready-made preset is selected in step with the active theme. */
    fun syncGlassPresetTheme(dark: Boolean) {
        glassDarkTheme = dark
        val selectedPreset = GlassPreset.values().firstOrNull { preset ->
            preset.matches(glassClarity.value.level, glassCustom.value, glassIndicator.value, frostedGlass.value)
        } ?: return
        applyGlassPreset(selectedPreset)
    }

    fun setProgressiveBlur(value: Boolean) {
        progressiveBlur.value = value
        prefs.edit {putBoolean(KEY_PROGRESSIVE_BLUR, value)}
    }

    fun setHighPerformanceMode(value: Boolean) {
        highPerformanceMode.value = value
        if (value) {
            reduceAnimation.value = false
            reduceDynamicBlur.value = false
        }
        prefs.edit {
            putBoolean(KEY_HIGH_PERFORMANCE_MODE, value)
            putBoolean(KEY_HIGH_PERFORMANCE_DEFAULT_APPLIED, true)
            if (value) {
                putBoolean(KEY_REDUCE_ANIMATION, false)
                putBoolean(KEY_REDUCE_BLUR, false)
            }
        }
    }

    fun setPerformanceRefreshRate(value: Int) {
        val normalized = normalizePerformanceRefreshRate(value)
        performanceRefreshRate.value = normalized
        prefs.edit {putInt(KEY_PERFORMANCE_REFRESH_RATE, normalized)}
    }

    fun setLyricsBlur(value: Boolean) {
        lyricsBlur.value = value
        prefs.edit {putBoolean(KEY_LYRICS_BLUR, value)}
    }

    fun setLiquidFlow(value: Boolean) {
        liquidFlow.value = value
        prefs.edit {putBoolean(KEY_LIQUID_FLOW, value)}
    }

    fun setLyricsSparkle(value: Boolean) {
        lyricsSparkle.value = value
        prefs.edit {putBoolean(KEY_LYRICS_SPARKLE, value)}
    }

    /** Saves the offset for the track now playing right away; 0 clears its entry. */
    fun setLyricsOffsetMs(value: Int) {
        val normalized = value.coerceIn(MIN_LYRICS_OFFSET_MS, MAX_LYRICS_OFFSET_MS)
        if (lyricsOffsetMs.value == normalized) return
        lyricsOffsetMs.value = normalized
        val trackId = lyricsOffsetTrackId ?: return
        val next = LinkedHashMap(lyricsOffsetByTrack)
        next.remove(trackId)
        if (normalized != 0) next[trackId] = normalized
        while (next.size > MAX_LYRICS_OFFSET_TRACKS) next.remove(next.keys.first())
        lyricsOffsetByTrack = next
        val json = org.json.JSONObject()
        next.forEach { (id, ms) -> json.put(id, ms) }
        prefs.edit {putString(KEY_LYRICS_OFFSET_BY_TRACK, json.toString())}
    }

    /** Points [lyricsOffsetMs] at [trackId]'s own saved offset, or 0 when it has none. */
    fun setLyricsOffsetTrack(trackId: String?) {
        lyricsOffsetTrackId = trackId?.takeIf { it.isNotBlank() }
        lyricsOffsetMs.value = lyricsOffsetTrackId?.let { lyricsOffsetByTrack[it] } ?: 0
        lyricsSpeed.value = lyricsOffsetTrackId?.let { lyricsSpeedByTrack[it] } ?: 1f
    }

    /** Saves the lyrics-only timing multiplier for the track now playing. */
    fun setLyricsSpeed(value: Float) {
        val normalized = (Math.round(value.coerceIn(0.75f, 1.25f) * 20f).coerceIn(15, 25) / 20f)
        if (lyricsSpeed.value == normalized) return
        lyricsSpeed.value = normalized
        val trackId = lyricsOffsetTrackId ?: return
        val next = LinkedHashMap(lyricsSpeedByTrack)
        next.remove(trackId)
        if (normalized != 1f) next[trackId] = normalized
        while (next.size > MAX_LYRICS_OFFSET_TRACKS) next.remove(next.keys.first())
        lyricsSpeedByTrack = next
        val json = org.json.JSONObject()
        next.forEach { (id, speed) -> json.put(id, speed.toDouble()) }
        prefs.edit { putString(KEY_LYRICS_SPEED_BY_TRACK, json.toString()) }
    }

    /** Points the editor and playback rule at the current media item. */
    fun setSkipSegmentsTrack(trackId: String?) {
        skipSegmentsTrackId = trackId?.takeIf { it.isNotBlank() }
        skipSegments.value = skipSegmentsTrackId?.let { skipSegmentsByTrack[it] }.orEmpty()
        setPlaybackWindowTrack(trackId)
    }

    private fun setPlaybackWindowTrack(trackId: String?) {
        playbackWindowTrackId = trackId?.takeIf { it.isNotBlank() }
        trackPlaybackWindow.value = playbackWindowTrackId?.let { playbackWindowsByTrack[it] }
    }

    fun playbackWindowForTrack(trackId: String?): TrackPlaybackWindow? =
        trackId?.takeIf { it.isNotBlank() }?.let { playbackWindowsByTrack[it] }

    fun setPlaybackWindowForTrack(trackId: String, window: TrackPlaybackWindow?) {
        if (trackId.isBlank()) return
        val next = LinkedHashMap(playbackWindowsByTrack)
        next.remove(trackId)
        if (window?.isValid == true) next[trackId] = window
        while (next.size > MAX_SKIP_SEGMENT_TRACKS) next.remove(next.keys.first())
        playbackWindowsByTrack = next
        if (playbackWindowTrackId == trackId) trackPlaybackWindow.value = next[trackId]
        val json = org.json.JSONObject()
        next.forEach { (id, range) ->
            val pair = org.json.JSONArray()
            pair.put(range.startMs)
            pair.put(range.endMs)
            json.put(id, pair)
        }
        prefs.edit { putString(KEY_TRACK_PLAYBACK_WINDOWS, json.toString()) }
    }

    private fun readPlaybackWindowsByTrack(): Map<String, TrackPlaybackWindow> {
        val raw = prefs.getString(KEY_TRACK_PLAYBACK_WINDOWS, null) ?: return emptyMap()
        return runCatching {
            val json = org.json.JSONObject(raw)
            val out = LinkedHashMap<String, TrackPlaybackWindow>()
            json.keys().forEach { id ->
                val pair = json.optJSONArray(id) ?: return@forEach
                if (pair.length() < 2) return@forEach
                val window = TrackPlaybackWindow(pair.optLong(0, -1L), pair.optLong(1, -1L))
                if (window.isValid) out[id] = window
            }
            out
        }.getOrDefault(emptyMap())
    }

    /** Compatibility name used by the playback-range editor. */
    fun playbackRangeFor(trackId: String?): TrackPlaybackWindow? = playbackWindowForTrack(trackId)

    /** Saves or clears the playable window using the editor's start/end API. */
    fun setPlaybackRange(trackId: String, startMs: Long?, endMs: Long?) {
        val window = if (startMs == null || endMs == null) null else TrackPlaybackWindow(startMs, endMs)
        setPlaybackWindowForTrack(trackId, window)
    }

    /** Compatibility name used by the multi-segment editor. */
    fun skipSegmentsFor(trackId: String?): List<SkipSegment> = skipSegmentsForTrack(trackId)

    fun setSkipSegments(trackId: String, segments: List<SkipSegment>) =
        setSkipSegmentsForTrack(trackId, segments)

    fun skipSegmentsForTrack(trackId: String?): List<SkipSegment> =
        trackId?.takeIf { it.isNotBlank() }?.let { skipSegmentsByTrack[it] }.orEmpty()

    /** Saves a normalized list; an empty list removes the track's override. */
    fun setSkipSegmentsForTrack(trackId: String, segments: List<SkipSegment>) {
        if (trackId.isBlank()) return
        val normalized = normalizeSkipSegments(segments)
        val next = LinkedHashMap(skipSegmentsByTrack)
        next.remove(trackId)
        if (normalized.isNotEmpty()) next[trackId] = normalized
        while (next.size > MAX_SKIP_SEGMENT_TRACKS) next.remove(next.keys.first())
        skipSegmentsByTrack = next
        if (skipSegmentsTrackId == trackId) skipSegments.value = normalized
        val json = org.json.JSONObject()
        next.forEach { (id, ranges) ->
            val array = org.json.JSONArray()
            ranges.forEach { range ->
                val pair = org.json.JSONArray()
                pair.put(range.startMs)
                pair.put(range.endMs)
                array.put(pair)
            }
            json.put(id, array)
        }
        prefs.edit { putString(KEY_SKIP_SEGMENTS_BY_TRACK, json.toString()) }
    }

    private fun readSkipSegmentsByTrack(): Map<String, List<SkipSegment>> {
        val raw = prefs.getString(KEY_SKIP_SEGMENTS_BY_TRACK, null) ?: return emptyMap()
        return runCatching {
            val json = org.json.JSONObject(raw)
            val out = LinkedHashMap<String, List<SkipSegment>>()
            json.keys().forEach { id ->
                val array = json.optJSONArray(id) ?: return@forEach
                val ranges = buildList {
                    for (index in 0 until array.length()) {
                        val pair = array.optJSONArray(index) ?: continue
                        if (pair.length() < 2) continue
                        add(SkipSegment(pair.optLong(0, -1L), pair.optLong(1, -1L)))
                    }
                }
                val normalized = normalizeSkipSegments(ranges)
                if (normalized.isNotEmpty()) out[id] = normalized
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun readLyricsOffsetByTrack(): Map<String, Int> {
        val raw = prefs.getString(KEY_LYRICS_OFFSET_BY_TRACK, null) ?: return emptyMap()
        return runCatching {
            val json = org.json.JSONObject(raw)
            val out = LinkedHashMap<String, Int>()
            json.keys().forEach {
                out[it] = json.getInt(it).coerceIn(MIN_LYRICS_OFFSET_MS, MAX_LYRICS_OFFSET_MS)
            }
            out as Map<String, Int>
        }.getOrDefault(emptyMap())
    }

    private fun readLyricsSpeedByTrack(): Map<String, Float> {
        val raw = prefs.getString(KEY_LYRICS_SPEED_BY_TRACK, null) ?: return emptyMap()
        return runCatching {
            val json = org.json.JSONObject(raw)
            val out = LinkedHashMap<String, Float>()
            json.keys().forEach { id ->
                val speed = json.getDouble(id).toFloat().coerceIn(0.75f, 1.25f)
                if (speed != 1f) out[id] = (speed * 100f).toInt() / 100f
            }
            out as Map<String, Float>
        }.getOrDefault(emptyMap())
    }

    /** Blank restores "follow the app language"; see [translationLanguage]. */
    fun setTranslationLanguage(value: String) {
        translationLanguage.value = value
        prefs.edit {putString(KEY_TRANSLATION_LANGUAGE, value)}
    }

    fun setSyncedLyrics(value: Boolean) {
        syncedLyrics.value = value
        prefs.edit {putBoolean(KEY_SYNCED_LYRICS, value)}
    }

    fun setLyricStrip(value: Boolean) {
        lyricStrip.value = value
        prefs.edit {putBoolean(KEY_LYRIC_STRIP, value)}
    }

    fun setLyricsSources(value: Set<LyricsSource>) {
        lyricsSources.value = value
        prefs.edit {
                putString(KEY_LYRICS_SOURCES, value.joinToString(",") { it.name })
                // Everything that was on the list this choice was made from, so a
                // later build can tell a source the user turned off from one they
                // have never been shown. See [readLyricsSources].
                .putString(KEY_LYRICS_SOURCES_SEEN, LyricsSource.offered.joinToString(",") { it.name })
            }
    }

    /**
     * Stored as a joined list of names rather than a string set: a name that
     * no longer exists — a source dropped in a later build — has to fall out
     * quietly, and the default when nothing has been saved is "all of them",
     * which a missing key and an empty set would otherwise be unable to tell
     * apart.
     *
     * A source *added* by an upgrade is enabled rather than left out. Absence
     * from a saved list is a decision only about the sources that list was
     * chosen from; a new one was never on it, so its absence says nothing, and
     * treating it as "off" would ship a source nobody could discover without
     * first going and looking for it. [KEY_LYRICS_SOURCES_SEEN] is what makes
     * the two cases distinguishable — before it existed, [LEGACY_SOURCES]
     * stands in as the list of everything there was to have an opinion about.
     */
    private fun readLyricsSources(): Set<LyricsSource> {
        val stored = prefs.getString(KEY_LYRICS_SOURCES, null)
            ?: return LyricsSource.offered.toSet()
        val chosen = stored.split(",").toSources()
        val seen = prefs.getString(KEY_LYRICS_SOURCES_SEEN, null)
            ?.split(",")?.toSources()
            ?: LEGACY_SOURCES
        return (chosen + LyricsSource.entries.filter { it !in seen }).filterNot { it.hidden }.toSet()
    }

    private fun List<String>.toSources(): Set<LyricsSource> =
        mapNotNull { name -> LyricsSource.entries.firstOrNull { it.name == name } }.toSet()

    /**
     * The sources that existed before [KEY_LYRICS_SOURCES_SEEN] was written.
     * Fixed forever: it describes what an old build could have saved, so it
     * does not grow when [LyricsSource] does.
     */
    private val LEGACY_SOURCES = setOf(
        LyricsSource.LYRICS_PLUS,
        LyricsSource.PAXSENIX,
        LyricsSource.BETTER_LYRICS,
        LyricsSource.SIMP_MUSIC,
        LyricsSource.KUGOU,
        LyricsSource.LRCLIB,
        LyricsSource.MUSIXMATCH,
        LyricsSource.GENIUS,
    )

    fun setLyricsSourceOrder(value: List<LyricsSource>) {
        lyricsSourceOrder.value = value
        prefs.edit {putString(KEY_LYRICS_SOURCE_ORDER, value.joinToString(",") { it.name })}
    }

    /**
     * A named source dropped from the stored order — an upgrade removed or
     * hid it since it was saved — falls out on read; one added since slots in
     * after its declared neighbour (see [LyricsSource.ordered]), so a fresh
     * install and an upgraded one agree on where a new source lands until the
     * user says otherwise.
     */
    private fun readLyricsSourceOrder(): List<LyricsSource> {
        val stored = prefs.getString(KEY_LYRICS_SOURCE_ORDER, null)
            ?: return LyricsSource.offered
        val saved = stored.split(",")
            .mapNotNull { name -> LyricsSource.entries.firstOrNull { it.name == name } }
        return LyricsSource.ordered(saved)
    }

    fun setPrioritizeSyllableSync(value: Boolean) {
        prioritizeSyllableSync.value = value
        prefs.edit {putBoolean(KEY_PRIORITIZE_SYLLABLE_SYNC, value)}
    }

    fun setPaxSenixApiKey(value: String) {
        val normalized = com.music.audify.data.lyrics.normalizePaxSenixApiKey(value)
        paxSenixApiKey.value = normalized
        prefs.edit {putString(KEY_PAXSENIX_API_KEY, normalized)}
        com.music.audify.data.lyrics.PaxSenix.setApiKey(normalized)
    }

    /**
     * Puts the source list, its order and [prioritizeSyllableSync] back the
     * way a fresh install finds them. [syncedLyrics] itself is left alone —
     * this is "start over on *which* lyrics", not "turn lyrics off".
     */
    fun resetLyricsSourceSettings() {
        setLyricsSources(LyricsSource.offered.toSet())
        setLyricsSourceOrder(LyricsSource.offered)
        setPrioritizeSyllableSync(false)
    }

    fun setAnimatedCanvas(value: Boolean) {
        animatedCanvas.value = value
        prefs.edit {putBoolean(KEY_ANIMATED_CANVAS, value)}
    }

    fun setCanvasOverCellular(value: Boolean) {
        canvasOverCellular.value = value
        prefs.edit {putBoolean(KEY_CANVAS_OVER_CELLULAR, value)}
    }

    fun setSpotifyCanvasAutoHide(value: Boolean) {
        spotifyCanvasAutoHide.value = value
        prefs.edit {putBoolean(KEY_SPOTIFY_CANVAS_AUTO_HIDE, value)}
    }

    fun setPrioritizeSpotifyCanvas(value: Boolean) {
        prioritizeSpotifyCanvas.value = value
        prefs.edit {putBoolean(KEY_PRIORITIZE_SPOTIFY_CANVAS, value)}
    }

    fun setSignatureApiEnabled(value: Boolean) {
        signatureApiEnabled.value = value
        prefs.edit {putBoolean(KEY_SIGNATURE_API_ENABLED, value)}
    }

    /** Normalizes (trim + strip trailing slash); blank forgets the provider. */
    fun setSignatureApiUrl(value: String) {
        val normalized = normalizeSignatureApiUrl(value)
        signatureApiUrl.value = normalized
        prefs.edit {putString(KEY_SIGNATURE_API_URL, normalized)}
    }

    fun setFullBleedArtwork(value: Boolean) {
        fullBleedArtwork.value = value
        prefs.edit {putBoolean(KEY_FULL_BLEED_ARTWORK, value)}
    }

    fun setLegacyMeshGradient(value: Boolean) {
        legacyMeshGradient.value = value
        prefs.edit {putBoolean(KEY_LEGACY_MESH_GRADIENT, value)}
    }

    fun setLastPlayerScreen(value: LastPlayerScreen) {
        if (lastPlayerScreen.value == value) return
        lastPlayerScreen.value = value
        prefs.edit {putString(KEY_LAST_PLAYER_SCREEN, value.name)}
    }

    /**
     * Clamped to [DEFAULT_CACHE_LIMIT_BYTES]..[MAX_CACHE_LIMIT_BYTES] — the floor is the default, not zero.
     * Anything past [MAX_CACHE_LIMIT_BYTES] is [UNLIMITED_CACHE_LIMIT_BYTES].
     */
    fun setAudioCacheLimitBytes(value: Long) {
        val clamped = clampCacheLimit(value)
        audioCacheLimitBytes.value = clamped
        prefs.edit {putLong(KEY_CACHE_LIMIT, clamped)}
    }

    private fun clampCacheLimit(value: Long): Long =
        if (value > MAX_CACHE_LIMIT_BYTES) UNLIMITED_CACHE_LIMIT_BYTES
        else value.coerceAtLeast(DEFAULT_CACHE_LIMIT_BYTES)

    /** Clamped to [MIN_CANVAS_CACHE_LIMIT_BYTES]..[MAX_CANVAS_CACHE_LIMIT_BYTES]. */
    fun setCanvasCacheLimitBytes(value: Long) {
        val clamped = value.coerceIn(MIN_CANVAS_CACHE_LIMIT_BYTES, MAX_CANVAS_CACHE_LIMIT_BYTES)
        canvasCacheLimitBytes.value = clamped
        prefs.edit {putLong(KEY_CANVAS_CACHE_LIMIT, clamped)}
    }

    fun setShowCacheFolder(value: Boolean) {
        showCacheFolder.value = value
        prefs.edit {putBoolean(KEY_SHOW_CACHE_FOLDER, value)}
    }

    fun setLastfmEnabled(value: Boolean) {
        lastfmEnabled.value = value
        prefs.edit {putBoolean(KEY_LASTFM_ENABLED, value)}
    }

    fun setLastfmUsername(value: String) {
        lastfmUsername.value = value
        prefs.edit {putString(KEY_LASTFM_USERNAME, value)}
    }

    fun setLastfmSessionKey(value: String) {
        lastfmSessionKey.value = value
        prefs.edit {putString(KEY_LASTFM_SESSION_KEY, value)}
    }

    fun setLastfmApiKey(value: String) {
        lastfmApiKey.value = value
        prefs.edit {putString(KEY_LASTFM_API_KEY, value)}
    }

    fun setLastfmSecret(value: String) {
        lastfmSecret.value = value
        prefs.edit {putString(KEY_LASTFM_SECRET, value)}
    }

    fun setLastfmEndpoint(value: String) {
        lastfmEndpoint.value = value
        prefs.edit {putString(KEY_LASTFM_ENDPOINT, value)}
    }

    fun setSpotifySpdcToken(value: String) {
        spotifySpdcToken.value = value
        prefs.edit {putString(KEY_SPOTIFY_SPDC_TOKEN, value)}
        SpotifyToken.invalidate()
    }

    fun setLastfmScrobbleEnabled(value: Boolean) {
        lastfmScrobbleEnabled.value = value
        if (!value) lastfmNowPlaying.value = false
        prefs.edit {
                putBoolean(KEY_LASTFM_SCROBBLE_ENABLED, value)
                .putBoolean(KEY_LASTFM_NOW_PLAYING, if (value) lastfmNowPlaying.value else false)
            }
    }

    fun setLastfmNowPlaying(value: Boolean) {
        if (!lastfmScrobbleEnabled.value && value) return
        lastfmNowPlaying.value = value
        prefs.edit {putBoolean(KEY_LASTFM_NOW_PLAYING, value)}
    }

    fun setOutputPcmMode(value: OutputPcmMode) {
        outputPcmMode.value = value
        prefs.edit {putString(KEY_OUTPUT_PCM_MODE, value.name)}
    }

    fun setPreferUsbDac(value: Boolean) {
        preferUsbDac.value = value
        prefs.edit {putBoolean(KEY_PREFER_USB_DAC, value)}
    }

    fun setBitPerfectMode(value: BitPerfectMode) {
        bitPerfectMode.value = value
        prefs.edit {putString(KEY_BIT_PERFECT_MODE, value.name)}
    }

    fun setUsbAllowFixedVolume(value: Boolean) {
        usbAllowFixedVolume.value = value
        prefs.edit {putBoolean(KEY_USB_ALLOW_FIXED_VOLUME, value)}
    }

    fun setConvertForLimitedDac(value: Boolean) {
        convertForLimitedDac.value = value
        prefs.edit {putBoolean(KEY_CONVERT_FOR_LIMITED_DAC, value)}
    }

    fun setLoudnessOffOnSpeaker(value: Boolean) {
        loudnessOffOnSpeaker.value = value
        prefs.edit {putBoolean(KEY_LOUDNESS_OFF_ON_SPEAKER, value)}
    }

    fun setLoudnessNormalization(value: Boolean) {
        loudnessNormalization.value = value
        prefs.edit {putBoolean(KEY_LOUDNESS_NORMALIZATION, value)}
    }

    fun setExportDownloads(value: Boolean) {
        exportDownloads.value = value
        prefs.edit {putBoolean(KEY_EXPORT_DOWNLOADS, value)}
    }

    fun setAutoDownloadLikedSongs(value: Boolean) {
        autoDownloadLikedSongs.value = value
        prefs.edit { putBoolean(KEY_AUTO_DOWNLOAD_LIKED_SONGS, value) }
    }

    fun setDownloadLikedSongs(value: Boolean) {
        downloadLikedSongs.value = value
        prefs.edit { putBoolean(KEY_DOWNLOAD_LIKED_SONGS, value) }
    }

    fun setSyncDownloadedPlaylists(value: Boolean) {
        syncDownloadedPlaylists.value = value
        prefs.edit {putBoolean(KEY_SYNC_DOWNLOADED_PLAYLISTS, value)}
    }

    fun setLastfmPrimaryArtistOnly(value: Boolean) {
        lastfmPrimaryArtistOnly.value = value
        prefs.edit {putBoolean(KEY_LASTFM_PRIMARY_ARTIST_ONLY, value)}
    }

    fun setScrobbleMinDuration(value: Int) {
        scrobbleMinDuration.value = value
        prefs.edit {putInt(KEY_SCROBBLE_MIN_DURATION, value)}
    }

    fun setScrobbleDelayPercent(value: Float) {
        scrobbleDelayPercent.value = value
        prefs.edit {putFloat(KEY_SCROBBLE_DELAY_PERCENT, value)}
    }

    fun setScrobbleDelaySeconds(value: Int) {
        scrobbleDelaySeconds.value = value
        prefs.edit {putInt(KEY_SCROBBLE_DELAY_SECONDS, value)}
    }

    fun setListenBrainzEnabled(value: Boolean) {
        listenBrainzEnabled.value = value
        prefs.edit {putBoolean(KEY_LISTENBRAINZ_ENABLED, value)}
    }

    fun setListenBrainzToken(value: String) {
        listenBrainzToken.value = value
        prefs.edit {putString(KEY_LISTENBRAINZ_TOKEN, value)}
    }

    fun setListenBrainzPrimaryArtistOnly(value: Boolean) {
        listenBrainzPrimaryArtistOnly.value = value
        prefs.edit {putBoolean(KEY_LISTENBRAINZ_PRIMARY_ARTIST_ONLY, value)}
    }

    /** Menyimpan override profil; foto lama yang diganti ikut dihapus dari penyimpanan. */
    fun setProfileOverride(owner: String, name: String, photoPath: String) {
        val oldPhoto = profileOverride.value.photoPath
        profileOverride.value = ProfileOverride(owner, name, photoPath)
        prefs.edit {
                putString(KEY_PROFILE_OWNER, owner)
                .putString(KEY_PROFILE_NAME, name)
                .putString(KEY_PROFILE_PHOTO, photoPath)
            }
        if (oldPhoto.isNotEmpty() && oldPhoto != photoPath) runCatching { java.io.File(oldPhoto).delete() }
    }

    fun clearProfileOverride() = setProfileOverride("", "", "")

    fun setDiscordRpcEnabled(value: Boolean) {
        discordRpcEnabled.value = value
        prefs.edit {putBoolean(KEY_DISCORD_RPC_ENABLED, value)}
    }

    fun setDiscordUseDetails(value: Boolean) {
        discordUseDetails.value = value
        prefs.edit {putBoolean(KEY_DISCORD_USE_DETAILS, value)}
    }

    fun setDiscordShowAudioQuality(value: Boolean) {
        discordShowAudioQuality.value = value
        prefs.edit {putBoolean(KEY_DISCORD_SHOW_AUDIO_QUALITY, value)}
    }

    fun setDiscordShowTrackTitle(value: Boolean) {
        discordShowTrackTitle.value = value
        prefs.edit { putBoolean(KEY_DISCORD_SHOW_TRACK_TITLE, value) }
    }

    fun setDiscordShowArtist(value: Boolean) {
        discordShowArtist.value = value
        prefs.edit { putBoolean(KEY_DISCORD_SHOW_ARTIST, value) }
    }

    fun setDiscordShowAlbum(value: Boolean) {
        discordShowAlbum.value = value
        prefs.edit { putBoolean(KEY_DISCORD_SHOW_ALBUM, value) }
    }

    fun setDiscordShowArtwork(value: Boolean) {
        discordShowArtwork.value = value
        prefs.edit { putBoolean(KEY_DISCORD_SHOW_ARTWORK, value) }
    }

    fun setDiscordShowElapsedTime(value: Boolean) {
        discordShowElapsedTime.value = value
        prefs.edit { putBoolean(KEY_DISCORD_SHOW_ELAPSED_TIME, value) }
    }

    fun setDiscordHideWhenPaused(value: Boolean) {
        discordHideWhenPaused.value = value
        prefs.edit { putBoolean(KEY_DISCORD_HIDE_WHEN_PAUSED, value) }
    }

    fun setDiscordAdvancedMode(value: Boolean) {
        discordAdvancedMode.value = value
        prefs.edit {putBoolean(KEY_DISCORD_ADVANCED_MODE, value)}
    }

    fun setDiscordActivityType(value: String) {
        discordActivityType.value = value
        prefs.edit {putString(KEY_DISCORD_ACTIVITY_TYPE, value)}
    }

    fun setDiscordButton1Text(value: String) {
        discordButton1Text.value = value
        prefs.edit {putString(KEY_DISCORD_BUTTON_1_TEXT, value)}
    }

    fun setDiscordButton1Visible(value: Boolean) {
        discordButton1Visible.value = value
        prefs.edit {putBoolean(KEY_DISCORD_BUTTON_1_VISIBLE, value)}
    }

    fun setDiscordButton2Text(value: String) {
        discordButton2Text.value = value
        prefs.edit {putString(KEY_DISCORD_BUTTON_2_TEXT, value)}
    }

    fun setDiscordButton2Visible(value: Boolean) {
        discordButton2Visible.value = value
        prefs.edit {putBoolean(KEY_DISCORD_BUTTON_2_VISIBLE, value)}
    }

    fun setReplayGenres(value: Boolean) {
        replayGenres.value = value
        prefs.edit {putBoolean(KEY_REPLAY_GENRES, value)}
    }

    fun setShowWeeklyRecapCard(value: Boolean) {
        showWeeklyRecapCard.value = value
        prefs.edit {putBoolean(KEY_SHOW_WEEKLY_RECAP_CARD, value)}
    }

    fun setShowTimeAwareShelf(value: Boolean) {
        showTimeAwareShelf.value = value
        prefs.edit {putBoolean(KEY_SHOW_TIME_AWARE_SHELF, value)}
    }

    fun setWeeklyRecapSeenWeek(value: Long) {
        weeklyRecapSeenWeek.value = value
        prefs.edit {putLong(KEY_WEEKLY_RECAP_SEEN_WEEK, value)}
    }

    fun setFilterNonMusicAudio(value: Boolean) {
        filterNonMusicAudio.value = value
        prefs.edit {putBoolean(KEY_FILTER_NON_MUSIC_AUDIO, value)}
    }

    fun setHideExplicitContent(value: Boolean) {
        hideExplicitContent.value = value
        prefs.edit {putBoolean(KEY_HIDE_EXPLICIT_CONTENT, value)}
    }

    fun setLocalMusicSort(value: LocalMusicSort) {
        localMusicSort.value = value
        prefs.edit {putString(KEY_LOCAL_MUSIC_SORT, value.name)}
    }

    fun setDownloadedMusicSort(value: LocalMusicSort) {
        downloadedMusicSort.value = value
        prefs.edit {putString(KEY_DOWNLOADED_MUSIC_SORT, value.name)}
    }

    fun setLibrarySort(value: LibrarySort) {
        librarySort.value = value
        prefs.edit {putString(KEY_LIBRARY_SORT, value.name)}
    }

    fun setDetailSongSort(browseId: String, value: SongSort) {
        detailSongSorts.value = detailSongSorts.value + (browseId to value)
        prefs.edit {putString(
                KEY_DETAIL_SONG_SORTS,
                detailSongSorts.value.entries.joinToString(",") { (id, sort) -> "$id=${sort.name}" },
        )}
    }

    fun setLocalMusicViewType(value: LibraryViewType) {
        localMusicViewType.value = value
        prefs.edit {putString(KEY_LOCAL_MUSIC_VIEW_TYPE, value.name)}
    }

    fun setDownloadedMusicViewType(value: LibraryViewType) {
        downloadedMusicViewType.value = value
        prefs.edit {putString(KEY_DOWNLOADED_MUSIC_VIEW_TYPE, value.name)}
    }

    fun setLocalMusicFolderUri(value: String) {
        localMusicFolderUri.value = value
        prefs.edit {putString(KEY_LOCAL_MUSIC_FOLDER_URI, value)}
    }

    fun setDownloadFolderUri(value: String) {
        downloadFolderUri.value = value
        prefs.edit {putString(KEY_DOWNLOAD_FOLDER_URI, value)}
    }

    fun setWebDavUrl(value: String) {
        val normalized = value.trim().trimEnd('/')
        webdavUrl.value = normalized
        prefs.edit {putString(KEY_WEBDAV_URL, normalized)}
        publishWebDavAuth()
    }

    fun setWebDavUsername(value: String) {
        val normalized = value.trim()
        webdavUsername.value = normalized
        prefs.edit {putString(KEY_WEBDAV_USERNAME, normalized)}
        publishWebDavAuth()
    }

    /** Writes through to the encrypted store; pass "" to forget. */
    fun setWebDavPassword(value: String) {
        webdavPassword.value = value
        authStore.webdavPassword = value.ifEmpty { null }
        publishWebDavAuth()
    }

    fun clearWebDav() {
        setWebDavUrl("")
        setWebDavUsername("")
        setWebDavPassword("")
    }

    fun setSmbHost(value: String) {
        val normalized = value.trim()
        smbHost.value = normalized
        prefs.edit {putString(KEY_SMB_HOST, normalized)}
        publishSmbAuth()
    }

    fun setSmbShare(value: String) {
        val normalized = value.trim().trim('/')
        smbShare.value = normalized
        prefs.edit {putString(KEY_SMB_SHARE, normalized)}
        publishSmbAuth()
    }

    fun setSmbBasePath(value: String) {
        val normalized = value.trim().trim('/')
        smbBasePath.value = normalized
        prefs.edit {putString(KEY_SMB_BASE_PATH, normalized)}
        publishSmbAuth()
    }

    fun setSmbUsername(value: String) {
        val normalized = value.trim()
        smbUsername.value = normalized
        prefs.edit {putString(KEY_SMB_USERNAME, normalized)}
        publishSmbAuth()
    }

    /** Writes through to the encrypted store; pass "" to forget. */
    fun setSmbPassword(value: String) {
        smbPassword.value = value
        authStore.smbPassword = value.ifEmpty { null }
        publishSmbAuth()
    }

    fun clearSmb() {
        setSmbHost("")
        setSmbShare("")
        setSmbBasePath("")
        setSmbUsername("")
        setSmbPassword("")
    }

    private fun publishSmbAuth() {
        if (!this::prefs.isInitialized || !this::authStore.isInitialized) return
        com.music.audify.data.smb.SmbAuth.update(
            smbHost.value,
            smbShare.value,
            smbBasePath.value,
            smbUsername.value,
            smbPassword.value,
        )
    }

    private fun publishWebDavAuth() {
        if (!this::prefs.isInitialized || !this::authStore.isInitialized) return
        com.music.audify.data.webdav.WebDavAuth.update(
            webdavUrl.value,
            webdavUsername.value,
            webdavPassword.value,
        )
    }

    private fun readLocalMusicSort(key: String): LocalMusicSort =
        prefs.getString(key, null)
            ?.let { saved -> LocalMusicSort.entries.firstOrNull { it.name == saved } }
            ?: LocalMusicSort.TITLE_ASC

    private fun readLibraryViewType(key: String): LibraryViewType =
        prefs.getString(key, null)
            ?.let { saved -> LibraryViewType.entries.firstOrNull { it.name == saved } }
            ?: LibraryViewType.LIST

    /**
     * Pins or unpins [browseId], returning whether it is pinned afterwards.
     *
     * Pinning past [MAX_PINNED_PLAYLISTS] is refused rather than evicting the
     * oldest pin: a silent swap would mean a playlist someone pinned on purpose
     * disappears from the row without them ever having touched it, the moment
     * they pin a sixth. Unpinning always succeeds.
     */
    fun togglePinnedPlaylist(browseId: String, card: PinnedCard? = null): Boolean {
        val current = pinnedPlaylists.value
        val updated = when {
            browseId in current -> current - browseId
            current.size >= MAX_PINNED_PLAYLISTS -> return false
            else -> current + browseId
        }
        pinnedPlaylists.value = updated
        prefs.edit { putString(KEY_PINNED_PLAYLISTS, updated.joinToString(",")) }
        val pinned = browseId in updated
        val cards = pinnedCards.value
        val nextCards = when {
            !pinned -> cards - browseId
            card != null -> cards + (browseId to card)
            else -> cards
        }
        if (nextCards != cards) {
            pinnedCards.value = nextCards
            prefs.edit {putString(KEY_PINNED_CARDS, encodePinnedCards(nextCards))}
        }
        return pinned
    }

    private fun readDetailSongSorts(): Map<String, SongSort> =
        prefs.getString(KEY_DETAIL_SONG_SORTS, null)
            ?.split(",")
            ?.mapNotNull { entry ->
                val id = entry.substringBefore('=', "")
                val sort = SongSort.entries.firstOrNull { it.name == entry.substringAfter('=', "") }
                if (id.isBlank() || sort == null) null else id to sort
            }
            ?.toMap()
            ?: emptyMap()

    private fun readPinnedPlaylists(): List<String> {
        val stored = prefs.getString(KEY_PINNED_PLAYLISTS, null) ?: return emptyList()
        return stored.split(",").filter { it.isNotBlank() }
    }

    private fun readPinnedCards(): Map<String, PinnedCard> {
        val raw = prefs.getString(KEY_PINNED_CARDS, null) ?: return emptyMap()
        return runCatching {
            val root = org.json.JSONObject(raw)
            val cards = LinkedHashMap<String, PinnedCard>()
            val ids = root.keys()
            while (ids.hasNext()) {
                val id = ids.next()
                val entry = root.getJSONObject(id)
                cards[id] = PinnedCard(
                    title = entry.optString("t"),
                    subtitle = entry.optString("s"),
                    thumbnailUrl = entry.optString("u").takeIf { it.isNotBlank() },
                )
            }
            cards as Map<String, PinnedCard>
        }.getOrDefault(emptyMap())
    }

    private fun encodePinnedCards(cards: Map<String, PinnedCard>): String {
        val root = org.json.JSONObject()
        cards.forEach { (id, card) ->
            root.put(
                id,
                org.json.JSONObject()
                    .put("t", card.title)
                    .put("s", card.subtitle)
                    .put("u", card.thumbnailUrl ?: ""),
            )
        }
        return root.toString()
    }

    // ── Backup ──────────────────────────────────────────────────────────────

    /**
     * Every stored preference, for an export.
     *
     * Read off the preference file wholesale rather than assembled from the
     * flows above, so a setting added in a later build is in the backup the day
     * it is added instead of the day somebody remembers to list it here. What is
     * *left out* is therefore the part worth stating explicitly, and it is
     * [SECRETS]: an export is a file the user is about to put in Drive or a
     * chat, and a scrobbler session key or an API secret in it is a credential
     * that has left the device in plain text. Signing back in after a restore is
     * a minute; a leaked session key is not recoverable at all.
     *
     * The retired Discord user token is purged during AuthStore initialization;
     * the official Social SDK does not require a user credential.
     */
    fun exportPrefs(): Map<String, Any?> {
        if (!this::prefs.isInitialized) return emptyMap()
        return prefs.all.filterKeys { it !in SECRETS && it !in DEVICE_LOCAL }
    }

    /**
     * Replaces the preference file with [values] and re-reads it.
     *
     * A replace, not a merge: a partial restore leaves a device holding half of
     * one configuration and half of another, which is the one outcome nobody
     * asked for. Keys in [SECRETS] survive untouched — they were never in the
     * file being restored from, and clearing them would sign the user out of
     * services the backup has nothing to say about.
     */
    fun importPrefs(values: Map<String, Any?>) {
        if (!this::prefs.isInitialized) return
        val kept = prefs.all.filterKeys { it in SECRETS || it in DEVICE_LOCAL }
        prefs.edit {
            clear()
            val incoming = values.filterKeys { it !in SECRETS && it !in DEVICE_LOCAL }
            (kept + incoming).forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    else -> Unit
                }
            }
        }
        reload()
    }

    /**
     * Preferences an export must not carry — credentials, not configuration.
     * See [exportPrefs].
     */
    private val SECRETS = setOf(
        KEY_LASTFM_SESSION_KEY,
        KEY_LASTFM_API_KEY,
        KEY_LASTFM_SECRET,
        KEY_LISTENBRAINZ_TOKEN,
        KEY_SPOTIFY_SPDC_TOKEN,
        KEY_PAXSENIX_API_KEY,
    )

    /**
     * Preferences that describe *this device* rather than this configuration,
     * and so are neither exported nor overwritten by an import.
     *
     * [Downloads][com.music.audify.download.Downloads] keeps its record of
     * what is saved in this same preference file, and that record is a list of
     * files on this phone's storage. Carrying it into a backup would restore a
     * folder full of tracks that are not here; clearing it on import would leave
     * the files on disk with nothing pointing at them, which is worse — the
     * Downloads page would read as empty while the space stayed used.
     */
    private val DEVICE_LOCAL = setOf(
        "downloaded_tracks",
        "downloaded_tracks_metadata",
        "downloaded_collections",
        KEY_LOCAL_MUSIC_FOLDER_URI,
        KEY_DOWNLOAD_FOLDER_URI,
        // A wallpaper points to a provider grant or a file in this install's
        // private storage; neither URI is portable in a user-exported backup.
        KEY_THEME_BACKGROUND_URI,
        KEY_LAST_VERSION_CODE,
        KEY_ONBOARDING_COMPLETE,
    )

    const val DEFAULT_CACHE_LIMIT_BYTES = 512L * 1024 * 1024
    const val MAX_CACHE_LIMIT_BYTES = 10L * 1024 * 1024 * 1024

    const val DEFAULT_CANVAS_CACHE_LIMIT_BYTES = 256L * 1024 * 1024
    const val MIN_CANVAS_CACHE_LIMIT_BYTES = 128L * 1024 * 1024
    const val MAX_CANVAS_CACHE_LIMIT_BYTES = 5L * 1024 * 1024 * 1024

    /** The cache limit with no ceiling: only free storage bounds it. */
    const val UNLIMITED_CACHE_LIMIT_BYTES = Long.MAX_VALUE

    const val MIN_LYRICS_OFFSET_MS = -5_000
    const val MAX_LYRICS_OFFSET_MS = 5_000

    const val DEFAULT_UPGRADE_LENGTH_SLACK_SECONDS = 5
    const val MIN_UPGRADE_LENGTH_SLACK_SECONDS = 0
    const val MAX_UPGRADE_LENGTH_SLACK_SECONDS = 10

    // Resolving 240 Hz against the current display's supported modes selects
    // its highest available rate (60/90/120/144/165/240 Hz, etc.).
    private const val DEFAULT_PERFORMANCE_REFRESH_RATE = 240

    private fun normalizePerformanceRefreshRate(value: Int): Int =
        value.takeIf { it in 50..240 } ?: DEFAULT_PERFORMANCE_REFRESH_RATE

    fun normalizeSignatureApiUrl(raw: String): String =
        raw.trim().trimEnd('/').take(500)

    private const val KEY_QUALITY_LEGACY = "audio_quality"
    private const val KEY_QUALITY_WIFI = "audio_quality_wifi"
    private const val KEY_QUALITY_CELLULAR = "audio_quality_cellular"
    private const val KEY_UPGRADE_LENGTH_SLACK_SECONDS = "upgrade_length_slack_seconds"
    private const val KEY_QUALITY_DOWNLOAD = "audio_quality_download"
    private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
    private const val KEY_AUTOMATIC_UPDATE_CHECKS = "automatic_update_checks"
    private const val KEY_INCLUDE_PRERELEASE_UPDATES = "include_prerelease_updates"
    private const val KEY_BETA_UPDATES_ENABLED = "beta_updates_enabled"
    private const val KEY_EXPORT_DOWNLOADS = "export_downloads"
    private const val KEY_AUTO_DOWNLOAD_LIKED_SONGS = "auto_download_liked_songs"
    private const val KEY_SYNC_DOWNLOADED_PLAYLISTS = "sync_downloaded_playlists"
    private const val KEY_DOWNLOAD_LIKED_SONGS = "download_liked_songs"
    private const val KEY_CROSSFADE = "crossfade_seconds"
    private const val KEY_CROSSFADE_ON_SKIP = "crossfade_on_skip"
    private const val KEY_SMART_FADE = "smart_fade_enabled"
    private const val KEY_AUTOMIX_PERFORMANCE_MODE = "automix_performance_mode"
    private const val KEY_SKIP_SILENCE = "skip_silence"
    private const val KEY_PAUSE_ON_MUTE = "pause_on_mute"
    private const val KEY_RESUME_ON_BLUETOOTH = "resume_on_bluetooth"
    private const val KEY_VIDEO_THUMBNAIL_RATIO_MIGRATED = "video_thumbnail_ratio_9_16_migrated"
    private const val KEY_OUTPUT_PCM_MODE = "output_pcm_mode"
    private const val KEY_PREFER_USB_DAC = "prefer_usb_dac"
    private const val KEY_BIT_PERFECT_MODE = "bit_perfect_mode"
    private const val KEY_USB_ALLOW_FIXED_VOLUME = "usb_allow_fixed_volume"
    private const val KEY_CONVERT_FOR_LIMITED_DAC = "convert_for_limited_dac"
    private const val KEY_LOUDNESS_NORMALIZATION = "loudness_normalization"
    private const val KEY_LOUDNESS_OFF_ON_SPEAKER = "loudness_off_on_speaker"
    private const val KEY_DOLBY_ATMOS = "dolby_atmos"
    private const val KEY_DIRECT_SOURCE_STREAM = "direct_source_stream"
    private const val KEY_SPATIAL_AUDIO = "spatial_audio"
    private const val KEY_SPATIAL_AUDIO_BY_TRACK = "spatial_audio_by_track"
    private const val MAX_SPATIAL_TRACKS = 5000
    private const val KEY_EQ_ENABLED = "equalizer_enabled"
    private const val KEY_EQ_MODE = "equalizer_mode"
    private const val KEY_EQ_TONE_X = "equalizer_tone_x"
    private const val KEY_EQ_TONE_Y = "equalizer_tone_y"
    private const val KEY_EQ_FOCUSED = "equalizer_focused"
    private const val KEY_EQ_BALANCE = "equalizer_balance"
    private const val KEY_EQ_BANDS = "equalizer_bands"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_APP_FONT = "app_font"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_THEME_PALETTE = "theme_palette"
    private const val KEY_CUSTOM_THEME_PACKS = "custom_theme_packs"
    private const val KEY_THEME_BACKGROUND_URI = "theme_background_uri"
    private const val KEY_BACKGROUND_STYLE = "background_style"
    private const val KEY_AMOLED_DISPLAY_COLORS = "amoled_display_colors"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_NERD_STATS = "show_nerd_stats"
    private const val KEY_LISTEN_TOGETHER_BUTTON = "show_listen_together_button"
    // v2 intentionally defaults hidden; do not inherit the earlier always-on tab.
    private const val KEY_LISTEN_TOGETHER_TAB = "show_listen_together_tab_v2"
    private const val KEY_CACHE_LIMIT = "audio_cache_limit_bytes"
    private const val KEY_CANVAS_CACHE_LIMIT = "canvas_cache_limit_bytes"
    private const val KEY_REDUCE_ANIMATION = "reduce_animation"
    private const val KEY_COVER_SLIDE_ANIMATION = "cover_slide_animation"
    private const val KEY_COVER_FLUID_ANIMATION = "cover_fluid_animation"
    private const val KEY_COVER_MIX_ONLY_EFFECTS = "cover_mix_only_effects"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    private const val KEY_HIGH_PERFORMANCE_MODE = "high_performance_mode"
    private const val KEY_HIGH_PERFORMANCE_DEFAULT_APPLIED = "high_performance_default_applied"
    private const val KEY_PERFORMANCE_REFRESH_RATE = "performance_refresh_rate"
    private const val KEY_STOP_ON_TASK_REMOVED = "stop_on_task_removed"
    private const val KEY_HIDE_VOLUME_BAR = "hide_volume_bar"
    private const val KEY_HIDE_STATUS_BAR_IN_PLAYER = "hide_status_bar_in_player"
    private const val KEY_HIDE_SONG_STATUS = "hide_song_status"
    private const val KEY_SHOW_QUEUE_HISTORY = "show_queue_history"
    const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
    private const val KEY_SWIPE_TO_PLAY_NEXT = "swipe_to_play_next"
    private const val KEY_DONT_REPEAT_SUGGESTIONS = "dont_repeat_suggestions"
    private const val KEY_SMART_VERSION_ALIGNMENT = "smart_version_alignment"
    private const val KEY_AUTO_MUSIC_ONLY = "auto_music_only"
    private const val KEY_HIDE_HOME_RECENTS = "hide_home_recents"
    private const val KEY_HIDE_HOME_BRANDING = "hide_home_branding"
    private const val KEY_HIDE_HOME_VIDEOS = "hide_home_videos"
    private const val KEY_HIDE_HOME_TOP_PICKS = "hide_home_top_picks"
    private const val KEY_HIDE_HOME_MOODS = "hide_home_moods"
    private const val KEY_SQUARE_VIDEO_COVERS = "square_video_covers"
    private const val KEY_STILL_VIDEO_COVERS = "still_video_covers"
    private const val KEY_HOME_LAYOUT = "home_layout"
    private const val KEY_LIBRARY_ARTISTS_ROW = "library_artists_row"
    private const val KEY_LIBRARY_LAYOUT = "library_layout"
    private const val KEY_REDUCE_BLUR = "reduce_dynamic_blur"
    private const val KEY_LIQUID_GLASS = "liquid_glass"
    private const val KEY_FROSTED_GLASS = "frosted_glass"
    private const val KEY_GLASS_CLARITY = "glass_clarity"
    private const val KEY_GLASS_CLARITY_LEVEL = "glass_clarity_level"
    private const val KEY_GLASS_BLUR = "glass_custom_blur"
    private const val KEY_GLASS_SATURATION = "glass_custom_saturation"
    private const val KEY_GLASS_TINT = "glass_custom_tint"
    private const val KEY_GLASS_REFRACTION = "glass_custom_refraction"
    private const val KEY_GLASS_REFRACTION_WIDTH = "glass_custom_refraction_width"
    private const val KEY_GLASS_DEPTH = "glass_custom_depth"
    private const val KEY_GLASS_RAINBOW = "glass_custom_rainbow"
    private const val KEY_GLASS_RIM = "glass_custom_rim"
    private const val KEY_GLASS_SHADOW = "glass_custom_shadow"
    private const val KEY_GLASS_QUALITY = "glass_custom_quality"
    private const val KEY_GIND_REST_LENS = "glass_ind_rest_lens"
    private const val KEY_GIND_REST_REFRACTION = "glass_ind_rest_refraction"
    private const val KEY_GIND_REST_WIDTH = "glass_ind_rest_width"
    private const val KEY_GIND_LIFT_REFRACTION = "glass_ind_lift_refraction"
    private const val KEY_GIND_LIFT_WIDTH = "glass_ind_lift_width"
    private const val KEY_GIND_RAINBOW = "glass_ind_rainbow"
    private const val KEY_GIND_DEPTH = "glass_ind_depth"
    private const val KEY_GIND_BLUR = "glass_ind_blur"
    private const val KEY_GIND_SATURATION = "glass_ind_saturation"
    private const val KEY_GIND_BRIGHTNESS = "glass_ind_brightness"
    private const val KEY_GIND_FILL = "glass_ind_fill"
    private const val KEY_GIND_RIM = "glass_ind_rim"
    private const val KEY_GIND_INNER_SHADOW = "glass_ind_inner_shadow"
    private const val KEY_GIND_INNER_RADIUS = "glass_ind_inner_radius"
    private const val KEY_GIND_SHADOW = "glass_ind_shadow"
    private const val KEY_GIND_LIFT = "glass_ind_lift"
    private const val KEY_GIND_ICON_LIFT = "glass_ind_icon_lift"
    private const val KEY_GIND_SQUASH = "glass_ind_squash"
    private const val KEY_PROGRESSIVE_BLUR = "progressive_blur"
    private const val KEY_LYRICS_BLUR = "lyrics_blur"
    private const val KEY_LIQUID_FLOW = "liquid_flow_motion"
    private const val KEY_LYRICS_SPARKLE = "lyrics_sparkle"
    private const val KEY_PLAYBACK_RATE = "playback_rate"
    private const val KEY_PLAYBACK_TEMPO = "playback_tempo"
    private const val KEY_LYRICS_OFFSET_BY_TRACK = "lyrics_offset_by_track"
    private const val KEY_LYRICS_SPEED_BY_TRACK = "lyrics_speed_by_track"
    private const val MAX_LYRICS_OFFSET_TRACKS = 5000
    private const val KEY_SKIP_SEGMENTS_BY_TRACK = "skip_segments_by_track"
    private const val MAX_SKIP_SEGMENT_TRACKS = 5000
    private const val KEY_TRACK_PLAYBACK_WINDOWS = "track_playback_windows"
    private const val KEY_TRANSLATION_LANGUAGE = "translation_language"
    private const val KEY_ANIMATED_CANVAS = "animated_canvas"
    private const val KEY_CANVAS_OVER_CELLULAR = "canvas_over_cellular"
    private const val KEY_SPOTIFY_CANVAS_AUTO_HIDE = "spotify_canvas_auto_hide"
    private const val KEY_PRIORITIZE_SPOTIFY_CANVAS = "prioritize_spotify_canvas"
    private const val KEY_SIGNATURE_API_ENABLED = "signature_api_enabled"
    private const val KEY_SIGNATURE_API_URL = "signature_api_url"
    private const val KEY_FULL_BLEED_ARTWORK = "full_bleed_artwork"
    private const val KEY_LEGACY_MESH_GRADIENT = "legacy_mesh_gradient"
    private const val KEY_LAST_PLAYER_SCREEN = "last_player_screen"
    private const val KEY_SYNCED_LYRICS = "synced_lyrics"
    private const val KEY_LYRIC_STRIP = "lyric_strip"
    private const val KEY_LYRICS_SOURCES = "lyrics_sources"
    private const val KEY_LYRICS_SOURCES_SEEN = "lyrics_sources_seen"
    private const val KEY_LYRICS_SOURCE_ORDER = "lyrics_source_order"
    private const val KEY_PRIORITIZE_SYLLABLE_SYNC = "prioritize_syllable_sync"
    private const val KEY_PAXSENIX_API_KEY = "paxsenix_api_key"
    private const val KEY_REPLAY_GENRES = "replay_genres"
    private const val KEY_SHOW_WEEKLY_RECAP_CARD = "show_weekly_recap_card"
    private const val KEY_SHOW_TIME_AWARE_SHELF = "show_time_aware_shelf"
    private const val KEY_WEEKLY_RECAP_SEEN_WEEK = "weekly_recap_seen_week"
    private const val KEY_FILTER_NON_MUSIC_AUDIO = "filter_non_music_audio"
    private const val KEY_HIDE_EXPLICIT_CONTENT = "hide_explicit_content"
    private const val KEY_SHOW_CACHE_FOLDER = "show_cache_folder"
    private const val KEY_LOCAL_MUSIC_SORT = "local_music_sort"
    private const val KEY_DOWNLOADED_MUSIC_SORT = "downloaded_music_sort"
    private const val KEY_LIBRARY_SORT = "library_sort"
    private const val KEY_DETAIL_SONG_SORTS = "detail_song_sorts"
    private const val KEY_LOCAL_MUSIC_VIEW_TYPE = "local_music_view_type"
    private const val KEY_DOWNLOADED_MUSIC_VIEW_TYPE = "downloaded_music_view_type"
    private const val KEY_LOCAL_MUSIC_FOLDER_URI = "local_music_folder_uri"
    private const val KEY_DOWNLOAD_FOLDER_URI = "download_folder_uri"
    private const val KEY_WEBDAV_URL = "webdav_url"
    private const val KEY_WEBDAV_USERNAME = "webdav_username"
    private const val KEY_SMB_HOST = "smb_host"
    private const val KEY_SMB_SHARE = "smb_share"
    private const val KEY_SMB_BASE_PATH = "smb_base_path"
    private const val KEY_SMB_USERNAME = "smb_username"
    private const val KEY_PINNED_PLAYLISTS = "pinned_playlists"
    private const val KEY_PINNED_CARDS = "pinned_cards"

    private const val KEY_LASTFM_ENABLED = "lastfm_enabled"
    private const val KEY_LASTFM_USERNAME = "lastfm_username"
    private const val KEY_LASTFM_SESSION_KEY = "lastfm_session_key"
    private const val KEY_LASTFM_API_KEY = "lastfm_api_key"
    private const val KEY_LASTFM_SECRET = "lastfm_secret"
    private const val KEY_LASTFM_ENDPOINT = "lastfm_endpoint"
    private const val KEY_LASTFM_SCROBBLE_ENABLED = "lastfm_scrobble_enabled"
    private const val KEY_LASTFM_NOW_PLAYING = "lastfm_now_playing"
    private const val KEY_LASTFM_PRIMARY_ARTIST_ONLY = "lastfm_primary_artist_only"
    private const val KEY_SCROBBLE_MIN_DURATION = "scrobble_min_duration"
    private const val KEY_SCROBBLE_DELAY_PERCENT = "scrobble_delay_percent"
    private const val KEY_SCROBBLE_DELAY_SECONDS = "scrobble_delay_seconds"
    private const val KEY_LISTENBRAINZ_ENABLED = "listenbrainz_enabled"
    private const val KEY_LISTENBRAINZ_TOKEN = "listenbrainz_token"
    private const val KEY_LISTENBRAINZ_PRIMARY_ARTIST_ONLY = "listenbrainz_primary_artist_only"
    private const val KEY_SPOTIFY_SPDC_TOKEN = "spotify_spdc_token"

    private const val KEY_PROFILE_OWNER = "profile_override_owner"
    private const val KEY_PROFILE_NAME = "profile_override_name"
    private const val KEY_PROFILE_PHOTO = "profile_override_photo"
    private const val KEY_DISCORD_RPC_ENABLED = "discord_rpc_enabled"
    private const val KEY_DISCORD_USE_DETAILS = "discord_use_details"
    private const val KEY_DISCORD_SHOW_AUDIO_QUALITY = "discord_show_audio_quality"
    private const val KEY_DISCORD_SHOW_TRACK_TITLE = "discord_show_track_title"
    private const val KEY_DISCORD_SHOW_ARTIST = "discord_show_artist"
    private const val KEY_DISCORD_SHOW_ALBUM = "discord_show_album"
    private const val KEY_DISCORD_SHOW_ARTWORK = "discord_show_artwork"
    private const val KEY_DISCORD_SHOW_ELAPSED_TIME = "discord_show_elapsed_time"
    private const val KEY_DISCORD_HIDE_WHEN_PAUSED = "discord_hide_when_paused"
    private const val KEY_DISCORD_ADVANCED_MODE = "discord_advanced_mode"
    private const val KEY_DISCORD_ACTIVITY_TYPE = "discord_activity_type"
    private const val KEY_DISCORD_BUTTON_1_TEXT = "discord_button_1_text"
    private const val KEY_DISCORD_BUTTON_1_VISIBLE = "discord_button_1_visible"
    private const val KEY_DISCORD_BUTTON_2_TEXT = "discord_button_2_text"
    private const val KEY_DISCORD_BUTTON_2_VISIBLE = "discord_button_2_visible"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_AUDIFY_PREFS_MIGRATED = "_audify_preferences_migrated_v1"
}



