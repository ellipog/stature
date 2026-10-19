package dev.ellipog.stature.fabric;

import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.command.StatureCommands;
import dev.ellipog.stature.common.frame.StatureFrames;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.parts.RigRegistry;
import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.net.ConfigPayload;
import dev.ellipog.stature.common.net.PartsPayload;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

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
        // 26.3 renamed the play registry: it is clientboundPlay()/serverboundPlay() now, not the
        // playS2C()/playC2S() pair that the older Fabric API used.
        PayloadTypeRegistry.clientboundPlay().register(AxesPayload.TYPE, AxesPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChannelsPayload.TYPE, ChannelsPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PartsPayload.TYPE, PartsPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigPayload.TYPE, ConfigPayload.STREAM_CODEC);
        StatureNetworking.install(new FabricStatureNetwork());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                StatureCommands.register(dispatcher));

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof LivingEntity living) {
                StatureNetworking.sendTo(player, living, ScaleService.stateOf(living));
                if (ChannelService.hasSyncedState(living)) {
                    StatureNetworking.sendTo(player, living, ChannelService.syncMap(living));
                }
                if (PartService.hasParts(living)) {
                    StatureNetworking.sendPartsTo(player, living, PartService.syncMap(living));
                }
            }
        });

        // Frames are data, so they are read once the server's packs are: a datapack's own frames are
        // not available before that, and /stature reload re-reads them after a pack change.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StatureFrames.load(server.getResourceManager());
            RigRegistry.instance().load(server.getResourceManager());
            StatureConfig.load(FabricLoader.getInstance().getConfigDir());
        });

        // Movement gating must agree on both sides, so each login carries the tiers and flags.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                StatureNetworking.sendConfigTo(handler.getPlayer(),
                        StatureConfig.microThreshold(), StatureConfig.macroThreshold(), StatureConfig.flagSnapshot()));

        LOG.info("Stature loaded");
    }
}
