package dev.ellipog.stature.common;

import dev.ellipog.stature.common.channel.ChannelTuning;

import net.minecraft.world.entity.LivingEntity;

/**
 * The scale handed to a vehicle's passenger attachment calculation.
 *
 * <p>Vanilla passes the mount's own scale for a living mount, so the base behaviour is already
 * proportional. This channel lets a pack or an operator tune that seat height per mount: the
 * factor replaces the mount's scale, and the ratio to the actual scale is applied to vanilla's
 * argument. At 1.0 (the default) the ratio is exactly one and the call is untouched.
 */
public final class RidingOffset {

    private RidingOffset() {
    }

    /** The seat scale for one mount, or {@code vanillaScale} when the channel is off. */
    public static float seatScale(LivingEntity mount, float vanillaScale) {
        if (!StatureConfig.enabled(mount, StatureConfig.RIDING_OFFSET)) {
            return vanillaScale;
        }
        float factor = ChannelTuning.factor(mount, StatureConfig.RIDING_OFFSET_CHANNEL);
        float base = ScaleService.stateOf(mount).scale();
        if (!Float.isFinite(base) || base <= 0.0F || !Float.isFinite(factor)) {
            return vanillaScale;
        }
        return vanillaScale * (factor / base);
    }
}
