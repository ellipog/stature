package dev.ellipog.stature.common.client;

import dev.ellipog.stature.common.StatureConfig;

import net.minecraft.world.phys.Vec3;

/**
 * Camera trauma from macro footsteps.
 *
 * <p>Footstep packets land here with a position, a scale and whether the step belongs to the
 * camera's own entity; trauma rises with proximity and decays with elapsed time. Only ever touched
 * on the client: the server sends, the camera reads, and nothing on a dedicated server names this
 * class. The camera comes in as a parameter so this file never names a client class, exactly like
 * the render-state interface beside it.
 *
 * <p>The radius is capped at 48 blocks and the falloff is smooth, so a giant next door reads as
 * weather rather than as damage. The scale multiplier stops growing at 2.0, so an 8x and a 16x step
 * shake a nearby player by the same amount.
 *
 * <p>Own footsteps are a separate, much gentler channel. A giant's camera sits directly above its
 * own feet, so the distance falloff that protects nearby players never protects the walker: before
 * this split, every step saturated trauma to 1.0 and the camera never stopped shaking. Own steps
 * now use their own strength curve and a cap a quarter of the nearby one. The two accumulators are
 * reported as a maximum, not a sum, so a walker's own steps never add on top of a nearby giant's.
 *
 * <p>{@code camera_shake_strength} scales both channels and their caps; 0 turns all shake off.
 * {@code camera_shake_self_strength} scales only own steps and 0 turns those off. The caller
 * multiplies by the vanilla Screen Effect slider, so a player who has turned screen effects off
 * feels nothing either way.
 */
public final class CameraTrauma {

    /** No footstep is felt beyond this many blocks, however large the entity. */
    static final double MAX_RADIUS = 48.0;

    /** The proximity multiplier stops here, so 8x and 16x steps are equally strong. */
    static final float MAX_SCALE_FACTOR = 2.0F;

    /** The most trauma nearby footsteps may hold, before the config multiplier. */
    static final float MAX_OTHER_TRAUMA = 0.6F;

    /** The most trauma your own footsteps may hold, before the config multiplier. */
    static final float MAX_SELF_TRAUMA = 0.15F;

    /** The most trauma the renderer will ever see, whichever channel supplied it. */
    static final float MAX_TRAUMA = 1.0F;

    /** Trauma one own footstep adds at 4x and above, before the config multipliers. */
    static final float SELF_STEP_TRAUMA = 0.18F;

    private static float otherTrauma;

    private static float selfTrauma;

    private static long lastNanos = System.nanoTime();

    private CameraTrauma() {
    }

