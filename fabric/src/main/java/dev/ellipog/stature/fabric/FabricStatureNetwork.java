package dev.ellipog.stature.fabric;

import java.util.LinkedHashSet;
import java.util.Set;

import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.net.ConfigPayload;
import dev.ellipog.stature.common.net.FootstepPayload;
import dev.ellipog.stature.common.net.PartsPayload;
import dev.ellipog.stature.common.net.ThresholdsPayload;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Fabric half of the size sync.
 *
 * <p>Sends go to the players actually tracking the entity, plus the entity itself when it is a
 * player: your own entity never appears in your tracking set, so without the self leg a player's
 * own shape would only arrive through the twenty-tick re-send. A footstep goes to players within
 * its audible radius instead of the whole dimension. Before M5 both used {@code level.players()},
 * which broadcast every shape and every step to everyone in the dimension.
 */
final class FabricStatureNetwork implements StatureNetworking.Network {

    @Override
    public void broadcast(LivingEntity entity, AxesPayload payload) {
        send(entity, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, AxesPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void broadcast(LivingEntity entity, ChannelsPayload payload) {
        send(entity, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, ChannelsPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void broadcast(LivingEntity entity, PartsPayload payload) {
        send(entity, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, PartsPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, ConfigPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void broadcast(LivingEntity entity, ThresholdsPayload payload) {
        send(entity, payload);
    }

    @Override
    public void sendTo(ServerPlayer player, ThresholdsPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendNear(ServerLevel level, double x, double y, double z, double radius,
            FootstepPayload payload) {
        for (ServerPlayer player : PlayerLookup.around(level, new Vec3(x, y, z), radius)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void send(LivingEntity entity, CustomPacketPayload payload) {
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        Set<ServerPlayer> viewers = new LinkedHashSet<>(PlayerLookup.tracking(entity));
        if (entity instanceof ServerPlayer self) {
            // PlayerLookup.tracking excludes the player themselves. The self leg is what makes
            // /stature set @s visible to @s without waiting for the periodic self re-send.
            viewers.add(self);
        }
        for (ServerPlayer player : viewers) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
