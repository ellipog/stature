package dev.ellipog.stature.common.net;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ResolvedThresholds;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureScale;

import java.util.Map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The seam between Stature's shared code and whichever loader is carrying the packet.
 *
 * <p>Stature depends on nothing (plan: its independence is the product), so it does not use
 * Armature's networking. Each loader installs one implementation here at construction and the
 * shared code only ever calls these methods.
 *
 * <p>A {@code broadcast} means "the players tracking this entity, plus the entity itself when it is
 * a player": the old implementation sent to the whole dimension. A footstep uses {@code sendNear}
 * with its audible radius instead. Nothing is installed on a client, and every caller is
 * server-only, so an uninstalled seam is silent rather than fatal: a dedicated server that somehow
 * has not installed it stops syncing shapes, which is visible, instead of throwing inside an entity
 * tick.
 */
public final class StatureNetworking {

    /** The loader's half. One method per way a packet leaves the server. */
    public interface Network {

        /** The players tracking this entity, plus the entity itself when it is a player. */
        void broadcast(LivingEntity entity, AxesPayload payload);

        /** One player, for a state they have not been sent yet. */
        void sendTo(ServerPlayer player, AxesPayload payload);

        /** The players tracking this entity, for its channel overrides. */
        void broadcast(LivingEntity entity, ChannelsPayload payload);

        /** One player, for channel overrides they have not been sent yet. */
        void sendTo(ServerPlayer player, ChannelsPayload payload);

        /** The players tracking this entity, for its part factors. */
        void broadcast(LivingEntity entity, PartsPayload payload);

        /** One player, for part factors they have not been sent yet. */
        void sendTo(ServerPlayer player, PartsPayload payload);

        /** One player, for the threshold tiers and flags. */
        void sendTo(ServerPlayer player, ConfigPayload payload);

        /** The players tracking this entity, for its resolved thresholds. */
        void broadcast(LivingEntity entity, ThresholdsPayload payload);

        /** One player, for resolved thresholds they have not been sent yet. */
        void sendTo(ServerPlayer player, ThresholdsPayload payload);

        /** Players near a macro footstep, for its camera shake. */
        void sendNear(ServerLevel level, double x, double y, double z, double radius, FootstepPayload payload);
    }

    private static volatile Network installed;

    private StatureNetworking() {
    }

    public static void install(Network network) {
        installed = network;
    }

    public static void broadcast(LivingEntity entity, ScaleState state) {
        if (installed != null) {
            installed.broadcast(entity, payload(entity, state));
        }
    }

    public static void sendTo(ServerPlayer player, LivingEntity entity, ScaleState state) {
        if (installed != null) {
            installed.sendTo(player, payload(entity, state));
        }
    }

    public static void broadcast(LivingEntity entity, Map<String, Float> channels) {
        if (installed != null) {
            installed.broadcast(entity, new ChannelsPayload(entity.getId(), channels));
        }
    }

    public static void sendTo(ServerPlayer player, LivingEntity entity, Map<String, Float> channels) {
        if (installed != null) {
            installed.sendTo(player, new ChannelsPayload(entity.getId(), channels));
        }
    }

    public static void broadcastParts(LivingEntity entity, Map<String, ScaleAxes> parts) {
        if (installed != null) {
            installed.broadcast(entity, new PartsPayload(entity.getId(), parts,
                    ((StatureScale) entity).stature$firstPersonVanilla()));
        }
    }

    public static void sendPartsTo(ServerPlayer player, LivingEntity entity, Map<String, ScaleAxes> parts) {
        if (installed != null) {
            installed.sendTo(player, new PartsPayload(entity.getId(), parts,
                    ((StatureScale) entity).stature$firstPersonVanilla()));
        }
    }

    public static void sendConfigTo(ServerPlayer player, float micro, float macro, Map<String, Boolean> flags) {
        if (installed != null) {
            installed.sendTo(player, new ConfigPayload(micro, macro, flags));
        }
    }

    public static void broadcastThresholds(LivingEntity entity, ResolvedThresholds thresholds) {
        if (installed != null) {
            installed.broadcast(entity,
                    new ThresholdsPayload(entity.getId(), thresholds.micro(), thresholds.macro(), thresholds.flags()));
        }
    }

    public static void sendThresholdsTo(ServerPlayer player, LivingEntity entity, ResolvedThresholds thresholds) {
        if (installed != null) {
            installed.sendTo(player,
                    new ThresholdsPayload(entity.getId(), thresholds.micro(), thresholds.macro(), thresholds.flags()));
        }
    }

    /**
     * Sends one macro footstep to the players close enough to hear it.
     *
     * <p>{@code volume} is the volume the sound was actually played at, so the radius follows the
     * same 16-blocks-per-volume rule vanilla uses and is capped at 64. A whole-dimension broadcast
     * used to shake clients that could not hear anything.
     */
    public static void broadcastFootstep(LivingEntity entity, float volume) {
        if (installed != null && entity.level() instanceof ServerLevel level) {
            installed.sendNear(level, entity.getX(), entity.getY(), entity.getZ(),
                    FootstepScope.radius(volume), new FootstepPayload(
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
