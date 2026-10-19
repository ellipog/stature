package dev.ellipog.stature.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The curves do what §14.1 promises: every one starts at 0 and lands at 1, and the cubic passes
 * through its one value worth pinning down.
 */
class EasingTest {

    @Test
    void everyCurveStartsAtZeroAndLandsAtOne() {
        for (Easing easing : Easing.values()) {
            assertEquals(0.0, easing.apply(0.0), "start of " + easing);
            assertEquals(1.0, easing.apply(1.0), 1.0E-9, "landing of " + easing);
        }
    }

    @Test
    void linearIsTheIdentity() {
        assertEquals(0.25, Easing.LINEAR.apply(0.25), 1.0E-12);
        assertEquals(0.75, Easing.LINEAR.apply(0.75), 1.0E-12);
    }

    @Test
    void easeOutCubicPassesThroughSevenEighths() {
        assertEquals(0.875, Easing.EASE_OUT_CUBIC.apply(0.5), 1.0E-12);
    }

    @Test
    void easeOutCubicNeverLeavesZeroToOne() {
        double previous = -1.0;
        for (int i = 0; i <= 20; i++) {
            double value = Easing.EASE_OUT_CUBIC.apply(i / 20.0);
            assertTrue(value >= 0.0 && value <= 1.0, "out of range at " + i);
            assertTrue(value >= previous, "not monotonic at " + i);
            previous = value;
        }
    }

    @Test
    void lerpFollowsTheCurve() {
        assertEquals(1.0 + (2.0 - 1.0) * 0.875, Easing.EASE_OUT_CUBIC.lerp(1.0, 2.0, 0.5), 1.0E-12);
        assertEquals(2.0, Easing.EASE_OUT_CUBIC.lerp(1.0, 2.0, 99.0), 1.0E-12);
    }
}
