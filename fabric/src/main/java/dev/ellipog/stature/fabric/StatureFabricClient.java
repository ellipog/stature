package dev.ellipog.stature.fabric;

import dev.ellipog.armature.api.net.ArmatureNetwork;
import dev.ellipog.stature.common.net.StaturePackets;

import net.fabricmc.api.ClientModInitializer;

/**
 * The client receiver, in a client entry point so a dedicated server never loads it.
 *
 * <p>The declarations and the backend are repeated here because Fabric does not promise whether
 * the common initialiser or this one runs first. Both are idempotent: declaring twice is a no-op,
 * and installation keeps the first backend. Without this, a client that ran its client
 * initialiser first would register zero receivers, because the declaration list was still empty,
 * and every sync would vanish with no error anywhere.
 */
public final class StatureFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        StaturePackets.declare();
        ArmatureNetwork.install(new FabricNetworkBackend());
        FabricClientNetworking.registerClientReceivers();
    }
}
