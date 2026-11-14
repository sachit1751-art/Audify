package com.music.audify.ui.replay

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.music.audify.data.stats.ArtistFacts
import com.music.audify.data.stats.ListeningStats
import com.music.audify.data.stats.ReplayPeriod
import com.music.audify.data.stats.ReplaySummary
import com.music.audify.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Replay's state: which stretch of listening is being shown, and the
 * numbers for it.
 *
 * Held here rather than in [com.music.audify.ui.MainViewModel] because it is
 * only alive while the page is: the summary is a merge of a few files and is
 * cheap to make, and keeping a copy of every chart in a view model that outlives
 * the screen would hold artwork URLs and a few hundred rows for the rest of the
 * session in exchange for saving a hundred milliseconds nobody would notice.
 *
 * The *period* does survive, because it is a choice rather than a result.
 */
class ReplayState(
    val period: ReplayPeriod,
    val summary: ReplaySummary?,
    val loading: Boolean,
    /**
     * `MM/YY` of the first month this device recorded anything, for the card.
     *
     * Deliberately all-time rather than the open period's own first month: a
     * card that says "member since" has to mean since you started, and reading
     * it off the period would have it announce a new membership every time the
     * chips were switched to This month.
     */
    val memberSince: String?,
    /** The day the open month or year is read around: today, or an earlier one the listener stepped to. */
    val anchor: LocalDate = LocalDate.now(),
    val onAnchorChange: (LocalDate) -> Unit = {},
    /** The first month this device recorded anything, so stepping back knows where to stop. */
    val firstMonth: YearMonth? = null,
)

/**
 * @param active whether the Replay is open in any of its three forms — the
 *   page, the stories, the share sheet. The state is hoisted to the app so all
 *   three read one set of numbers, and this is what stops that hoisting from
 *   costing a file merge on every cold start for a page most launches never
 *   open. It also means reopening Replay re-reads: whatever has been played
 *   since is on it.
 */
@Composable
fun rememberReplayState(active: Boolean): Pair<ReplayState, (ReplayPeriod) -> Unit> {
    var period by rememberSaveable { mutableStateOf(ReplayPeriod.THIS_YEAR) }
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var memberSince by remember { mutableStateOf<String?>(null) }
    var anchorDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var firstMonth by remember { mutableStateOf<YearMonth?>(null) }
    val anchor = anchorDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        // A directory listing, so off the composition's thread.
        val first = withContext(Dispatchers.IO) { ListeningStats.months().firstOrNull() }
        firstMonth = first
        memberSince = first?.let { "%02d/%02d".format(Locale.ROOT, it.monthValue, it.year % 100) }
    }
    LaunchedEffect(period, anchor, active) {
        if (!active) return@LaunchedEffect
        // Only the first read shows a spinner. Switching period must not blank
        // the charts for the beat it takes to merge the files — that reads as
        // the page breaking rather than as it answering a different question.
        loading = summary == null
        summary = ListeningStats.summary(period, anchor)
        loading = false

        // Artist pictures and pages arrive after the page has been built — see
        // [ArtistFacts.revision] — so the charts are rebuilt when they do.
        //
        // Collected inside the effect rather than as composed state on purpose:
        // this function is called from the app's root, so a revision held as
        // state would recompose the whole tree every time a lookup landed, even
        // with the Replay closed. `collectLatest` gives the debounce for free —
        // a burst of lookups cancels each pending delay and only the last one
        // gets as far as a rebuild.
        ArtistFacts.revision.drop(1).collectLatest {
            delay(SETTLE_MILLIS)
            summary = ListeningStats.summary(period, anchor)
        }
    }
    return ReplayState(
        period = period,
        summary = summary,
        loading = loading,
        memberSince = memberSince,
        anchor = anchor,
        onAnchorChange = { day -> anchorDay = day.toEpochDay() },
        firstMonth = firstMonth,
    ) to
        { next: ReplayPeriod -> period = next }
}

/** Keep one recap available for the current Monday-based week until opened or dismissed. */
fun shouldShowWeeklyRecap(enabled: Boolean, hasData: Boolean, seenWeek: Long, currentWeek: Long): Boolean =
    enabled && hasData && seenWeek != currentWeek

/** How long a burst of artist lookups is allowed to settle before a rebuild. */
private const val SETTLE_MILLIS = 1_200L

