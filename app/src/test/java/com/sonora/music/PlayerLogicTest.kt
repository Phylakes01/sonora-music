package com.sonora.music

import com.sonora.music.model.RepeatMode
import com.sonora.music.model.ShuffleMode
import com.sonora.music.model.Track
import com.sonora.music.player.AudioEffectsManager
import com.sonora.music.player.QueueEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlayerLogicTest {

    private fun sampleTracks(count: Int, prefix: String = "t"): List<Track> =
        (1..count).map { idx ->
            Track(
                id = "$prefix$idx",
                title = "Track $prefix$idx",
                artist = "Artist $idx",
                durationSec = 180,
                streamUrl = "https://example.com/$prefix$idx.mp3"
            )
        }

    @Test
    fun shufflePreservingCurrent_keepsActiveTrackAtIndexZeroAndRetainsAllItems() {
        val tracks = sampleTracks(8)
        val shuffled = QueueEngine.shufflePreservingCurrent(
            queue = tracks,
            currentIndex = 4,
            random = Random(42)
        )

        assertEquals(tracks.size, shuffled.size)
        assertEquals("t5", shuffled.first().id)
        assertEquals(tracks.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
    }

    @Test
    fun shufflePreservingCurrent_handlesEmptyAndSingleItemQueues() {
        assertEquals(emptyList<Track>(), QueueEngine.shufflePreservingCurrent(emptyList(), 0))

        val single = sampleTracks(1)
        assertEquals(single, QueueEngine.shufflePreservingCurrent(single, 0))
    }

    @Test
    fun weaveSmartPicks_insertsPicksAtIntervalAndMarksSmartPickAndDeduplicates() {
        val queue = sampleTracks(5)
        val duplicateOfExisting = queue[2].copy(isSmartPick = false)
        val smartPicks = listOf(
            duplicateOfExisting,
            Track(id = "s1", title = "Smart 1", artist = "AI"),
            Track(id = "s2", title = "Smart 2", artist = "AI"),
            Track(id = "s3", title = "Smart 3", artist = "AI")
        )

        val woven = QueueEngine.weaveSmartPicks(
            queue = queue,
            currentIndex = 0,
            smartPicks = smartPicks,
            interval = 2
        )

        // Expected: t1 (current), t2, t3, s1, t4, t5, s2, s3
        assertEquals(listOf("t1", "t2", "t3", "s1", "t4", "t5", "s2", "s3"), woven.map { it.id })
        assertFalse(woven[0].isSmartPick)
        assertFalse(woven[1].isSmartPick)
        assertTrue(woven[3].isSmartPick)
        assertTrue(woven[6].isSmartPick)
        assertTrue(woven[7].isSmartPick)
        // Original t3 should remain non-smart-pick and not duplicated
        assertEquals(1, woven.count { it.id == "t3" })
    }

    @Test
    fun weaveSmartPicks_replacesStaleSmartPicksInUpNextWithoutDuplicating() {
        val t1 = Track(id = "t1", title = "1", artist = "A")
        val oldSmart = Track(id = "old_s", title = "Old", artist = "AI", isSmartPick = true)
        val t2 = Track(id = "t2", title = "2", artist = "A")
        val newSmart = Track(id = "new_s", title = "New", artist = "AI")

        val woven = QueueEngine.weaveSmartPicks(
            queue = listOf(t1, oldSmart, t2),
            currentIndex = 0,
            smartPicks = listOf(newSmart),
            interval = 1
        )

        assertEquals(listOf("t1", "t2", "new_s"), woven.map { it.id })
    }

    @Test
    fun moveItem_reordersForwardBackwardAndHandlesBounds() {
        val tracks = sampleTracks(5)

        val movedForward = QueueEngine.moveItem(tracks, fromIndex = 1, toIndex = 3)
        assertEquals(listOf("t1", "t3", "t4", "t2", "t5"), movedForward.map { it.id })

        val movedBackward = QueueEngine.moveItem(tracks, fromIndex = 4, toIndex = 0)
        assertEquals(listOf("t5", "t1", "t2", "t3", "t4"), movedBackward.map { it.id })

        val outOfBounds = QueueEngine.moveItem(tracks, fromIndex = -1, toIndex = 2)
        assertEquals(tracks, outOfBounds)

        assertEquals(3, QueueEngine.computeAdjustedIndexOnMove(currentIndex = 1, fromIndex = 1, toIndex = 3))
        assertEquals(1, QueueEngine.computeAdjustedIndexOnMove(currentIndex = 2, fromIndex = 1, toIndex = 3))
        assertEquals(2, QueueEngine.computeAdjustedIndexOnMove(currentIndex = 1, fromIndex = 3, toIndex = 0))
    }

    @Test
    fun computeCrossfadeVolume_rampsSmoothlyAtEdgesAndFullVolumeInMiddle() {
        val duration = 180_000L
        val crossfadeSec = 6 // 6_000ms

        // Disabled crossfade -> always 1f
        assertEquals(1f, QueueEngine.computeCrossfadeVolume(179_000L, duration, crossfadeSeconds = 0), 0.0001f)

        // Middle of track -> full volume
        assertEquals(1f, QueueEngine.computeCrossfadeVolume(60_000L, duration, crossfadeSec), 0.0001f)

        // Fade-in ramp at start when enabled
        val startVol = QueueEngine.computeCrossfadeVolume(0L, duration, crossfadeSec, fadeInEnabled = true)
        val midInVol = QueueEngine.computeCrossfadeVolume(3_000L, duration, crossfadeSec, fadeInEnabled = true)
        val endInVol = QueueEngine.computeCrossfadeVolume(6_000L, duration, crossfadeSec, fadeInEnabled = true)
        assertEquals(0f, startVol, 0.001f)
        assertTrue(midInVol in 0.6f..0.8f)
        assertEquals(1f, endInVol, 0.001f)

        // Fade-in disabled at start -> full volume immediately
        assertEquals(1f, QueueEngine.computeCrossfadeVolume(0L, duration, crossfadeSec, fadeInEnabled = false), 0.0001f)

        // Fade-out curve near end monotonically drops to 0
        val fadeStart = QueueEngine.computeCrossfadeVolume(174_000L, duration, crossfadeSec)
        val fadeHalf = QueueEngine.computeCrossfadeVolume(177_000L, duration, crossfadeSec)
        val fadeQuarter = QueueEngine.computeCrossfadeVolume(178_500L, duration, crossfadeSec)
        val fadeEnd = QueueEngine.computeCrossfadeVolume(180_000L, duration, crossfadeSec)

        assertEquals(1f, fadeStart, 0.001f)
        assertTrue(fadeHalf > fadeQuarter)
        assertTrue(fadeQuarter > fadeEnd)
        assertEquals(0f, fadeEnd, 0.001f)

        // Short track clamps fade window to half duration so fade-in and fade-out don't collide
        val shortDuration = 4_000L
        assertEquals(1f, QueueEngine.computeCrossfadeVolume(2_000L, shortDuration, crossfadeSeconds = 10), 0.01f)
    }

    @Test
    fun modeCycling_followsExpectedTransitions() {
        var repeat = RepeatMode.OFF
        repeat = QueueEngine.nextRepeatMode(repeat)
        assertEquals(RepeatMode.ALL, repeat)
        repeat = QueueEngine.nextRepeatMode(repeat)
        assertEquals(RepeatMode.ONE, repeat)
        repeat = QueueEngine.nextRepeatMode(repeat)
        assertEquals(RepeatMode.OFF, repeat)

        var shuffle = ShuffleMode.OFF
        shuffle = QueueEngine.nextShuffleMode(shuffle)
        assertEquals(ShuffleMode.SHUFFLE, shuffle)
        shuffle = QueueEngine.nextShuffleMode(shuffle)
        assertEquals(ShuffleMode.SMART_SHUFFLE, shuffle)
        shuffle = QueueEngine.nextShuffleMode(shuffle)
        assertEquals(ShuffleMode.OFF, shuffle)
    }

    @Test
    fun audioEffectsScaleBandLevel_scalesAcrossDifferentBandCountsAndRanges() {
        val fiveBands = listOf(600, 300, 0, -300, -600)

        // 1:1 on standard 5-band [-1500, 1500] hardware
        assertEquals(600, AudioEffectsManager.scaleBandLevel(0, 5, fiveBands, -1500, 1500))
        assertEquals(0, AudioEffectsManager.scaleBandLevel(2, 5, fiveBands, -1500, 1500))
        assertEquals(-600, AudioEffectsManager.scaleBandLevel(4, 5, fiveBands, -1500, 1500))

        // Scaled onto narrower [-1000, 1000] hardware
        assertEquals(400, AudioEffectsManager.scaleBandLevel(0, 5, fiveBands, -1000, 1000))
        assertEquals(-400, AudioEffectsManager.scaleBandLevel(4, 5, fiveBands, -1000, 1000))

        // Interpolated onto 9-band hardware
        val midLow = AudioEffectsManager.scaleBandLevel(1, 9, fiveBands, -1500, 1500)
        assertEquals(450, midLow)
    }
}
