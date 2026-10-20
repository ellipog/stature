package dev.ellipog.stature.fabric;

import dev.ellipog.armature.api.ArmatureApi;
import dev.ellipog.armature.api.net.ArmatureNetwork;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.command.StatureCommands;
import dev.ellipog.stature.common.command.StatureEntitySelectors;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.StatureDataReload;
import dev.ellipog.stature.common.frame.StatureFrames;
import dev.ellipog.stature.common.net.StatureHandshake;
import dev.ellipog.stature.common.net.StaturePackets;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.parts.RigRegistry;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import net.minecraft.server.packs.PackType;

import net.minecraft.world.entity.LivingEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entry point.
 *
 * <p>Three jobs, all the loader's rather than Stature's: hand the command tree to Fabric's
 * dispatcher, register the size packet and the network that sends it, and send a joining viewer the
 * shape of anything they start tracking. Everything the command actually does lives in
 * {@code stature-common}, compiled into this jar and into the NeoForge one, so the two loaders cannot
 * drift apart in behaviour.
 *
 * <p>The tracking event lives in {@code fabric-networking-api-v1} in 26.3, not in the entity-events
 * module it used to be in.
 *
 * <p>Runs on both the client and a dedicated server, so nothing in here may touch a client class; the
 * receiver is {@link StatureFabricClient}'s, a separate entry point precisely for that.
 */
public final class StatureFabric implements ModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    @Override
    public void onInitialize() {
        // Declare before install: installation reads the declarations and registers them, so
        // anything declared afterwards is never seen. The client initialiser repeats both calls
        // because Fabric does not promise which runs first; both are idempotent.
        StaturePackets.declare();
        ArmatureNetwork.install(new FabricNetworkBackend());
        FabricNetworkBackend.registerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // After vanilla's Bootstrap, before the tree that can contain the selector.
            StatureEntitySelectors.register();
            StatureCommands.register(dispatcher);
        });

        // Frames are datapack data, so vanilla /reload must refresh them the same way
        // /stature reload does. The startup load below stays as the fallback: it runs
        // after the listener at startup, and guarantees frames exist either way.
        ResourceLoader.get(PackType.SERVER_DATA)
                .registerReloadListener(StatureDataReload.ID, StatureDataReload.listener());

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof LivingEntity living) {
                StaturePackets.sendTo(player, living, ScaleService.stateOf(living));
                if (ChannelService.hasSyncedState(living)) {
                    StaturePackets.sendTo(player, living, ChannelService.syncMap(living));
                }
                if (PartService.hasParts(living)
                        || ((StatureScale) living).stature$firstPersonVanilla()) {
                    StaturePackets.sendPartsTo(player, living, PartService.syncMap(living));
                }
                if (FrameService.frameOf(living) != null) {
                    StatureConfig.sendThresholds(player, living);
                }
            }
        });

        // Frames are data, so they are read once the server's packs are: a datapack's own frames are
        // not available before that, and /stature reload re-reads them after a pack change.
        // The vanilla /reload listener above runs first; this stays as the fallback and
        // records the server so that listener can re-evaluate entities after later reloads.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StatureDataReload.setServer(server);
            StatureFrames.load(server.getResourceManager());
            RigRegistry.instance().load(server.getResourceManager());
            StatureConfig.load(ArmatureApi.platform().configDir());
        });

        // Movement gating must agree on both sides, so each login carries the tiers and flags.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                StaturePackets.sendConfigTo(handler.getPlayer(),
                        StatureConfig.microThreshold(), StatureConfig.macroThreshold(),
                        StatureConfig.flagSnapshot(), StatureConfig.tuningSnapshot()));

        // The §16 handshake. Each join is asked; the answer removes it from the pending map, and
        // the tick check refuses whatever never answered. Vanilla peers ignore the question,
        // which is exactly what gives them away. Everything here fails open.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                StatureHandshake.ask(handler.getPlayer(), server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                StatureHandshake.forget(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(StatureHandshake::tick);

        LOG.info("Stature loaded");
    }
}
