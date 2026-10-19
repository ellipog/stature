package dev.ellipog.stature.neoforge;

import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.net.PartsPayload;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The NeoForge half of the size sync.
 *
 * <p>Broadcast goes to everyone in the dimension rather than to a tracking set: the packet is sent
 * when a shape changes and not once per tick, and NeoForge's own distributor is the thing that
 * already knows how to reach a dimension's listeners.
 */
final class NeoForgeStatureNetwork implements StatureNetworking.Network {

    @Override
    public void broadcast(LivingEntity entity, AxesPayload payload) {
        if (entity.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayersInDimension(level, payload);
        }
    }

    @Override
    public void sendTo(ServerPlayer player, AxesPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void broadcast(LivingEntity entity, ChannelsPayload payload) {
        if (entity.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayersInDimension(level, payload);
        }
    }

    @Override
    public void sendTo(ServerPlayer player, ChannelsPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void broadcast(LivingEntity entity, PartsPayload payload) {
        if (entity.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayersInDimension(level, payload);
        }
    }

    @Override
    public void sendTo(ServerPlayer player, PartsPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
