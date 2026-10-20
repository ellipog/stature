package dev.ellipog.stature.neoforge;

import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.net.ConfigPayload;
import dev.ellipog.stature.common.net.FootstepPayload;
import dev.ellipog.stature.common.net.PartsPayload;
import dev.ellipog.stature.common.net.ThresholdsPayload;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The NeoForge half of the size sync.
 *
 * <p>{@code sendToPlayersTrackingEntityAndSelf} is NeoForge's one call for "everyone who can see
 * this, including the player themselves". A footstep uses {@code sendToPlayersNear} with the
 * audible radius instead of the old dimension-wide send. Start-tracking resync still covers a
 * viewer who arrives after the packet.
 */
final class NeoForgeStatureNetwork implements StatureNetworking.Network {

    @Override
    public void broadcast(LivingEntity entity, AxesPayload payload) {
        send(entity, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, AxesPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void broadcast(LivingEntity entity, ChannelsPayload payload) {
        send(entity, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, ChannelsPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void broadcast(LivingEntity entity, PartsPayload payload) {
        send(entity, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, PartsPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, ConfigPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void broadcast(LivingEntity entity, ThresholdsPayload payload) {
        send(entity, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, ThresholdsPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendNear(ServerLevel level, double x, double y, double z, double radius,
            FootstepPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, radius, payload);
    }

    private static void send(LivingEntity entity, CustomPacketPayload payload) {
        if (entity.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        }
    }
}
