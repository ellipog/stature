package dev.ellipog.stature.common.client;

import dev.ellipog.armature.api.net.ArmatureNetwork;
import dev.ellipog.stature.common.ResolvedThresholds;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureProjectile;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.net.ConfigPayload;
import dev.ellipog.stature.common.net.FootstepPayload;
import dev.ellipog.stature.common.net.HandshakePayload;
import dev.ellipog.stature.common.net.PartsPayload;
import dev.ellipog.stature.common.net.ProjectileScalePayload;
import dev.ellipog.stature.common.net.ThresholdsPayload;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * What each payload does when it reaches a client.
 *
 * <p>Separate from {@link dev.ellipog.stature.common.net.StaturePackets} so the declaration class
 * names no client type. The registration lambdas in that class point here, and the JVM resolves
 * the reference when the lambda runs on a client, never when the payload is declared on a
 * dedicated server. That is the same arrangement Armature's networking docs describe: name the
 * client path, do not load it.
 *
 * <p>Handlers run on the client's main thread, because both loader backends schedule them there.
 * The entity is looked up by id in the client level rather than trusted from the packet: an id for
 * an entity this client has not received yet is a packet that arrived before its spawn, and the
 * right answer is to ignore it once the next broadcast carries the same state.
 */
public final class StatureClientHandlers {

    private StatureClientHandlers() {
    }

    public static void axes(AxesPayload payload) {
        Entity entity = ClientViews.entity(payload.entityId());
        if (entity instanceof LivingEntity living) {
            ScaleService.applyFromServer(living, payload.state(), payload.eyeHeight());
        }
    }

    /**
     * Puts the server's snapshot on a projectile the client may have derived differently.
     *
     * <p>The spawn path already lets the client guess from its copy of the owner. That guess is
     * unavailable when the owner is not loaded, and stale when the owner has changed size since
     * the shot, so this message is authoritative rather than supplementary. The dimension refresh
     * is what makes the client's box match the new factor in the same frame.
     */
    public static void projectileScale(ProjectileScalePayload payload) {
        Entity entity = ClientViews.entity(payload.entityId());
        if (entity instanceof Projectile projectile && entity instanceof StatureProjectile duck) {
            duck.stature$setProjectileScale(payload.factor());
            projectile.refreshDimensions();
        }
    }

    public static void channels(ChannelsPayload payload) {
        Entity entity = ClientViews.entity(payload.entityId());
        if (entity instanceof LivingEntity living) {
            // Full replace, never merge: a key missing here was removed there, and a merge would
            // keep the dead key forever. The service also reports the differences to
            // StatureEvents on this thread.
            ChannelService.applyFromServer(living, payload.channels());
        }
    }

    public static void parts(PartsPayload payload) {
        Entity entity = ClientViews.entity(payload.entityId());
        if (entity instanceof LivingEntity living) {
            StatureScale duck = (StatureScale) living;
            duck.stature$partFactors().clear();
            duck.stature$partFactors().putAll(payload.parts());
            duck.stature$setFirstPersonVanilla(payload.firstPersonVanilla());
        }
    }

    public static void thresholds(ThresholdsPayload payload) {
        Entity entity = ClientViews.entity(payload.entityId());
        if (entity instanceof LivingEntity living) {
            // Full replace, like the channels: the server resolved this entity's frame over the
            // configured values, and a removal there reads as a re-resolve here rather than a
            // merge that keeps the dead key. The service reports a tier crossing after storing
            // the new edges.
            StatureConfig.applyFromServer(
                    living,
                    new ResolvedThresholds(payload.micro(), payload.macro(), payload.flags()));
        }
    }

    public static void config(ConfigPayload payload) {
        StatureConfig.applySynced(
                payload.micro(), payload.macro(), payload.flags(), payload.tuning());
    }

    public static void footstep(FootstepPayload payload) {
        Vec3 camera = ClientViews.cameraPosition();
        if (camera != null) {
            CameraTrauma.addShake(
                    payload.x(), payload.y(), payload.z(), payload.scale(), camera,
                    ClientViews.screenEffectScale());
        }
    }

    public static void handshake(HandshakePayload payload) {
        ArmatureNetwork.sendToServer(new HandshakePayload());
    }
}
