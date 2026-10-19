package dev.ellipog.stature.neoforge;

import dev.ellipog.stature.api.Stature;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entry point.
 *
 * <p>Nothing here yet beyond loading: the platform layer, the events and the first real behaviour
 * arrive with the frame work (plan §14.8). Runs on both the client and a dedicated server, so
 * nothing in here may touch a client class.
 */
@Mod(Stature.MOD_ID)
public final class StatureNeoForge {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    public StatureNeoForge(IEventBus modEventBus) {
        LOG.info("Stature initialising on NeoForge");
    }
}
