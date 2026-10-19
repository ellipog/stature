package dev.ellipog.stature.common.mixin.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.client.StatureRenderState;

import org.junit.jupiter.api.Test;

/**
 * The render mixins cannot run headlessly, but the class holding the blend math is just a class:
 * subclassing the mixin exercises {@code pushFrame} and the two render reads directly, with no
 * game running. What this proves is the frame logic -- samples shift on tick boundaries, blend
 * within a frame, and never produce anything but vanilla from a fresh or reused state.
 */
class RenderInterpolationTest {

    private static final float DELTA = 0.0001F;

    private static final ScaleAxes SHAPED = new ScaleAxes(0.5F, 1.2F, 2.0F);

    private static StatureRenderState freshState() {
        return new LivingEntityRenderStateMixin() {
        };
    }

    @Test
    void theFirstFrameDrawsExact() {
        StatureRenderState state = freshState();
        state.stature$pushFrame(10, 0.5F, 2.0F, SHAPED);

        assertEquals(2.0F, state.stature$renderScale(), DELTA);
        assertEquals(SHAPED, state.stature$renderShape());
    }

    @Test
    void aFrameBlendsHalfwayBetweenTwoTicks() {
        StatureRenderState state = freshState();
        state.stature$pushFrame(10, 1.0F, 1.0F, ScaleAxes.UNIFORM);
        state.stature$pushFrame(11, 0.5F, 2.0F, SHAPED);

        assertEquals(1.5F, state.stature$renderScale(), DELTA);
        assertEquals(ScaleAxes.lerp(ScaleAxes.UNIFORM, SHAPED, 0.5), state.stature$renderShape());
    }

    @Test
    void aFinishedTickReadsTheNewSampleWhole() {
        StatureRenderState state = freshState();
        state.stature$pushFrame(10, 1.0F, 1.0F, ScaleAxes.UNIFORM);
        state.stature$pushFrame(11, 1.0F, 2.0F, SHAPED);

        assertEquals(2.0F, state.stature$renderScale(), DELTA);
        assertEquals(SHAPED, state.stature$renderShape());
    }

    @Test
    void aPooledStateReusedByAYoungerEntityStartsOver() {
        StatureRenderState state = freshState();
        state.stature$pushFrame(100, 1.0F, 3.0F, SHAPED);
        state.stature$pushFrame(5, 0.7F, 2.0F, ScaleAxes.UNIFORM);

        assertEquals(2.0F, state.stature$renderScale(), DELTA);
        assertEquals(ScaleAxes.UNIFORM, state.stature$renderShape());
    }

    @Test
    void anUnpushedStateDrawsVanilla() {
        StatureRenderState state = freshState();

        assertEquals(1.0F, state.stature$renderScale(), DELTA);
        assertEquals(ScaleAxes.UNIFORM, state.stature$renderShape());
    }
}