    /** How far a footstep of this scale can be felt. */
    static double range(float scale) {
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            return 0.0;
        }
        return Math.min(MAX_RADIUS, 16.0 * scale);
    }

    /**
     * The trauma a nearby footstep adds at this distance, before the config multipliers.
     *
     * <p>Squared falloff rather than linear: a linear ramp makes the edge of the radius feel like
     * a switch, while a square one fades out.
     */
    static float strength(double distance, float scale, float effectScale) {
        double range = range(scale);
        if (range <= 0.0 || !Double.isFinite(distance) || distance > range) {
            return 0.0F;
        }
        double falloff = 1.0 - distance / range;
        return (float) (falloff * falloff) * scaleFactor(scale) * effect(effectScale);
    }

    /**
     * The trauma one own footstep adds, before the config multipliers.
     *
     * <p>Own steps do not use the distance falloff: the camera is always at the top of the walker,
     * so the distance says nothing about how hard the step should feel. They ramp with scale up to
     * the giant preset and hold there. This is deliberately much smaller than a nearby step.
     */
    static float selfStrength(float scale, float effectScale) {
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            return 0.0F;
        }
        return SELF_STEP_TRAUMA * Math.min(scale / 4.0F, 1.0F) * effect(effectScale);
    }

    /** The capped scale term, so a larger giant does not keep adding shake forever. */
    static float scaleFactor(float scale) {
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            return 0.0F;
        }
        return Math.min(scale / 2.5F, MAX_SCALE_FACTOR);
    }

    /** The trauma one nearby footstep adds, including the global multiplier. */
    static float otherStep(
            double distance, float scale, float effectScale, float strengthMultiplier) {
        return strength(distance, scale, effectScale) * multiplier(strengthMultiplier);
    }

    /** The trauma one own footstep adds, including both multipliers. */
    static float selfStep(
            float scale, float effectScale, float strengthMultiplier, float selfMultiplier) {
        return selfStrength(scale, effectScale)
                * multiplier(strengthMultiplier)
                * multiplier(selfMultiplier);
    }

    /** The cap on nearby trauma, including the global multiplier. */
    static float otherCap(float strengthMultiplier) {
        return Math.min(MAX_TRAUMA, MAX_OTHER_TRAUMA * multiplier(strengthMultiplier));
    }

    /** The cap on own trauma. Zero when either switch is zero, so both can turn self shake off. */
    static float selfCap(float strengthMultiplier, float selfMultiplier) {
        if (multiplier(selfMultiplier) <= 0.0F) {
            return 0.0F;
        }
        return Math.min(MAX_TRAUMA, MAX_SELF_TRAUMA * multiplier(strengthMultiplier));
    }

    /** One accumulator step: add, then clamp to the cap. Pure so the caps are testable. */
    static float accumulate(float current, float added, float cap) {
        if (!Float.isFinite(cap) || cap <= 0.0F) {
            return 0.0F;
        }
        float base = Float.isFinite(current) && current > 0.0F ? current : 0.0F;
        float add = Float.isFinite(added) && added > 0.0F ? added : 0.0F;
        return Math.min(cap, base + add);
    }

    /**
     * Adds trauma for a footstep, scaled by proximity to the camera and the Screen Effect slider.
     *
     * <p>{@code screenEffectScale} is {@code Options.screenEffectScale().get()} from the client
     * call site. Zero means the player has turned screen effects off and feels nothing.
     * {@code self} is true when the footstep belongs to the camera's own entity; own steps use the
     * gentler curve and the lower cap.
     */
    public static void addShake(
            double x, double y, double z, float scale, Vec3 camera, float screenEffectScale,
            boolean self) {
        float global = StatureConfig.cameraShakeStrength();
        if (self) {
            float own = StatureConfig.cameraShakeSelfStrength();
            float step = selfStep(scale, screenEffectScale, global, own);
            if (step > 0.0F) {
                selfTrauma = accumulate(selfTrauma, step, selfCap(global, own));
            }
            return;
        }

        double distance = Math.sqrt(
                (camera.x - x) * (camera.x - x)
                        + (camera.y - y) * (camera.y - y)
                        + (camera.z - z) * (camera.z - z));
        float step = otherStep(distance, scale, screenEffectScale, global);
        if (step > 0.0F) {
            otherTrauma = accumulate(otherTrauma, step, otherCap(global));
        }
    }

    /**
     * The current trauma, decayed to now.
     *
     * <p>The two channels decay independently and the stronger one wins, so own steps cannot add on
     * top of a nearby giant's. The caps are re-read here, which is what makes a config reload lower
     * the shake immediately instead of waiting for the old trauma to decay.
     *
     * <p>The decay is computed from elapsed nanoseconds, so calling this twice in one frame
     * advances the decay twice but composes to the same total: the first call decays to now and
     * the second decays by the near-zero time since. {@code bobView} runs twice per frame (world
     * and hand) and that is harmless. The old javadoc's "call once per frame" warning described a
     * per-call decay rate, which is not what this does.
     */
    public static float sample() {
        long now = System.nanoTime();
        float elapsed = (now - lastNanos) / 1.0E9F;
        lastNanos = now;
        float decay = (float) Math.exp(-3.0 * elapsed);

        float global = StatureConfig.cameraShakeStrength();
        float own = StatureConfig.cameraShakeSelfStrength();
        otherTrauma = accumulate(otherTrauma * decay, 0.0F, otherCap(global));
        selfTrauma = accumulate(selfTrauma * decay, 0.0F, selfCap(global, own));
        return Math.max(otherTrauma, selfTrauma);
    }

    /** Forgets every accumulator. Tests only: the game never needs to drop a shake mid-frame. */
    static void reset() {
        otherTrauma = 0.0F;
        selfTrauma = 0.0F;
        lastNanos = System.nanoTime();
    }

    /** The Screen Effect slider, defaulting to full when the value is unusable. */
    private static float effect(float effectScale) {
        if (!Float.isFinite(effectScale)) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, effectScale));
    }

    /** A config multiplier, with a bad value treated as off rather than as maximum shake. */
    private static float multiplier(float value) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            return 0.0F;
        }
        return value;
    }
}
