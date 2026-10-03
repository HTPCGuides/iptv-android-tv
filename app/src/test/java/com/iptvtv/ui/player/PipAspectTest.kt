package com.iptvtv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PipAspectTest {
    @Test fun `a normal video keeps its own shape`() {
        assertEquals(1920 to 1080, pipAspect(1920, 1080))
        assertEquals(720 to 576, pipAspect(720, 576))
    }

    @Test fun `no size yet falls back to 16 by 9`() {
        // Before the first frame Media3 reports 0x0; Rational(0, 0) would make the platform throw.
        assertEquals(16 to 9, pipAspect(0, 0))
        assertEquals(16 to 9, pipAspect(1280, 0))
    }

    @Test fun `ratios Android rejects are clamped to its limits`() {
        assertEquals(239 to 100, pipAspect(3000, 1000))   // flatter than 2.39:1
        assertEquals(100 to 239, pipAspect(400, 1200))    // taller than 1:2.39
    }

    @Test fun `exactly at the limit is accepted unchanged`() {
        assertEquals(239 to 100, pipAspect(239, 100))
    }
}
