package com.music.audify.playback

import com.music.audify.data.model.Song
import com.music.audify.playback.smart.EnergySample
import com.music.audify.playback.smart.TrackAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RadioListeningIntentTest {

    private fun song(id: String, artist: String) = Song(
        videoId = id,
        title = id,
        artist = artist,
        thumbnailUrl = null,
    )

    @Test
    fun offPreservesProviderOrderAndOriginalList() {
        val candidates = listOf(song("a", "A"), song("b", "B"))
        val ordered = orderForListeningIntent(
            RadioListeningIntent.OFF,
            seed = song("seed", "A"),
            existing = emptyList(),
            candidates = candidates,
            analysisOf = { null },
        )

        assertSame(candidates, ordered)
    }

    @Test
    fun familiarIntentRanksArtistsAlreadyInTheSessionFirst() {
        val familiar = song("familiar", "The Familiar")
        val new = song("new", "New Artist")

        val ordered = orderForListeningIntent(
            RadioListeningIntent.FAMILIAR,
            seed = song("seed", "The Familiar"),
            existing = emptyList(),
            candidates = listOf(new, familiar),
            analysisOf = { null },
        )

        assertEquals(listOf(familiar, new), ordered)
    }

    @Test
    fun discoveryIntentRanksArtistsNotPresentInTheSessionFirst() {
        val familiar = song("familiar", "The Familiar")
        val new = song("new", "New Artist")

        val ordered = orderForListeningIntent(
            RadioListeningIntent.DISCOVERY,
            seed = song("seed", "The Familiar"),
            existing = listOf(song("recent", "The Familiar")),
            candidates = listOf(familiar, new),
            analysisOf = { null },
        )

        assertEquals(listOf(new, familiar), ordered)
    }

    @Test
    fun diversityBreaksTiesWithoutOverridingTheIntentScore() {
        val firstA = song("a1", "Artist A")
        val secondA = song("a2", "Artist A")
        val artistB = song("b", "Artist B")
        val artistC = song("c", "Artist C")
        val ordered = orderForListeningIntent(
            RadioListeningIntent.DISCOVERY,
            seed = song("seed", "Seed Artist"),
            existing = emptyList(),
            candidates = listOf(firstA, secondA, artistB, artistC),
            analysisOf = { null },
        )

        assertEquals(listOf(firstA, artistB, artistC, secondA), ordered)
    }

    @Test
    fun diversityNeverMovesLowerIntentScoresAheadOfHigherScores() {
        val familiar = song("familiar", "Known Artist")
        val newFirst = song("new-1", "New Artist")
        val newSecond = song("new-2", "New Artist")
        val ordered = orderForListeningIntent(
            RadioListeningIntent.DISCOVERY,
            seed = song("seed", "Known Artist"),
            existing = emptyList(),
            candidates = listOf(familiar, newFirst, newSecond),
            analysisOf = { null },
        )

        assertEquals(listOf(newFirst, newSecond, familiar), ordered)
    }

    @Test
    fun energyIntentsUseMeasuredEnergyAndKeepUnanalysedTracksLast() {
        val high = song("high", "A")
        val low = song("low", "B")
        val unknown = song("unknown", "C")
        val analyses = mapOf(
            high.videoId to analysis(high.videoId, 0.9),
            low.videoId to analysis(low.videoId, 0.2),
        )
        val candidates = listOf(unknown, high, low)

        val energetic = orderForListeningIntent(
            RadioListeningIntent.ENERGETIC,
            seed = song("seed", "Seed"),
            existing = emptyList(),
            candidates = candidates,
            analysisOf = analyses::get,
        )
        val calm = orderForListeningIntent(
            RadioListeningIntent.CALM,
            seed = song("seed", "Seed"),
            existing = emptyList(),
            candidates = candidates,
            analysisOf = analyses::get,
        )

        assertEquals(listOf(high, low, unknown), energetic)
        assertEquals(listOf(low, high, unknown), calm)
    }

    @Test
    fun missingArtistOrEnergyEvidenceLeavesProviderOrderUnchanged() {
        val candidates = listOf(song("a", ""), song("b", ""))
        assertEquals(
            candidates,
            orderForListeningIntent(
                RadioListeningIntent.DISCOVERY,
                seed = song("seed", ""),
                existing = emptyList(),
                candidates = candidates,
                analysisOf = { null },
            ),
        )
        assertEquals(
            candidates,
            orderForListeningIntent(
                RadioListeningIntent.ENERGETIC,
                seed = song("seed", ""),
                existing = emptyList(),
                candidates = candidates,
                analysisOf = { null },
            ),
        )
    }

    @Test
    fun explanationsNameOnlySignalsThatActuallyContributed() {
        val familiar = song("familiar", "Known Artist")
        val discovery = song("discovery", "New Artist")
        val energetic = song("energetic", "Energy Artist")
        val calm = song("calm", "Calm Artist")
        val analyses = mapOf(
            energetic.videoId to analysis(energetic.videoId, 0.9),
            calm.videoId to analysis(calm.videoId, 0.2),
        )
        val seed = song("seed", "Known Artist")

        assertEquals(
            RadioRecommendationReason.FAMILIAR_ARTIST,
            explainListeningIntentCandidate(RadioListeningIntent.FAMILIAR, seed, emptyList(), familiar) { analyses[it] },
        )
        assertEquals(
            RadioRecommendationReason.NEW_ARTIST,
            explainListeningIntentCandidate(RadioListeningIntent.DISCOVERY, seed, emptyList(), discovery) { analyses[it] },
        )
        assertEquals(
            RadioRecommendationReason.HIGHER_MEASURED_ENERGY,
            explainListeningIntentCandidate(RadioListeningIntent.ENERGETIC, seed, emptyList(), energetic) { analyses[it] },
        )
        assertEquals(
            RadioRecommendationReason.LOWER_MEASURED_ENERGY,
            explainListeningIntentCandidate(RadioListeningIntent.CALM, seed, emptyList(), calm) { analyses[it] },
        )
        assertEquals(
            RadioRecommendationReason.PROVIDER_ORDER,
            explainListeningIntentCandidate(RadioListeningIntent.ENERGETIC, seed, emptyList(), discovery) { null },
        )
    }

    @Test
    fun intentCycleIncludesAnExplicitOffState() {
        assertEquals(RadioListeningIntent.FAMILIAR, RadioListeningIntent.OFF.next())
        assertEquals(RadioListeningIntent.DISCOVERY, RadioListeningIntent.FAMILIAR.next())
        assertEquals(RadioListeningIntent.ENERGETIC, RadioListeningIntent.DISCOVERY.next())
        assertEquals(RadioListeningIntent.CALM, RadioListeningIntent.ENERGETIC.next())
        assertEquals(RadioListeningIntent.OFF, RadioListeningIntent.CALM.next())
    }

    private fun analysis(id: String, energy: Double) = TrackAnalysis(
        status = TrackAnalysis.STATUS_READY,
        trackId = id,
        energyCurve = listOf(EnergySample(time = 0.0, energy = energy)),
    )
}
