package dev.ellipog.stature.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScaleLimitsTest {

    @Test
    void theEnvelopeIsTheGamesOwn() {
        assertTrue(ScaleLimits.within(ScaleLimits.MIN));
        assertTrue(ScaleLimits.within(ScaleLimits.MAX));
        assertTrue(ScaleLimits.within(1.0F));
        assertFalse(ScaleLimits.within(0.05F));
        assertFalse(ScaleLimits.within(17.0F));
    }

    @Test
    void clampPullsIntoTheEnvelopeAndLeavesTheRestAlone() {
        assertEquals(ScaleLimits.MIN, ScaleLimits.clamp(0.0F));
        assertEquals(ScaleLimits.MIN, ScaleLimits.clamp(0.05F));
        assertEquals(ScaleLimits.MAX, ScaleLimits.clamp(100.0F));
        assertEquals(2.5F, ScaleLimits.clamp(2.5F));
    }

    @Test
    void nonFiniteValuesBecomeSomethingSafe() {
        // Only a corrupt packet can produce these -- commands cannot spell them -- so the
        // answer is "no change" rather than an exception on the network thread.
        assertEquals(1.0F, ScaleLimits.clamp(Float.NaN));
        assertEquals(ScaleLimits.MAX, ScaleLimits.clamp(Float.POSITIVE_INFINITY));
        assertEquals(ScaleLimits.MIN, ScaleLimits.clamp(Float.NEGATIVE_INFINITY));
    }
}
