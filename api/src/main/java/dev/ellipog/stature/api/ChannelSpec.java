package dev.ellipog.stature.api;

import java.util.Objects;
import java.util.Optional;

/**
 * One named thing a scale can drive (plan §14.9).
 *
 * <p>A channel is {@code follows} plus {@code exponent} plus an optional override, and nothing
 * else: {@code value = override, else basis ^ exponent}. The basis is whatever the channel
 * follows -- usually the uniform scale -- so an unset channel tracks the entity for free and a
 * single override says "this one stays put while the rest move". Negative exponents are allowed
 * on purpose: knockback received wants {@code s ^ -0.5}, which is smaller when the entity is
 * bigger, and that is the whole point of the field.
 *
 * <p>Game-free: ids are strings here and become {@code Identifier}s where Minecraft starts. The
 * registry, the per-entity values and the effects all live outside this module.
 */
public record ChannelSpec(String id, ChannelFollow follows, double exponent, boolean experimental) {

    public ChannelSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a channel needs a name, got '" + id + "'");
        }
        Objects.requireNonNull(follows, "follows");
        if (!Double.isFinite(exponent)) {
            throw new IllegalArgumentException(
                    "channel " + id + " needs a finite exponent, got " + exponent);
        }
    }

    /**
     * What this channel currently says, if it says anything at all.
     *
     * @param baseScale the uniform scale, the usual basis
     * @param shape     the box's multipliers, for the followers that track girth or stature
     * @param override  an explicit value, which always wins
     * @return empty when the channel follows nothing and carries no override: off, not zero
     */
    public Optional<Double> resolve(float baseScale, ScaleAxes shape, Float override) {
        if (override != null) {
            return Optional.of((double) override);
        }
        double basis = switch (this.follows) {
            case BASE -> baseScale;
            case WIDTH -> shape.width();
            case HEIGHT -> shape.height();
            case DEPTH -> shape.depth();
            case NONE -> Double.NaN;
        };
        if (Double.isNaN(basis)) {
            return Optional.empty();
        }
        return Optional.of(Math.pow(basis, this.exponent));
    }
}
