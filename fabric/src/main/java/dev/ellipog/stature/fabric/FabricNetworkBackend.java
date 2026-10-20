package dev.ellipog.stature.fabric;

import dev.ellipog.armature.api.net.ArmatureNetwork;
import dev.ellipog.armature.api.net.NetworkBackend;

import java.util.LinkedHashSet;
import java.util.Set;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Armature's networking, on Fabric.
 *
 * <p>Fabric registers in two parts. The format half is common to both sides and done by
 * {@link #register}; the receiver half is per side, so the server receivers are registered here
 * from the common initialiser and the client receivers in {@link FabricClientNetworking}. The
 * split is the reason Armature's backend exists at all: NeoForge takes both halves in one call
 * inside an event that fires later.
 */
final class FabricNetworkBackend implements NetworkBackend {

    @Override
    public <T extends CustomPacketPayload> void register(ArmatureNetwork.Registration<T> registration) {
        if (registration.direction().reachesClient()) {
            PayloadTypeRegistry.clientboundPlay().register(registration.type(), registration.codec());
        }
        if (registration.direction().reachesServer()) {
            PayloadTypeRegistry.serverboundPlay().register(registration.type(), registration.codec());
        }
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToTracking(Entity entity, CustomPacketPayload payload) {
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

    @Override
    public void sendNear(ServerLevel level, Vec3 at, double radius, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.around(level, at, radius)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // Delegated: ClientPlayNetworking is a client-only class and this one is loaded on both
        // sides. Sending to the server from a dedicated server cannot happen, and routing through
        // the client holder is what keeps the server from ever loading it.
        FabricClientNetworking.sendToServer(payload);
    }

    /**
     * Registers the server-side receivers for every payload that arrives at a server.
     *
     * <p>Called from the common initialiser, because a client with an integrated server needs
     * them too. {@code registerGlobalReceiver} rather than the per-connection form: these
     * handlers want nothing from one player's session, and the global form survives a respawn.
     */
    static void registerReceivers() {
        for (ArmatureNetwork.Registration<?> registration : ArmatureNetwork.registrations()) {
            if (registration.direction().reachesServer()) {
                registerServerReceiver(registration);
            }
        }
    }

    private static <T extends CustomPacketPayload> void registerServerReceiver(
            ArmatureNetwork.Registration<T> registration) {
        ServerPlayNetworking.registerGlobalReceiver(registration.type(),
                (payload, context) -> registration.onServer().accept(payload, context.player()));
    }
}
