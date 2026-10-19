package dev.ellipog.stature.api;

/**
 * The interpolation curves of plan §14.1.
 *
 * <p>A scale change eases from start to target as {@code s(t) = s_start + (s_target - s_start) *
 * E(t)}, where {@code E} is one of these and {@code t} runs 0 to 1 over the transition ticks.
 * Game-free on purpose: the same curve drives the box, the model and the camera, so it lives in
 * the API module where every one of them can name it.
 */
public enum Easing {

    /** No curve: {@code E(t) = t}. */
    LINEAR {
        @Override
        public double apply(double t) {
            return t;
        }
    },

    /** Fast start, soft landing: {@code E(t) = 1 - (1 - t)^3}. */
    EASE_OUT_CUBIC {
        @Override
        public double apply(double t) {
            return 1.0 - Math.pow(1.0 - t, 3.0);
        }
    },

    /** Overshoots and settles: the standard ease-out elastic. */
    ELASTIC {
        @Override
        public double apply(double t) {
            if (t <= 0.0) {
                return 0.0;
            }
            if (t >= 1.0) {
                return 1.0;
            }
            return Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * (2.0 * Math.PI / 3.0)) + 1.0;
        }
    };

    /**
     * The curve value at {@code t}, where {@code t} runs 0 (start) to 1 (target).
     */
    public abstract double apply(double t);

    /**
     * The interpolated value between {@code start} and {@code target} at {@code t}.
     */
    public double lerp(double start, double target, double t) {
        double clamped = Math.min(1.0, Math.max(0.0, t));
        return start + (target - start) * this.apply(clamped);
    }
}
