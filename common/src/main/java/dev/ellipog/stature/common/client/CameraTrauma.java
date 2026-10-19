package dev.ellipog.stature.common.client;

import net.minecraft.world.phys.Vec3;

/**
 * Camera trauma from macro footsteps.
 *
 * <p>Footstep packets land here with a position, a scale and the camera position; trauma
 * rises with proximity and decays every frame it is sampled. Only ever touched on the
 * client: the server sends, the camera reads, and nothing on a dedicated server names
 * this class. The camera comes in as a parameter so this file never names a client
 * class, exactly like the render-state interface beside it.
 */
public final class CameraTrauma {

    private static float trauma;

    private static long lastNanos = System.nanoTime();

    private CameraTrauma() {
    }

    /** Adds trauma for a footstep, scaled by proximity to the camera. Ignores distant ones. */
    public static void addShake(double x, double y, double z, float scale, Vec3 camera) {
        double distance = Math.sqrt(
                (camera.x - x) * (camera.x - x) + (camera.y - y) * (camera.y - y) + (camera.z - z) * (camera.z - z));
        double range = 16.0 * scale;
        if (distance > range) {
            return;
        }
        float strength = (float) (1.0 - distance / range) * Math.min(scale / 2.5F, 2.0F);
        trauma = Math.min(1.0F, trauma + strength);
    }

    /**
     * The current trauma, decayed to now. Call once per frame: sampling is what advances
     * the decay, so sampling twice advances it twice.
     */
    public static float sample() {
        long now = System.nanoTime();
        float elapsed = (now - lastNanos) / 1.0E9F;
        lastNanos = now;
        trauma = Math.max(0.0F, trauma * (float) Math.exp(-3.0 * elapsed));
        return trauma;
    }
}
