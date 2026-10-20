package dev.ellipog.stature.fabric;

import dev.ellipog.armature.api.net.ArmatureNetwork;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client-only half of Fabric's networking.
 *
 * <p>A separate class so {@link FabricNetworkBackend} and {@link StatureFabric}, both loaded on a
 * dedicated server, never mention {@code ClientPlayNetworking}. Naming a client class in a class
 * the server loads is harmless until it is reached, and then the failure is a
 * {@code NoClassDefFoundError} naming a class nobody wrote.
 */
final class FabricClientNetworking {

    private FabricClientNetworking() {
    }

    static void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    /** Registers the client receivers for every payload that arrives at a client. */
    static void registerClientReceivers() {
        for (ArmatureNetwork.Registration<?> registration : ArmatureNetwork.registrations()) {
            if (registration.direction().reachesClient()) {
                registerClientReceiver(registration);
            }
        }
    }

    private static <T extends CustomPacketPayload> void registerClientReceiver(
            ArmatureNetwork.Registration<T> registration) {
        ClientPlayNetworking.registerGlobalReceiver(registration.type(),
                (payload, context) -> context.client().execute(
                        () -> registration.onClient().accept(payload)));
    }
}
