package dev.ellipog.stature.api;

/**
 * One size change, eased from where it started to where it is going (plan §14.1).
 *
 * <pre>
 * s(t) = s_start + (s_target - s_start) * E(t)
 * </pre>
 *
 * <p>Game-free on purpose: the same object drives the bounding box, the model and the camera, and it
 * is the thing a unit test can pin down. The clock is not read here -- {@link #advance()} is one
 * tick, driven by whoever owns the entity -- so a twenty-tick ease is asserted in microseconds
 * rather than by sleeping.
 *
 * <p>The uniform scale and the box's shape ease together, because a change that moved one and not
 * the other would pass through frames nobody asked for. A transition between two identical states is
 * over immediately, which is how "instant" needs no branch of its own.
 *
 * <p>Not thread-safe, and not meant to be: it belongs to one entity, touched on that entity's thread.
 */
public final class ScaleTransition {

    private final ScaleState start;

    private final ScaleState target;

    private final int ticks;

    private final Easing easing;

    private int elapsed;

    private ScaleTransition(ScaleState start, ScaleState target, int ticks, Easing easing) {
        this.start = start;
        this.target = target;
        this.ticks = Math.max(0, ticks);
        this.easing = easing;
    }

    /** A transition that is already over, sitting at {@code state}. */
    public static ScaleTransition at(ScaleState state) {
        return new ScaleTransition(state, state, 0, Easing.LINEAR);
    }

    /**
     * A change from {@code from} to {@code to} over {@code ticks}.
     *
     * <p>A non-positive tick count means "now": the transition is created finished, which is how an
     * instant command and an eased one stay the same code path from here on.
     */
    public static ScaleTransition between(ScaleState from, ScaleState to, int ticks, Easing easing) {
        return new ScaleTransition(from, to, ticks, easing);
    }

    /** Whether the transition has reached its target. */
    public boolean isDone() {
        return this.elapsed >= this.ticks || this.start.equals(this.target);
    }

    /** Where the transition is going. */
    public ScaleState target() {
        return this.target;
    }

    /** Where the transition started. */
    public ScaleState start() {
        return this.start;
    }

    /** How many ticks the whole change takes. */
    public int ticks() {
        return this.ticks;
    }

    /** The curve this transition eases with. */
    public Easing easing() {
        return this.easing;
    }

    /** The size at the current point of the transition. */
    public ScaleState state() {
        if (this.ticks <= 0 || this.elapsed >= this.ticks) {
            return this.target;
        }
        double t = this.easing.apply((double) this.elapsed / this.ticks);
        return ScaleState.lerp(this.start, this.target, t);
    }

    /**
     * Advances one tick and answers with the new state.
     *
     * <p>Clamped at the end rather than running past it, so a caller that ticks one time too many
     * gets the target rather than a number continuing off the curve.
     */
    public ScaleState advance() {
        if (this.elapsed < this.ticks) {
            this.elapsed++;
        }
        return this.state();
    }
}
