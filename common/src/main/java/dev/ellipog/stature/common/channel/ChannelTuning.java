package dev.ellipog.stature.common.channel;

import dev.ellipog.stature.api.ChannelSpec;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureScale;

import net.minecraft.world.entity.LivingEntity;

/**
 * The resolved factor of a channel whose effect is Stature's own rather than a vanilla attribute.
 *
 * <p>These channels still use the ordinary channel table: a per-entity override wins, an unset
 * channel follows the scale through its declared exponent, and a frame or command can set either.
 * What sits on top is the server config: a global on/off switch, a strength multiplier for the
 * followed value, and a min/max clamp for the result. The clamp applies to overrides too, because
 * it is a safety bound for hitboxes and explosion radii, not a suggestion.
 *
 * <p>Disabled means exactly vanilla: a factor of one, whether or not an override is stored. The
 * override survives so turning the channel back on does not lose the pack's choice.
 */
public final class ChannelTuning {

    private ChannelTuning() {
    }

    /** The effective factor, or exactly 1.0 when the channel is off or undeclared. */
    public static float factor(LivingEntity entity, String id) {
        String configKey = id.startsWith("stature:")
                ? id.substring("stature:".length())
                : id;
        if (!StatureConfig.enabled(entity, configKey)) {
            return 1.0F;
        }
        ChannelSpec spec = ChannelRegistry.instance().get(id);
        if (spec == null) {
            return 1.0F;
        }
        StatureScale duck = (StatureScale) entity;
        Float override = duck.stature$channelOverrides().get(id);
        float min = StatureConfig.channelMin(id);
        float max = StatureConfig.channelMax(id);
        if (override != null) {
            return clamp(override, min, max);
        }
        float followed = spec.resolve(
                ScaleService.stateOf(entity).scale(), duck.stature$axes(), null)
                .orElse(1.0).floatValue();
        return clamp(followed * StatureConfig.channelStrength(id), min, max);
    }

    /** The linear damage multiplier a projectile inherits from its own scale factor. */
    public static float projectileDamageFactor(float factor) {
        float strength = StatureConfig.projectileDamageStrength();
        return 1.0F + (factor - 1.0F) * strength;
    }

    /**
     * The speed multiplier a projectile inherits.
     *
     * <p>An exponent rather than a linear multiplier: at the default 0.5 a four-times arrow flies
     * twice as fast, which keeps a giant's shots useful without turning them into railguns.
     */
    public static float projectileSpeedFactor(float factor) {
        float exponent = StatureConfig.projectileSpeedStrength();
        if (exponent <= 0.0F || factor == 1.0F) {
            return 1.0F;
        }
        return (float) Math.pow(factor, exponent);
    }

    /** Clamps a factor, treating a corrupt bound as neutral rather than poisoning the caller. */
    static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return 1.0F;
        }
        float lo = Float.isFinite(min) && min > 0.0F ? min : 0.0F;
        float hi = Float.isFinite(max) && max >= lo ? max : Math.max(lo, 1.0F);
        return Math.max(lo, Math.min(hi, value));
    }
}
