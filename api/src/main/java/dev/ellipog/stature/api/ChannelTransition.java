package dev.ellipog.stature.api;

import java.util.Objects;

/**
 * One channel value changing, eased from where it started to where it is going.
 *
 * <p>The scalar sibling of {@link ScaleTransition}: same clock discipline (whoever owns the entity
 * calls {@link #advance()} once per tick), same instant-when-timeless rule, and the same reason
 * for existing -- a value that snapped would pass through states nobody asked for. Not
 * thread-safe, and not meant to be: it belongs to one entity, touched on that entity's thread.
 */
public final class ChannelTransition {

    private final float start;

    private final float target;

    private final int ticks;

    private final Easing easing;

    private int elapsed;

    private ChannelTransition(float start, float target, int ticks, Easing easing) {
        this.start = start;
        this.target = target;
        this.ticks = Math.max(0, ticks);
        this.easing = easing;
    }

    /**
     * A change from {@code from} to {@code to} over {@code ticks}.
     *
     * <p>A non-positive tick count means "now": the transition is created finished, which is how an
     * instant command and an eased one stay the same code path from here on.
     */
    public static ChannelTransition between(float from, float to, int ticks, Easing easing) {
        return new ChannelTransition(from, to, ticks, Objects.requireNonNull(easing, "easing"));
    }

    /** Whether the transition has reached its target. */
    public boolean isDone() {
        return this.elapsed >= this.ticks || this.start == this.target;
    }

    /** Where the transition is going. */
    public float target() {
        return this.target;
    }

    /** The value at the current point of the transition. */
    public float value() {
        if (this.ticks <= 0 || this.elapsed >= this.ticks) {
            return this.target;
        }
        return (float) (this.start
                + (this.target - this.start) * this.easing.apply((double) this.elapsed / this.ticks));
    }

    /**
     * Advances one tick and answers with the new value.
     *
     * <p>Clamped at the end rather than running past it, so a caller that ticks one time too many
     * gets the target rather than a number continuing off the curve.
     */
    public float advance() {
        if (this.elapsed < this.ticks) {
            this.elapsed++;
        }
        return this.value();
    }
}