/**
 * One run of a card's headline, and whether it is the emphasised part.
 *
 * The sentence lives here rather than in the story that draws it because it is
 * drawn twice — once on screen and once into the picture the share button
 * produces — and a card that says something different in the version people
 * send is worse than no picture at all.
 */
data class HeadlineRun(val text: String, val bold: Boolean)

private fun runs(vararg parts: Pair<String, Boolean>): List<HeadlineRun> =
    parts.map { HeadlineRun(it.first, it.second) }

/** The sentence at the top of [page]. */
fun ReplaySummary.storyHeadline(context: Context, page: ReplayStoryPage): List<HeadlineRun> = when (page) {
    ReplayStoryPage.INTRO -> context.twoPart(R.string.replay_story_intro_a, R.string.replay_story_intro_b)
    ReplayStoryPage.MINUTES -> context.twoPart(R.string.replay_story_minutes_a, R.string.replay_story_minutes_b)
    ReplayStoryPage.ARTISTS -> context.twoPart(R.string.replay_story_artist_a, R.string.replay_story_artist_b)
    ReplayStoryPage.SONGS -> context.twoPart(R.string.replay_story_songs_a, R.string.replay_story_songs_b)
    ReplayStoryPage.ALBUMS -> context.twoPart(R.string.replay_story_albums_a, R.string.replay_story_albums_b)
    ReplayStoryPage.GENRES -> context.twoPart(R.string.replay_story_personality_a, R.string.replay_story_personality_b)
    ReplayStoryPage.SUMMARY -> context.twoPart(R.string.replay_story_final_a, R.string.replay_story_final_b)
    ReplayStoryPage.HABITS -> habitsHeadline(context)
}

private fun Context.twoPart(first: Int, second: Int): List<HeadlineRun> {
    // Android memangkas spasi di ujung string resource, jadi spasi antar-bagian
    // ditambahkan di sini supaya "Lagu-lagu ini" + "menemani" tidak menempel.
    val a = getString(first)
    val lead = if (a.isEmpty() || a.last().isWhitespace()) a else "$a "
    return listOf(HeadlineRun(lead, false), HeadlineRun(getString(second), true))
}

private fun ReplaySummary.habitsHeadline(context: Context): List<HeadlineRun> = runs(
    context.getString(R.string.replay_habits_start) to false,
    " " to false,
    context.replayCount(distinctSongs, R.plurals.replay_song_count) to true,
    " " to false,
    context.getString(R.string.replay_habits_middle) to false,
    " " to false,
    context.replayCount(distinctArtists, R.plurals.replay_artist_count) to true,
    "." to false,
)

fun ReplayPeriod.localizedChip(context: Context): String = when (this) {
    ReplayPeriod.THIS_WEEK -> context.getString(R.string.replay_this_week)
    ReplayPeriod.THIS_MONTH -> context.getString(R.string.this_month)
    ReplayPeriod.THIS_YEAR -> context.getString(R.string.this_year)
    ReplayPeriod.ALL_TIME -> context.getString(R.string.all_time)
}

/** The period picker's short name for each stretch — see `PeriodPicker`. */
fun ReplayPeriod.localizedTab(context: Context): String = when (this) {
    ReplayPeriod.THIS_WEEK -> context.getString(R.string.replay_period_weekly)
    ReplayPeriod.THIS_MONTH -> context.getString(R.string.replay_period_monthly)
    ReplayPeriod.THIS_YEAR -> context.getString(R.string.replay_period_yearly)
    ReplayPeriod.ALL_TIME -> context.getString(R.string.replay_period_lifetime)
}

fun ReplaySummary.localizedLabel(context: Context): String = when (period) {
    ReplayPeriod.THIS_WEEK -> "This week"
    ReplayPeriod.THIS_MONTH -> DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        .format(anchor)
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    ReplayPeriod.THIS_YEAR -> anchor.year.toString()
    ReplayPeriod.ALL_TIME -> context.getString(R.string.all_time)
}

/**
 * Which cover a card is washed in.
 *
 * The three cards that are *about* something take that thing's artwork. The rest
 * walk a pool of everything on the Replay, so a run of eight cards is lit by
 * eight different records rather than by the top song eight times.
 */
