package dev.ellipog.stature.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScaleAxesTest {

    private static final double DELTA = 1.0E-5;

    @Test
    void uniformIsVanilla() {
        assertTrue(ScaleAxes.UNIFORM.isUniform());
        assertEquals(0.6F, ScaleAxes.UNIFORM.applyWidth(0.6F), DELTA);
        assertEquals(1.8F, ScaleAxes.UNIFORM.applyHeight(1.8F), DELTA);
        assertEquals(0.6F, ScaleAxes.UNIFORM.applyDepth(0.6F), DELTA);
    }

    @Test
    void depthIsMeasuredAgainstWidthLikeVanillaDoes() {
        // Zephyr's frame from the plan: 0.55 wide, 1.71 tall, on a 0.6 x 1.8 body.
        ScaleAxes slender = new ScaleAxes(0.55F / 0.6F, 1.71F / 1.8F, 0.55F / 0.6F);
        assertEquals(0.55F, slender.applyWidth(0.6F), DELTA);
        assertEquals(1.71F, slender.applyHeight(1.8F), DELTA);
        assertEquals(0.55F, slender.applyDepth(0.6F), DELTA);
    }

    @Test
    void aDeeperThanWideBoxIsRepresentable() {
        ScaleAxes deep = new ScaleAxes(1.0F, 1.0F, 2.0F);
        assertEquals(0.6F, deep.applyWidth(0.6F), DELTA);
        assertEquals(1.2F, deep.applyDepth(0.6F), DELTA);
    }

    @Test
    void nonsenseIsRejectedRatherThanClamped() {
        // Zero inverts the box, negative inverts it harder, NaN makes the model vanish without an
        // error. All three are caller bugs, so they say so.
        assertThrows(IllegalArgumentException.class, () -> new ScaleAxes(0.0F, 1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class, () -> new ScaleAxes(1.0F, -1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class, () -> new ScaleAxes(1.0F, 1.0F, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ScaleAxes(1.0F, Float.POSITIVE_INFINITY, 1.0F));
    }

    @Test
    void lerpWalksEveryAxis() {
        ScaleAxes half = ScaleAxes.lerp(ScaleAxes.UNIFORM, new ScaleAxes(2.0F, 3.0F, 5.0F), 0.5);
        assertEquals(1.5F, half.width(), DELTA);
        assertEquals(2.0F, half.height(), DELTA);
        assertEquals(3.0F, half.depth(), DELTA);
    }
}
