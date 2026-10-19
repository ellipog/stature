package dev.ellipog.stature.fabric;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entry point.
 *
 * <p>Nothing here yet beyond loading: the platform layer, the events and the first real behaviour
 * arrive with the frame work (plan §14.8). Runs on both the client and a dedicated server, so
 * nothing in here may touch a client class; when a client entry point earns its place it is a
 * separate one, precisely so the server never loads it.
 */
public final class StatureFabric implements ModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    @Override
    public void onInitialize() {
        LOG.info("Stature initialising on Fabric");
    }
}
