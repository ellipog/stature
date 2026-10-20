package dev.ellipog.stature.common.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChannelTuningTest {

    @Test
    void clampKeepsTheConfiguredBounds() {
        assertEquals(0.25F, ChannelTuning.clamp(0.01F, 0.25F, 4.0F));
        assertEquals(4.0F, ChannelTuning.clamp(16.0F, 0.25F, 4.0F));
        assertEquals(1.0F, ChannelTuning.clamp(1.0F, 0.25F, 4.0F));
    }

    @Test
    void corruptBoundsAndValuesDegradeToNeutral() {
        assertEquals(1.0F, ChannelTuning.clamp(Float.NaN, 0.25F, 4.0F));
        assertEquals(1.0F, ChannelTuning.clamp(1.0F, Float.NaN, Float.NaN));
        assertEquals(0.25F, ChannelTuning.clamp(-1.0F, 0.25F, 4.0F));
    }

    @Test
    void anInvertedPairCollapsesToItsLowerBound() {
        assertEquals(5.0F, ChannelTuning.clamp(2.0F, 5.0F, 1.0F));
    }
}
