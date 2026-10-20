package dev.ellipog.stature.common.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only views over game state, for receivers that run where the game is whole.
 *
 * <p>Every signature here is vanilla types only, so referencing this class never loads a
 * client class by itself; the bodies run exclusively on the client. Nothing on a server
 * path may call in, which is what keeps that promise.
 */
public final class ClientViews {

    private ClientViews() {
    }

    /** An entity in the client level by id, or null before the world exists or before its spawn. */
    public static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    /** Where the camera entity stands, or null before the world exists. */
    public static Vec3 cameraPosition() {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        return camera == null ? null : camera.position();
    }

    /**
     * The vanilla Screen Effect accessibility slider, 0 to 1.
     *
     * <p>Read at the client call site so {@code CameraTrauma} never names a client class. The
     * option lives on the client's own options object, not on the server.
     */
    public static float screenEffectScale() {
        return Minecraft.getInstance().options.screenEffectScale().get().floatValue();
    }
}
