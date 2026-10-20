package dev.ellipog.stature.fabric;

import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.command.StatureCommands;
import dev.ellipog.stature.common.command.StatureEntitySelectors;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.StatureDataReload;
import dev.ellipog.stature.common.frame.StatureFrames;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.parts.RigRegistry;
import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.net.ConfigPayload;
import dev.ellipog.stature.common.net.FootstepPayload;
import dev.ellipog.stature.common.net.HandshakePayload;
import dev.ellipog.stature.common.net.PartsPayload;
import dev.ellipog.stature.common.net.ThresholdsPayload;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * Joins asked but not yet answered, by deadline tick. Receivers may run off the server
     * thread, so this is concurrent; iteration always runs over a copy.
     */
    private static final Map<UUID, Integer> pendingJoins = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        // 26.3 renamed the play registry: it is clientboundPlay()/serverboundPlay() now, not the
        // playS2C()/playC2S() pair that the older Fabric API used.
        PayloadTypeRegistry.clientboundPlay().register(AxesPayload.TYPE, AxesPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChannelsPayload.TYPE, ChannelsPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PartsPayload.TYPE, PartsPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ThresholdsPayload.TYPE, ThresholdsPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigPayload.TYPE, ConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FootstepPayload.TYPE, FootstepPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.TYPE, HandshakePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HandshakePayload.TYPE, HandshakePayload.STREAM_CODEC);
        StatureNetworking.install(new FabricStatureNetwork());

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
                StatureNetworking.sendTo(player, living, ScaleService.stateOf(living));
                if (ChannelService.hasSyncedState(living)) {
                    StatureNetworking.sendTo(player, living, ChannelService.syncMap(living));
                }
                if (PartService.hasParts(living)
                        || ((StatureScale) living).stature$firstPersonVanilla()) {
                    StatureNetworking.sendPartsTo(player, living, PartService.syncMap(living));
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
            StatureConfig.load(FabricLoader.getInstance().getConfigDir());
        });

        // Movement gating must agree on both sides, so each login carries the tiers and flags.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                StatureNetworking.sendConfigTo(handler.getPlayer(),
                        StatureConfig.microThreshold(), StatureConfig.macroThreshold(),
                        StatureConfig.flagSnapshot(), StatureConfig.tuningSnapshot()));

        // The §16 handshake. Each join is asked; the answer removes it from the pending
        // map, and the tick check below refuses whatever never answered. Vanilla peers
        // ignore the question, which is exactly what gives them away. Everything here
        // fails open: a broken check must let a modded client in, never keep one out.
        ServerPlayNetworking.registerGlobalReceiver(HandshakePayload.TYPE,
                (payload, context) -> pendingJoins.remove(context.player().getUUID()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                ServerPlayNetworking.send(handler.getPlayer(), new HandshakePayload());
                pendingJoins.put(handler.getPlayer().getUUID(), server.getTickCount() + 200);
            } catch (RuntimeException e) {
                LOG.warn("handshake question could not be sent; allowing the join", e);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                pendingJoins.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (pendingJoins.isEmpty()) {
                return;
            }
            int now = server.getTickCount();
            for (var entry : Map.copyOf(pendingJoins).entrySet()) {
                if (now < entry.getValue()) {
                    continue;
                }
                pendingJoins.remove(entry.getKey());
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    try {
                        player.connection.disconnect(Component.translatable("stature.handshake.required"));
                    } catch (RuntimeException e) {
                        LOG.warn("handshake refusal could not be delivered", e);
                    }
                }
            }
        });

        LOG.info("Stature loaded");
    }
}
