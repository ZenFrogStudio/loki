package org.schabi.newpipe.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks the rule that tells taps and swipes apart in driving mode. Distances are in dp. */
class DrivingGestureTest {

    @Test
    fun `a finger that does not move is a tap`() {
        assertEquals(DrivingGesture.TAP, classifyDrivingGesture(0f, 0f))
    }

    @Test
    fun `a sloppy tap that drifts a little is still a tap`() {
        assertEquals(DrivingGesture.TAP, classifyDrivingGesture(20f, -25f))
        assertEquals(DrivingGesture.TAP, classifyDrivingGesture(-29f, 29f))
    }

    @Test
    fun `a long swipe to the right skips forward`() {
        assertEquals(DrivingGesture.SWIPE_FORWARD, classifyDrivingGesture(120f, 10f))
    }

    @Test
    fun `a long swipe to the left skips back`() {
        assertEquals(DrivingGesture.SWIPE_BACK, classifyDrivingGesture(-120f, -10f))
    }

    @Test
    fun `a swipe counts from exactly 90dp and not just below it`() {
        assertEquals(DrivingGesture.SWIPE_FORWARD, classifyDrivingGesture(90f, 0f))
        assertEquals(DrivingGesture.NONE, classifyDrivingGesture(89.9f, 0f))
    }

    @Test
    fun `a movement too big for a tap but too short for a swipe is ignored`() {
        assertEquals(DrivingGesture.NONE, classifyDrivingGesture(30f, 0f))
        assertEquals(DrivingGesture.NONE, classifyDrivingGesture(-60f, 5f))
    }

    @Test
    fun `a diagonal drag is ignored even when it is long`() {
        assertEquals(DrivingGesture.NONE, classifyDrivingGesture(100f, 80f))
        assertEquals(DrivingGesture.NONE, classifyDrivingGesture(-100f, 50f))
    }

    @Test
    fun `an up or down swipe is ignored`() {
        assertEquals(DrivingGesture.NONE, classifyDrivingGesture(5f, 150f))
        assertEquals(DrivingGesture.NONE, classifyDrivingGesture(0f, -150f))
    }
}