fun ReplaySummary.storyArtwork(page: ReplayStoryPage): String? {
    val pool = (
        songs.map { it.song.thumbnailUrl } +
            artists.map { it.artworkUrl } +
            albums.map { it.artworkUrl }
        ).filterNotNull().distinct()
    val pinned = when (page) {
        ReplayStoryPage.SONGS -> songs.firstOrNull()?.song?.thumbnailUrl
        ReplayStoryPage.ARTISTS -> artists.firstOrNull()?.artworkUrl
        ReplayStoryPage.ALBUMS -> albums.firstOrNull()?.artworkUrl
        else -> null
    }
    if (pinned != null) return pinned
    if (pool.isEmpty()) return null
    return pool[page.ordinal % pool.size]
}

/**
 * How far a card's palette is turned around the colour wheel.
 *
 * The mesh is sampled from artwork, and a Replay is frequently four covers by
 * two artists with the same art direction — which is a run of eight cards in one
 * shade of blue. Rotating the hue per card is what makes the story *look* like a
 * story: every one arrives a different colour, and because only the hue moves,
 * the saturation and lightness the mesh was tuned for are untouched, so no card
 * comes out muddy or blown out.
 *
 * The steps are irregular rather than an even eighth of the wheel: an even walk
 * reads as a colour-picker demo, and the gaps here keep neighbours far enough
 * apart to be obviously different without the run looking mechanical.
 */
fun storyHue(page: ReplayStoryPage): Float = when (page) {
    ReplayStoryPage.INTRO -> 0f
    ReplayStoryPage.MINUTES -> 40f
    ReplayStoryPage.SONGS -> 95f
    ReplayStoryPage.ARTISTS -> 145f
    ReplayStoryPage.ALBUMS -> 195f
    ReplayStoryPage.GENRES -> 240f
    ReplayStoryPage.HABITS -> 285f
    ReplayStoryPage.SUMMARY -> 325f
}

enum class ReplayPersonalityKind { NIGHT_EXPLORER, MELODIC_DREAMER, SONIC_ADVENTURER, RHYTHM_SEEKER }

/** Kepribadian musik, dihitung dari data mendengarkan yang sebenarnya. */
data class ReplayPersonality(
    val kind: ReplayPersonalityKind,
    /** Porsi mendengarkan antara 21.00 dan 04.59. */
    val nightShare: Float,
    /** Rata-rata putaran per lagu berbeda. */
    val repeatRatio: Float,
)

/**
 * Aturan sederhana dan berurutan: banyak mendengarkan malam hari, lalu banyaknya
 * artis berbeda per putaran, lalu seberapa sering lagu yang sama diulang.
 * Selain itu, Melodic Dreamer.
 */
fun ReplaySummary.personality(): ReplayPersonality {
    val total = hourOfDay.sum().coerceAtLeast(1L)
    val night = hourOfDay.withIndex().filter { it.index >= 21 || it.index <= 4 }.sumOf { it.value }
    val nightShare = night.toFloat() / total
    val variety = distinctArtists.toFloat() / totalPlays.coerceAtLeast(1)
    val repeat = totalPlays.toFloat() / distinctSongs.coerceAtLeast(1)
    val kind = when {
        nightShare >= 0.34f -> ReplayPersonalityKind.NIGHT_EXPLORER
        variety >= 0.28f && distinctArtists >= 8 -> ReplayPersonalityKind.SONIC_ADVENTURER
        repeat >= 2.2f -> ReplayPersonalityKind.RHYTHM_SEEKER
        else -> ReplayPersonalityKind.MELODIC_DREAMER
    }
    return ReplayPersonality(kind, nightShare, repeat)
}

fun ReplayPersonality.title(context: Context): String = context.getString(
    when (kind) {
        ReplayPersonalityKind.NIGHT_EXPLORER -> R.string.replay_persona_night_title
        ReplayPersonalityKind.MELODIC_DREAMER -> R.string.replay_persona_dreamer_title
        ReplayPersonalityKind.SONIC_ADVENTURER -> R.string.replay_persona_adventurer_title
        ReplayPersonalityKind.RHYTHM_SEEKER -> R.string.replay_persona_rhythm_title
    },
)

