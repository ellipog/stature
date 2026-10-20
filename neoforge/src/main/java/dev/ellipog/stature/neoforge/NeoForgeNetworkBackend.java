package dev.ellipog.stature.neoforge;

import dev.ellipog.armature.api.net.ArmatureNetwork;
import dev.ellipog.armature.api.net.NetworkBackend;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Armature's networking, on NeoForge.
 *
 * <p>NeoForge will not accept a payload registration during mod construction; it wants them inside
 * {@link RegisterPayloadHandlersEvent}, which fires later. So {@link #register} records and
 * {@link #onRegisterPayloads} performs, which is exactly the difference Armature's backend exists
 * to hide.
 *
 * <p>The event fires on both sides, so one registration serves both directions and client
 * handlers are registered here rather than in a second client-only class. A handler is scheduled
 * on its side's main thread with {@code enqueueWork}.
 */
public final class NeoForgeNetworkBackend implements NetworkBackend {

    private static final List<ArmatureNetwork.Registration<?>> QUEUED = new ArrayList<>();

    @Override
    public <T extends CustomPacketPayload> void register(ArmatureNetwork.Registration<T> registration) {
        QUEUED.add(registration);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToTracking(Entity entity, CustomPacketPayload payload) {
        if (entity.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        }
    }

    @Override
    public void sendNear(ServerLevel level, Vec3 at, double radius, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, radius, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // Delegated: ClientPacketDistributor is client-only and this class is loaded on both sides.
        NeoForgeClientNetworking.sendToServer(payload);
    }

    /** Registers everything queued. Called from the mod bus during startup, on both sides. */
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (ArmatureNetwork.Registration<?> registration : QUEUED) {
            registerOne(registrar, registration);
        }
    }

    private static <T extends CustomPacketPayload> void registerOne(
            PayloadRegistrar registrar, ArmatureNetwork.Registration<T> registration) {
        switch (registration.direction()) {
            case TO_CLIENT -> registrar.playToClient(registration.type(), registration.codec(),
                    (payload, context) -> context.enqueueWork(
                            () -> registration.onClient().accept(payload)));

            case TO_SERVER -> registrar.playToServer(registration.type(), registration.codec(),
                    (payload, context) -> {
                        if (context.player() instanceof ServerPlayer sender) {
                            context.enqueueWork(
                                    () -> registration.onServer().accept(payload, sender));
                        }
                    });

            case BIDIRECTIONAL -> registrar.playBidirectional(
                    registration.type(), registration.codec(),
                    (payload, context) -> {
                        if (context.player() instanceof ServerPlayer sender) {
                            context.enqueueWork(
                                    () -> registration.onServer().accept(payload, sender));
                        }
                        else {
                            context.enqueueWork(
                                    () -> registration.onClient().accept(payload));
                        }
                    });
        }
    }
}
