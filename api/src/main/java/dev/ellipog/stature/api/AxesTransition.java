package dev.ellipog.stature.api;

import java.util.Objects;

/**
 * One part factor changing, eased like a {@link ChannelTransition} but over three axes.
 * Same clock discipline: the owner advances it once per tick.
 */
public final class AxesTransition {

    private final ScaleAxes start;

    private final ScaleAxes target;

    private final int ticks;

    private final Easing easing;

    private int elapsed;

    private AxesTransition(ScaleAxes start, ScaleAxes target, int ticks, Easing easing) {
        this.start = start;
        this.target = target;
        this.ticks = Math.max(0, ticks);
        this.easing = easing;
    }

    /** A change from {@code from} to {@code to} over {@code ticks}; non-positive means now. */
    public static AxesTransition between(ScaleAxes from, ScaleAxes to, int ticks, Easing easing) {
        return new AxesTransition(
                Objects.requireNonNull(from, "from"),
                Objects.requireNonNull(to, "to"),
                ticks,
                Objects.requireNonNull(easing, "easing"));
    }

    /** Whether the transition has reached its target. */
    public boolean isDone() {
        return this.elapsed >= this.ticks || this.start.equals(this.target);
    }

    /** Where the transition is going. */
    public ScaleAxes target() {
        return this.target;
    }

    /** The factors at the current point of the transition. */
    public ScaleAxes factors() {
        if (this.ticks <= 0 || this.elapsed >= this.ticks) {
            return this.target;
        }
        double t = this.easing.apply((double) this.elapsed / this.ticks);
        return ScaleAxes.lerp(this.start, this.target, t);
    }

    /** Advances one tick and answers with the new factors, clamped at the target. */
    public ScaleAxes advance() {
        if (this.elapsed < this.ticks) {
            this.elapsed++;
        }
        return this.factors();
    }
}
