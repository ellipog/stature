package dev.ellipog.stature.common.net;

import dev.ellipog.armature.api.net.ArmatureNetwork;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ResolvedThresholds;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.client.StatureClientHandlers;

import java.util.Map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Stature's payload declarations, and the scoped sends that carry them.
 *
 * <p>Declaring is separate from sending because Armature's networking is a declaration, not a
 * call: each loader registers the same list at the moment it accepts one. This class names no
 * loader, and the per-loader backends under {@code fabric/} and {@code neoforge/} are the only
 * files that do.
 *
 * <p>A {@code broadcast} means "the players tracking this entity, plus the entity itself when it is
 * a player": the old hand-rolled seam sent to the whole dimension. A footstep uses {@code sendNear}
 * with its audible radius instead. Nothing is installed on a client, and every caller is
 * server-only, so an uninstalled seam is silent rather than fatal.
 */
public final class StaturePackets {

    private static boolean declared;

    private StaturePackets() {
    }

    /**
     * Declares every payload, once.
     *
     * <p>Called from both entry points because Fabric does not promise which runs first; the
     * boolean is what makes a second call a no-op rather than a duplicate registration the loader
     * would refuse.
     */
    public static void declare() {
        if (declared) {
            return;
        }
        declared = true;

        ArmatureNetwork.register(new ArmatureNetwork.Registration<>(
                AxesPayload.TYPE,
                AxesPayload.STREAM_CODEC,
                ArmatureNetwork.Direction.TO_CLIENT,
                payload -> StatureClientHandlers.axes(payload),
                null));

        ArmatureNetwork.register(new ArmatureNetwork.Registration<>(
                ChannelsPayload.TYPE,
                ChannelsPayload.STREAM_CODEC,
                ArmatureNetwork.Direction.TO_CLIENT,
                payload -> StatureClientHandlers.channels(payload),
                null));

        ArmatureNetwork.register(new ArmatureNetwork.Registration<>(
                PartsPayload.TYPE,
                PartsPayload.STREAM_CODEC,
                ArmatureNetwork.Direction.TO_CLIENT,
                payload -> StatureClientHandlers.parts(payload),
                null));

        ArmatureNetwork.register(new ArmatureNetwork.Registration<>(
                ThresholdsPayload.TYPE,
                ThresholdsPayload.STREAM_CODEC,
                ArmatureNetwork.Direction.TO_CLIENT,
                payload -> StatureClientHandlers.thresholds(payload),
                null));

        ArmatureNetwork.register(new ArmatureNetwork.Registration<>(
                ConfigPayload.TYPE,
                ConfigPayload.STREAM_CODEC,
                ArmatureNetwork.Direction.TO_CLIENT,
                payload -> StatureClientHandlers.config(payload),
                null));

        ArmatureNetwork.register(new ArmatureNetwork.Registration<>(
                FootstepPayload.TYPE,
                FootstepPayload.STREAM_CODEC,
                ArmatureNetwork.Direction.TO_CLIENT,
                payload -> StatureClientHandlers.footstep(payload),
                null));

        ArmatureNetwork.register(new ArmatureNetwork.Registration<>(
                HandshakePayload.TYPE,
                HandshakePayload.STREAM_CODEC,
                ArmatureNetwork.Direction.BIDIRECTIONAL,
                payload -> StatureClientHandlers.handshake(payload),
                (payload, player) -> StatureHandshake.answered(player.getUUID())));
    }

    public static void broadcast(LivingEntity entity, ScaleState state) {
        ArmatureNetwork.sendToTracking(entity, payload(entity, state));
    }

    public static void sendTo(ServerPlayer player, LivingEntity entity, ScaleState state) {
        ArmatureNetwork.sendToPlayer(player, payload(entity, state));
    }

    public static void broadcast(LivingEntity entity, Map<String, Float> channels) {
        ArmatureNetwork.sendToTracking(entity, new ChannelsPayload(entity.getId(), channels));
    }

    public static void sendTo(ServerPlayer player, LivingEntity entity, Map<String, Float> channels) {
        ArmatureNetwork.sendToPlayer(player, new ChannelsPayload(entity.getId(), channels));
    }

    public static void broadcastParts(LivingEntity entity, Map<String, ScaleAxes> parts) {
        ArmatureNetwork.sendToTracking(entity, new PartsPayload(entity.getId(), parts,
                ((StatureScale) entity).stature$firstPersonVanilla()));
    }

    public static void sendPartsTo(ServerPlayer player, LivingEntity entity,
                                   Map<String, ScaleAxes> parts) {
        ArmatureNetwork.sendToPlayer(player, new PartsPayload(entity.getId(), parts,
                ((StatureScale) entity).stature$firstPersonVanilla()));
    }

    public static void sendConfigTo(
            ServerPlayer player, float micro, float macro,
            Map<String, Boolean> flags, Map<String, Float> tuning) {
        ArmatureNetwork.sendToPlayer(player, new ConfigPayload(micro, macro, flags, tuning));
    }

    public static void broadcastThresholds(LivingEntity entity, ResolvedThresholds thresholds) {
        ArmatureNetwork.sendToTracking(entity,
                new ThresholdsPayload(entity.getId(), thresholds.micro(), thresholds.macro(),
                        thresholds.flags()));
    }

    public static void sendThresholdsTo(ServerPlayer player, LivingEntity entity,
                                        ResolvedThresholds thresholds) {
        ArmatureNetwork.sendToPlayer(player,
                new ThresholdsPayload(entity.getId(), thresholds.micro(), thresholds.macro(),
                        thresholds.flags()));
    }

    /**
     * Sends one macro footstep to the players close enough to hear it.
     *
     * <p>{@code volume} is the volume the sound was actually played at, so the radius follows the
     * same 16-blocks-per-volume rule vanilla uses and is capped at 64. A whole-dimension broadcast
     * used to shake clients that could not hear anything.
     */
    public static void broadcastFootstep(LivingEntity entity, float volume) {
        if (entity.level() instanceof ServerLevel level) {
            ArmatureNetwork.sendNear(
                    level,
                    new Vec3(entity.getX(), entity.getY(), entity.getZ()),
                    FootstepScope.radius(volume),
                    new FootstepPayload(
                            entity.getX(), entity.getY(), entity.getZ(),
                            ScaleService.stateOf(entity).scale()));
        }
    }

    private static AxesPayload payload(LivingEntity entity, ScaleState state) {
        return new AxesPayload(
                entity.getId(),
                state.scale(),
                state.axes().width(),
                state.axes().height(),
                state.axes().depth(),
                ((StatureScale) entity).stature$eyeHeight());
    }
}
