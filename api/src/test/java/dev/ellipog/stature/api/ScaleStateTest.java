package dev.ellipog.stature.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScaleStateTest {

    private static final double DELTA = 1.0E-5;

    @Test
    void uniformIsVanilla() {
        assertTrue(ScaleState.UNIFORM.isUniform());
        assertTrue(ScaleState.of(1.0F).isUniform());
        assertFalse(ScaleState.of(2.0F).isUniform());
    }

    @Test
    void withKeepsTheOtherHalf() {
        ScaleState tall = ScaleState.of(0.5F).withAxes(new ScaleAxes(1.0F, 2.0F, 1.0F));
        assertEquals(0.5F, tall.scale(), DELTA);
        assertEquals(2.0F, tall.axes().height(), DELTA);
        assertEquals(0.5F, tall.withAxes(ScaleAxes.UNIFORM).scale(), DELTA);
    }

    @Test
    void outOfEnvelopeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ScaleState.of(0.01F));
        assertThrows(IllegalArgumentException.class, () -> ScaleState.of(17.0F));
    }

    @Test
    void lerpClampsTheScaleBecauseCurvesOvershoot() {
        // Elastic passes its target: 1.0 -> 16.0 at t=0.5 is past the top of the envelope, and the
        // constructor would throw mid-animation without the clamp.
        ScaleState overshoot = ScaleState.lerp(ScaleState.of(1.0F), ScaleState.of(16.0F), 1.06);
        assertEquals(ScaleLimits.MAX, overshoot.scale(), DELTA);
    }

    @Test
    void lerpHalfwayIsHalfwayOnEveryComponent() {
        ScaleState from = ScaleState.of(1.0F);
        ScaleState to = ScaleState.of(3.0F).withAxes(new ScaleAxes(1.0F, 2.0F, 4.0F));
        ScaleState half = ScaleState.lerp(from, to, 0.5);
        assertEquals(2.0F, half.scale(), DELTA);
        assertEquals(1.0F, half.axes().width(), DELTA);
        assertEquals(1.5F, half.axes().height(), DELTA);
        assertEquals(2.5F, half.axes().depth(), DELTA);
    }
}
