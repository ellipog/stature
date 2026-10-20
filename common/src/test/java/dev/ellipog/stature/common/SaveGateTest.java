package dev.ellipog.stature.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.frame.FrameSource;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/**
 * The save early-return, driven directly: every leg of the predicate is a case,
 * and the parts-only leg is the regression for entities shaped only by
 * {@code /stature part} losing their silhouette on unload.
 */
class SaveGateTest {

    private static final Identifier FRAME = Identifier.fromNamespaceAndPath("stature", "tall");

    @Test
    void vanillaWritesNothing() {
        assertFalse(SaveGate.shouldWrite(ScaleAxes.UNIFORM, StatureScale.NO_EYE_HEIGHT,
                null, FrameSource.NONE, false, false));
    }

    @Test
    void defaultSourceWritesNothing() {
        assertFalse(SaveGate.shouldWrite(ScaleAxes.UNIFORM, StatureScale.NO_EYE_HEIGHT,
                null, FrameSource.DEFAULT, false, false));
    }

    @Test
    void partsOnlyWrites() {
        assertTrue(SaveGate.shouldWrite(ScaleAxes.UNIFORM, StatureScale.NO_EYE_HEIGHT,
                null, FrameSource.NONE, false, true));
    }

    @Test
    void channelsOnlyWrites() {
        assertTrue(SaveGate.shouldWrite(ScaleAxes.UNIFORM, StatureScale.NO_EYE_HEIGHT,
                null, FrameSource.NONE, true, false));
    }

    @Test
    void shapedBoxWrites() {
        assertTrue(SaveGate.shouldWrite(new ScaleAxes(2.0F, 2.0F, 2.0F), StatureScale.NO_EYE_HEIGHT,
                null, FrameSource.NONE, false, false));
    }

    @Test
    void eyeOverrideWrites() {
        assertTrue(SaveGate.shouldWrite(ScaleAxes.UNIFORM, 1.62F,
                null, FrameSource.NONE, false, false));
    }

    @Test
    void namedFrameWrites() {
        assertTrue(SaveGate.shouldWrite(ScaleAxes.UNIFORM, StatureScale.NO_EYE_HEIGHT,
                FRAME, FrameSource.NONE, false, false));
    }

    @Test
    void manualSourceWritesEvenWhenVanillaOtherwise() {
        assertTrue(SaveGate.shouldWrite(ScaleAxes.UNIFORM, StatureScale.NO_EYE_HEIGHT,
                null, FrameSource.MANUAL, false, false));
    }
}
