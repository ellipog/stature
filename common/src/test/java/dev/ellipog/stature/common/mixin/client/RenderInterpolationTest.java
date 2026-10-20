package dev.ellipog.stature.common.mixin.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.client.RenderSampler;
import dev.ellipog.stature.common.client.StatureRenderState;

import org.junit.jupiter.api.Test;

/**
 * The render mixins cannot run headlessly, but the blend math is just a class: ticking a
 * {@link RenderSampler} and reading it at a partial tick exercises exactly what
 * {@code extractRenderState} does with the entity's sampler, with no game running. What this
 * proves is the frame logic -- samples shift on tick boundaries and blend within a frame --
 * plus the one guarantee the state still makes: a pushed sample reads back whole, and an
 * unpushed state draws exactly vanilla.
 */
class RenderInterpolationTest {

    private static final float DELTA = 0.0001F;

    private static final ScaleAxes SHAPED = new ScaleAxes(0.5F, 1.2F, 2.0F);

    @Test
    void aFreshSamplerReadsVanilla() {
        RenderSampler sampler = new RenderSampler();

        assertEquals(1.0F, sampler.scale(0.5F), DELTA);
        assertEquals(ScaleAxes.UNIFORM, sampler.axes(0.5F));
    }

    @Test
    void aFrameBlendsHalfwayBetweenTwoTicks() {
        RenderSampler sampler = new RenderSampler();
        sampler.tick(1.0F, ScaleAxes.UNIFORM);
        sampler.tick(2.0F, SHAPED);

        assertEquals(1.5F, sampler.scale(0.5F), DELTA);
        assertEquals(ScaleAxes.lerp(ScaleAxes.UNIFORM, SHAPED, 0.5), sampler.axes(0.5F));
    }

    @Test
    void aFinishedTickReadsTheNewSampleWhole() {
        RenderSampler sampler = new RenderSampler();
        sampler.tick(1.0F, ScaleAxes.UNIFORM);
        sampler.tick(2.0F, SHAPED);

        assertEquals(2.0F, sampler.scale(1.0F), DELTA);
        assertEquals(SHAPED, sampler.axes(1.0F));
    }

    @Test
    void theFrameStartReadsThePreviousSampleWhole() {
        RenderSampler sampler = new RenderSampler();
        sampler.tick(1.0F, ScaleAxes.UNIFORM);
        sampler.tick(2.0F, SHAPED);

        assertEquals(1.0F, sampler.scale(0.0F), DELTA);
        assertEquals(ScaleAxes.UNIFORM, sampler.axes(0.0F));
    }

    @Test
    void samplesShiftEveryTick() {
        RenderSampler sampler = new RenderSampler();
        sampler.tick(1.0F, ScaleAxes.UNIFORM);
        sampler.tick(2.0F, SHAPED);
        sampler.tick(4.0F, ScaleAxes.UNIFORM);

        assertEquals(3.0F, sampler.scale(0.5F), DELTA);
    }

    @Test
    void aCorruptPartialTickDegradesToThePreviousSample() {
        RenderSampler sampler = new RenderSampler();
        sampler.tick(1.0F, ScaleAxes.UNIFORM);
        sampler.tick(2.0F, SHAPED);

        assertEquals(1.0F, sampler.scale(Float.NaN), DELTA);
        assertEquals(ScaleAxes.UNIFORM, sampler.axes(Float.NaN));
    }

    private static StatureRenderState freshState() {
        return new LivingEntityRenderStateMixin() {
        };
    }

    @Test
    void aPushedSampleReadsBackWhole() {
        StatureRenderState state = freshState();
        state.stature$pushVisual(2.0F, 2.0F, SHAPED);

        assertEquals(2.0F, state.stature$visualScale(), DELTA);
        assertEquals(2.0F, state.stature$attributeScale(), DELTA);
        assertEquals(SHAPED, state.stature$visualShape());
    }

    @Test
    void aLaterPushReplacesTheWholeSample() {
        StatureRenderState state = freshState();
        state.stature$pushVisual(3.0F, 3.0F, SHAPED);
        state.stature$pushVisual(1.0F, 1.0F, ScaleAxes.UNIFORM);

        assertEquals(1.0F, state.stature$visualScale(), DELTA);
        assertEquals(ScaleAxes.UNIFORM, state.stature$visualShape());
    }

    @Test
    void anUnpushedStateDrawsVanilla() {
        StatureRenderState state = freshState();

        assertEquals(1.0F, state.stature$visualScale(), DELTA);
        assertEquals(1.0F, state.stature$attributeScale(), DELTA);
        assertEquals(ScaleAxes.UNIFORM, state.stature$visualShape());
        assertFalse(state.stature$hidden());
        assertEquals(1.0F, state.stature$heldFactor(), DELTA);
        assertEquals(1.0F, state.stature$shadowFactor(), DELTA);
    }

    @Test
    void pushedChannelsReadBackWhole() {
        StatureRenderState state = freshState();
        state.stature$pushChannels(Map.of("stature:visibility", 0.0F, "stature:held_item", 2.0F));

        assertTrue(state.stature$hidden());
        assertEquals(2.0F, state.stature$heldFactor(), DELTA);
        assertEquals(1.0F, state.stature$shadowFactor(), DELTA);
    }

    @Test
    void aLaterPushReplacesTheWholeMap() {
        StatureRenderState state = freshState();
        state.stature$pushChannels(Map.of("stature:visibility", 0.0F));
        state.stature$pushChannels(Map.of());

        assertFalse(state.stature$hidden());
    }

    @Test
    void corruptFactorsDegradeToNeutral() {
        StatureRenderState state = freshState();
        state.stature$pushChannels(Map.of("stature:shadow", Float.NaN, "stature:held_item", -3.0F));

        assertEquals(1.0F, state.stature$shadowFactor(), DELTA);
        assertEquals(1.0F, state.stature$heldFactor(), DELTA);
    }
}
