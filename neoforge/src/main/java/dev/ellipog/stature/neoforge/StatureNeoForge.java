package dev.ellipog.stature.neoforge;

import dev.ellipog.stature.api.Stature;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.command.StatureCommands;
import dev.ellipog.stature.common.frame.StatureFrames;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.parts.RigRegistry;
import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.net.PartsPayload;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entry point.
 *
 * <p>The command hook is on the <b>game</b> bus and the payload registration is on the <b>mod</b> bus
 * the constructor receives: NeoForge splits them, and putting either on the wrong one produces a
 * silent no-op rather than an error.
 *
 * <p>The packet handler is deliberately written without a single client class. It asks the context
 * for the player, then for that player's level and the entity by id, which is the same code on both
 * sides -- so the class can stay in the common payload rather than needing a client-only twin, and a
 * dedicated server never verifies a class it cannot load.
 */
@Mod(Stature.MOD_ID)
public final class StatureNeoForge {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    public StatureNeoForge(IEventBus modEventBus) {
        StatureNetworking.install(new NeoForgeStatureNetwork());

        modEventBus.addListener((RegisterPayloadHandlersEvent event) ->
                event.registrar("1").playToClient(AxesPayload.TYPE, AxesPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> {
                            Entity entity = context.player().level().getEntity(payload.entityId());
                            if (entity instanceof LivingEntity living) {
                                ScaleService.applyFromServer(living, payload.state(), payload.eyeHeight());
                            }
                        })).playToClient(ChannelsPayload.TYPE, ChannelsPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> {
                            Entity entity = context.player().level().getEntity(payload.entityId());
                            if (entity instanceof LivingEntity living) {
                                StatureScale duck = (StatureScale) living;
                                duck.stature$channelOverrides().clear();
                                duck.stature$channelOverrides().putAll(payload.channels());
                            }
                        })).playToClient(PartsPayload.TYPE, PartsPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> {
                            Entity entity = context.player().level().getEntity(payload.entityId());
                            if (entity instanceof LivingEntity living) {
                                StatureScale duck = (StatureScale) living;
                                duck.stature$partFactors().clear();
                                duck.stature$partFactors().putAll(payload.parts());
                            }
                        })));

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                StatureCommands.register(event.getDispatcher()));

        // A viewer who arrives after the shape was set has never been told it.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity living) {
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
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            StatureFrames.load(event.getServer().getResourceManager());
            RigRegistry.instance().load(event.getServer().getResourceManager());
        });

        LOG.info("Stature loaded");
    }
}
