package dev.ellipog.stature.common.client;

import net.minecraft.world.phys.Vec3;

/**
 * Camera trauma from macro footsteps.
 *
 * <p>Footstep packets land here with a position, a scale and the camera position; trauma rises
 * with proximity and decays with elapsed time. Only ever touched on the client: the server sends,
 * the camera reads, and nothing on a dedicated server names this class. The camera comes in as a
 * parameter so this file never names a client class, exactly like the render-state interface
 * beside it.
 *
 * <p>The radius is capped at 48 blocks and the falloff is smooth, so a giant next door reads as
 * weather rather than as damage. The caller multiplies by the vanilla Screen Effect slider; this
 * class stays free of client classes by taking that number as an argument.
 */
public final class CameraTrauma {

    /** No footstep is felt beyond this many blocks, however large the entity. */
    static final double MAX_RADIUS = 48.0;

    private static float trauma;

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
     * The trauma a footstep adds at this distance, before the accessibility scale.
     *
     * <p>Squared falloff rather than linear: a linear ramp makes the edge of the radius feel like
     * a switch, while a square one fades out.
     */
    static float strength(double distance, float scale, float effectScale) {
        double range = range(scale);
        if (range <= 0.0 || distance > range) {
            return 0.0F;
        }
        double falloff = 1.0 - distance / range;
        float effect = Float.isFinite(effectScale)
                ? Math.max(0.0F, Math.min(1.0F, effectScale))
                : 1.0F;
        return (float) (falloff * falloff) * Math.min(scale / 2.5F, 2.0F) * effect;
    }

    /**
     * Adds trauma for a footstep, scaled by proximity to the camera and the Screen Effect slider.
     *
     * <p>{@code screenEffectScale} is {@code Options.screenEffectScale().get()} from the client
     * call site. Zero means the player has turned screen effects off and feels nothing.
     */
    public static void addShake(
            double x, double y, double z, float scale, Vec3 camera, float screenEffectScale) {
        double distance = Math.sqrt(
                (camera.x - x) * (camera.x - x)
                        + (camera.y - y) * (camera.y - y)
                        + (camera.z - z) * (camera.z - z));
        float strength = strength(distance, scale, screenEffectScale);
        if (strength <= 0.0F) {
            return;
        }
        trauma = Math.min(1.0F, trauma + strength);
    }

    /**
     * The current trauma, decayed to now.
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
        trauma = Math.max(0.0F, trauma * (float) Math.exp(-3.0 * elapsed));
        return trauma;
    }
}
