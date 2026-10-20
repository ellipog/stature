package dev.ellipog.stature.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The client-only half of NeoForge's networking.
 *
 * <p>A separate class so {@link NeoForgeNetworkBackend}, loaded on a dedicated server, never
 * mentions {@code ClientPacketDistributor}. On 26.x the client send moved out of
 * {@code PacketDistributor} into this distributor while the server send stayed.
 */
final class NeoForgeClientNetworking {

    private NeoForgeClientNetworking() {
    }

    static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
