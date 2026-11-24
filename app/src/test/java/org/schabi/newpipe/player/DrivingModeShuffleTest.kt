package org.schabi.newpipe.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

/** Checks how the playlist "Driving mode" button prepares the list before playing it. */
class DrivingModeShuffleTest {

    private fun queueOf(count: Int) = SinglePlayQueue(
        (0 until count).map { StreamInfoItem(0, "URL_$it", "Song $it", StreamType.VIDEO_STREAM) },
        0
    )

    @Test
    fun `the list is in shuffle mode afterwards`() {
        val queue = queueOf(10)
        shuffleFromRandomStart(queue, Random(1))
        assertTrue(queue.isShuffled)
    }

    @Test
    fun `the randomly picked song plays first`() {
        val queue = queueOf(10)
        val pick = Random(42).nextInt(10) // the same number the function will draw
        shuffleFromRandomStart(queue, Random(42))
        assertEquals(0, queue.index)
        assertEquals("URL_$pick", queue.item!!.url)
    }

    @Test
    fun `different drives can start with different songs`() {
        val firstSongs = (1..20).map { seed ->
            queueOf(10).also { shuffleFromRandomStart(it, Random(seed)) }.item!!.url
        }
        assertTrue(firstSongs.toSet().size > 1)
    }

    @Test
    fun `no song is lost or repeated`() {
        val queue = queueOf(25)
        shuffleFromRandomStart(queue, Random(7))
        assertEquals((0 until 25).map { "URL_$it" }.toSet(), queue.streams.map { it.url }.toSet())
        assertEquals(25, queue.size())
    }

    @Test
    fun `turning shuffle off brings back the playlist order`() {
        val queue = queueOf(10)
        shuffleFromRandomStart(queue, Random(3))
        queue.unshuffle()
        assertEquals((0 until 10).map { "URL_$it" }, queue.streams.map { it.url })
    }

    @Test
    fun `a one-song list still works`() {
        val queue = queueOf(1)
        shuffleFromRandomStart(queue, Random(5))
        assertEquals("URL_0", queue.item!!.url)
        assertTrue(queue.isShuffled)
    }
}
