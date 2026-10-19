package dev.ellipog.stature.common.net;

import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.StatureScale;

import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The seam between Stature's shared code and whichever loader is carrying the packet.
 *
 * <p>Stature depends on nothing (plan: its independence is the product), so it does not use
 * Armature's networking. Until §16 has a home, each loader installs one implementation here at
 * construction and the shared code only ever calls these two methods.
 *
 * <p>Nothing is installed on a client, and every caller is server-only, so an uninstalled seam is
 * silent rather than fatal: a dedicated server that somehow has not installed it stops syncing
 * shapes, which is visible, instead of throwing inside an entity tick.
 */
public final class StatureNetworking {

    /** The loader's half. One method per way a packet leaves the server. */
    public interface Network {

        /** Everyone who could see this entity, in its dimension. */
        void broadcast(LivingEntity entity, AxesPayload payload);

        /** One player, for a state they have not been sent yet. */
        void sendTo(ServerPlayer player, AxesPayload payload);

        /** Everyone who could see this entity, for its channel overrides. */
        void broadcast(LivingEntity entity, ChannelsPayload payload);

        /** One player, for channel overrides they have not been sent yet. */
        void sendTo(ServerPlayer player, ChannelsPayload payload);
    }

    private static Network installed;

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
