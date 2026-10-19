package dev.ellipog.stature.fabric;

import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Fabric half of the size sync.
 *
 * <p>Broadcast goes to everyone in the dimension rather than to a tracking set: Stature's packet is
 * sent when a shape changes and not once per tick, and asking the level for its players is a line of
 * code that cannot disagree with the server's own tracking bookkeeping. A client that can see the
 * dimension and does not care about the entity discards thirty bytes.
 */
final class FabricStatureNetwork implements StatureNetworking.Network {

    @Override
    public void broadcast(LivingEntity entity, AxesPayload payload) {
        if (entity.level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Override
    public void sendTo(ServerPlayer player, AxesPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