fun ReplayPersonality.description(context: Context): String = context.getString(
    when (kind) {
        ReplayPersonalityKind.NIGHT_EXPLORER -> R.string.replay_persona_night_desc
        ReplayPersonalityKind.MELODIC_DREAMER -> R.string.replay_persona_dreamer_desc
        ReplayPersonalityKind.SONIC_ADVENTURER -> R.string.replay_persona_adventurer_desc
        ReplayPersonalityKind.RHYTHM_SEEKER -> R.string.replay_persona_rhythm_desc
    },
)

/** Sampai tiga baris kebiasaan mendengarkan, semuanya dari angka nyata. */
fun ReplaySummary.behaviorLines(context: Context, persona: ReplayPersonality): List<String> = buildList {
    peakHour?.let { add(context.getString(R.string.replay_behavior_peak, formatHour(context, it))) }
    if (persona.nightShare >= 0.05f) {
        add(context.getString(R.string.replay_behavior_night, (persona.nightShare * 100f).toInt()))
    }
    if (persona.repeatRatio >= 1.2f) {
        add(context.getString(R.string.replay_behavior_repeat, String.format(Locale.US, "%.1f", persona.repeatRatio)))
    }
    busiestDay?.let { add(context.getString(R.string.replay_behavior_day, formatDay(context, it))) }
}.take(3)

/** Rata-rata waktu mendengarkan per hari dalam periode yang dibuka. */
fun ReplaySummary.averageMsPerDay(): Long {
    val today = LocalDate.now()
    val days = when (period) {
        ReplayPeriod.THIS_WEEK -> today.dayOfWeek.value.toLong()
        ReplayPeriod.THIS_MONTH ->
            if (anchor.year == today.year && anchor.month == today.month) {
                today.dayOfMonth.toLong()
            } else {
                anchor.lengthOfMonth().toLong()
            }
        ReplayPeriod.THIS_YEAR ->
            if (anchor.year == today.year) today.dayOfYear.toLong() else anchor.lengthOfYear().toLong()
        ReplayPeriod.ALL_TIME -> runCatching {
            java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse("$since-01"), today) + 1
        }.getOrDefault(1L)
    }
    return totalMs / days.coerceAtLeast(1L)
}

/** The label for the month or year being browsed; empty for periods that are not stepped. */
fun ReplayPeriod.anchorLabel(anchor: LocalDate): String = when (this) {
    ReplayPeriod.THIS_MONTH -> DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        .format(anchor)
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    ReplayPeriod.THIS_YEAR -> anchor.year.toString()
    else -> ""
}

/** [anchor] moved [delta] months or years, landing on the first day of that month or year. */
fun ReplayPeriod.stepAnchor(anchor: LocalDate, delta: Long): LocalDate = when (this) {
    ReplayPeriod.THIS_MONTH -> anchor.withDayOfMonth(1).plusMonths(delta)
    ReplayPeriod.THIS_YEAR -> anchor.withDayOfYear(1).plusYears(delta)
    else -> anchor
}

/** Which page of the story view something opens onto. */
enum class ReplayStoryPage {
    INTRO, MINUTES, ARTISTS, SONGS, ALBUMS, GENRES, HABITS, SUMMARY;

    companion object {
        val ordered: List<ReplayStoryPage> = entries
    }
}

// ── Formatting ──────────────────────────────────────────────────────────────

/**
 * Listening time, in the largest unit that still says something.
 *
 * Minutes up to a day's worth, then hours: "1,284 minutes" is a number people
 * read as a number, and "21 hours" is one they read as an amount. Past a
 * thousand hours neither works and it becomes days.
 */
fun formatListening(context: Context, ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 60 -> context.getString(R.string.minutes_short, minutes)
        minutes < 1_440 -> context.getString(R.string.hours_minutes_short, minutes / 60, minutes % 60)
        else -> context.getString(R.string.minutes_grouped_short, grouped(minutes))
    }
}

/** The headline figure on the minutes card: always minutes, always grouped. */
fun formatMinutes(ms: Long): String = grouped(ms / 60_000)

fun grouped(value: Long): String = String.format(Locale.US, "%,d", value)

/** "3 pm", "midnight" — an hour of the day said the way anyone would say it. */
fun formatHour(context: Context, hour: Int): String = when (hour) {
    0 -> context.getString(R.string.midnight)
    12 -> context.getString(R.string.midday)
    in 1..11 -> context.getString(R.string.hour_am, hour)
    else -> context.getString(R.string.hour_pm, hour - 12)
}

