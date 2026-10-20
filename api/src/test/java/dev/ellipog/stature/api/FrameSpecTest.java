package dev.ellipog.stature.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameSpecTest {

    private static final double DELTA = 1.0E-5;

    /** A sample small frame: 0.55 x 1.71 on a player's 0.6 x 1.8 base. */
    private static final FrameSpec ZEPHYR = FrameSpec.of(0.55F, 1.71F, 0.55F);

    @Test
    void aFrameBecomesMultipliersAgainstTheEntitysOwnBox() {
        ScaleAxes axes = ZEPHYR.axesFor(0.6F, 1.8F);
        assertEquals(0.55F / 0.6F, axes.width(), DELTA);
        assertEquals(1.71F / 1.8F, axes.height(), DELTA);
        // Depth against the base width: vanilla is as deep as it is wide.
        assertEquals(0.55F / 0.6F, axes.depth(), DELTA);

        // And feeding those back through a base box returns the frame's size.
        assertEquals(0.55F, axes.applyWidth(0.6F), DELTA);
        assertEquals(1.71F, axes.applyHeight(1.8F), DELTA);
        assertEquals(0.55F, axes.applyDepth(0.6F), DELTA);
    }

    @Test
    void theSameFrameMeansTheSameThingOnADifferentEntity() {
        // A zombie's base is 0.6 x 1.95, and the frame still lands on 0.55 x 1.71.
        ScaleAxes axes = ZEPHYR.axesFor(0.6F, 1.95F);
        assertEquals(0.55F, axes.applyWidth(0.6F), DELTA);
        assertEquals(1.71F, axes.applyHeight(1.95F), DELTA);
    }

    @Test
    void aFrameCanBeDeeperThanItIsWide() {
        FrameSpec deep = FrameSpec.of(0.63F, 2.10F, 1.26F);
        ScaleAxes axes = deep.axesFor(0.6F, 1.8F);
        assertEquals(0.63F, axes.applyWidth(0.6F), DELTA);
        assertEquals(2.10F, axes.applyHeight(1.8F), DELTA);
        assertEquals(1.26F, axes.applyDepth(0.6F), DELTA);
        assertTrue(axes.zRatio() > 1.0F);
    }

    @Test
    void theScaleMultipliesTheFrame() {
        // The plan's rule: a frame's size is absolute and a scale multiplies it.
        FrameSpec goliath = new FrameSpec(1.65F, 0.99F, 2.97F, 0.99F, 3.05F, Easing.EASE_OUT_CUBIC);
        ScaleAxes box = goliath.boxAt(1.65F);
        assertEquals(0.99F * 1.65F, box.width(), DELTA);
        assertEquals(2.97F * 1.65F, box.height(), DELTA);
        assertTrue(goliath.hasEyeHeight());
        assertEquals(3.05F, goliath.eyeHeight(), DELTA);
    }

    @Test
    void aFrameWithoutAnEyeHeightSaysSo() {
        assertFalse(ZEPHYR.hasEyeHeight());
        assertEquals(FrameSpec.NO_EYE_HEIGHT, ZEPHYR.eyeHeight(), DELTA);
        assertEquals(Easing.EASE_OUT_CUBIC, ZEPHYR.easing());
    }

    @Test
    void nonsenseIsRejectedWithTheFieldNamed() {
        assertThrows(IllegalArgumentException.class, () -> FrameSpec.of(0.0F, 1.8F, 1.8F));
        assertThrows(IllegalArgumentException.class, () -> FrameSpec.of(0.6F, -1.0F, 0.6F));
        assertThrows(IllegalArgumentException.class, () -> new FrameSpec(1.0F, 0.6F, 1.8F, 0.6F, 0.0F, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> new FrameSpec(20.0F, 0.6F, 1.8F, 0.6F, -1.0F, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> ZEPHYR.axesFor(0.0F, 1.8F));
    }
}