/** `2026-08-14` as "14 August". */
fun formatDay(context: Context, iso: String): String = runCatching {
    LocalDate.parse(iso).format(
        DateTimeFormatter.ofPattern("d MMMM", context.resources.configuration.locales[0]),
    )
}.getOrDefault(iso)

fun Context.replayCount(count: Int, plural: Int): String =
    resources.getQuantityString(plural, count, grouped(count.toLong()))

// ── Rows and cards ──────────────────────────────────────────────────────────

/**
 * One line of one chart, with the four categories flattened onto a common
 * shape.
 *
 * Songs, artists, albums and genres are unlike enough in the data layer to be
 * kept apart there and alike enough on screen to be drawn once. [rank] is
 * carried on the row rather than derived from its index because the same row is
 * drawn in three places — the page, a story, the shared poster — and only one of
 * them has an index to hand.
 */
data class ReplayRow(
    val key: String,
    val rank: Int,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val ms: Long,
    val plays: Int,
)

fun ReplaySummary.songRows(limit: Int): List<ReplayRow> =
    songs.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.song.videoId,
            rank = index + 1,
            title = entry.song.title,
            subtitle = entry.song.artist.takeIf { it.isNotBlank() },
            artworkUrl = entry.song.thumbnailUrl,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

fun ReplaySummary.artistRows(limit: Int): List<ReplayRow> =
    artists.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.title,
            rank = index + 1,
            title = entry.title,
            subtitle = null,
            artworkUrl = entry.artworkUrl,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

fun ReplaySummary.albumRows(limit: Int): List<ReplayRow> =
    albums.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.title + "|" + entry.subtitle.orEmpty(),
            rank = index + 1,
            title = entry.title,
            subtitle = entry.subtitle,
            artworkUrl = entry.artworkUrl,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

/**
 * Genres, with the artwork deliberately dropped.
 *
 * The cover of whichever artist happened to lead the genre is not a picture of
 * the genre, and putting it there makes a row of five look like five artists
 * mislabelled. [com.music.audify.ui.replay.InitialTile] stands in instead.
 */
fun ReplaySummary.genreRows(limit: Int): List<ReplayRow> =
    genres.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.title,
            rank = index + 1,
            title = entry.title,
            subtitle = null,
            artworkUrl = null,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

/** One of the Replay headline cards shown on Library and inside Replay. */
data class ReplayHeroCard(
    val label: String,
    val value: String,
    val detail: String?,
    val artworkUrl: String?,
    val page: ReplayStoryPage,
)

/**
 * The Library's three headline facts, in their original visual order: minutes,
 * top album and top artist. The detailed song chart remains available inside
 * Replay; it does not displace the album card in the Library overview.
 *
 * Minutes leads because it is the one figure that needs no context to mean
 * something. A category with nothing in it is left out rather than shown empty:
 * a Replay of loose singles has no album chart, and a card reading "—" is worse
 * than three cards.
 */
fun ReplaySummary.cards(context: Context): List<ReplayHeroCard> = buildList {
    add(
        ReplayHeroCard(
            label = context.getString(R.string.minutes_listened),
            value = formatMinutes(totalMs),
            detail = "${context.replayCount(totalPlays, R.plurals.replay_play_count)} · " +
                localizedLabel(context),
            artworkUrl = songs.firstOrNull()?.song?.thumbnailUrl,
            page = ReplayStoryPage.MINUTES,
        ),
    )
    albums.firstOrNull()?.let {
        add(
            ReplayHeroCard(
                label = context.getString(R.string.top_album),
                value = it.title,
                detail = listOfNotNull(it.subtitle, formatListening(context, it.ms)).joinToString(" · "),
                artworkUrl = it.artworkUrl,
                page = ReplayStoryPage.ALBUMS,
            ),
        )
    }
    artists.firstOrNull()?.let {
        add(
            ReplayHeroCard(
                label = context.getString(R.string.top_artist),
                value = it.title,
                detail = "${formatListening(context, it.ms)} · " +
                    context.replayCount(it.plays, R.plurals.replay_play_count),
                artworkUrl = it.artworkUrl,
                page = ReplayStoryPage.ARTISTS,
            ),
        )
    }
}
