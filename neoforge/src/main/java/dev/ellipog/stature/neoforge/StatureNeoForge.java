package dev.ellipog.stature.neoforge;

import dev.ellipog.stature.api.Stature;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StaturePlatform;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.command.StatureCommands;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.StatureDataReload;
import dev.ellipog.stature.common.frame.StatureFrames;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.parts.RigRegistry;
import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.net.ConfigPayload;
import dev.ellipog.stature.common.net.FootstepPayload;
import dev.ellipog.stature.common.net.PartsPayload;
import dev.ellipog.stature.common.net.ThresholdsPayload;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
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
 * <p>The payload <b>types and codecs</b> are registered here, on both sides. The client-bound
 * <b>handlers</b> are registered in {@code NeoForgeStatureClientPayloads} through
 * {@code RegisterClientPayloadHandlersEvent}, which only fires on a client: a dedicated server never
 * classloads that class, so a handler cannot pull {@code ClientViews} (and {@code Minecraft}) onto
 * the server through a lambda.
 */
@Mod(Stature.MOD_ID)
public final class StatureNeoForge {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    public StatureNeoForge(IEventBus modEventBus) {
        // Gametests (plan M4): registers nothing in a normal run. The
        // gameTestServer run sets -Dstature.gametest=true, and only then do
        // the shared bodies become test instances.
        NeoForgeStatureGameTests.register(modEventBus);

        StatureNetworking.install(new NeoForgeStatureNetwork());
        StaturePlatform.install(new NeoForgeStaturePlatform());

        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToClient(AxesPayload.TYPE, AxesPayload.STREAM_CODEC)
                .playToClient(ChannelsPayload.TYPE, ChannelsPayload.STREAM_CODEC)
                .playToClient(PartsPayload.TYPE, PartsPayload.STREAM_CODEC)
                .playToClient(ThresholdsPayload.TYPE, ThresholdsPayload.STREAM_CODEC)
                .playToClient(ConfigPayload.TYPE, ConfigPayload.STREAM_CODEC)
                .playToClient(FootstepPayload.TYPE, FootstepPayload.STREAM_CODEC));

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                StatureCommands.register(event.getDispatcher()));

        // Frames are datapack data, so vanilla /reload must refresh them the same way
        // /stature reload does. This is not a mod-bus event, so it listens on the game
        // bus; the startup load below stays as the fallback and guarantees frames
        // exist either way.
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) ->
                event.addListener(StatureDataReload.ID, StatureDataReload.listener()));

        // A viewer who arrives after the shape was set has never been told it.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity living) {
                StatureNetworking.sendTo(player, living, ScaleService.stateOf(living));
                if (ChannelService.hasSyncedState(living)) {
                    StatureNetworking.sendTo(player, living, ChannelService.syncMap(living));
                }
                if (PartService.hasParts(living) || ((StatureScale) living).stature$firstPersonVanilla()) {
                    StatureNetworking.sendPartsTo(player, living, PartService.syncMap(living));
                }
                if (FrameService.frameOf(living) != null) {
                    StatureConfig.sendThresholds(player, living);
                }
            }
        });

        // Frames are data, so they are read once the server's packs are: a datapack's own frames are
        // not available before that, and /stature reload re-reads them after a pack change.
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            StatureDataReload.setServer(event.getServer());
            StatureFrames.load(event.getServer().getResourceManager());
            RigRegistry.instance().load(event.getServer().getResourceManager());
            StatureConfig.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        });

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                StatureNetworking.sendConfigTo(player,
                        StatureConfig.microThreshold(), StatureConfig.macroThreshold(),
                        StatureConfig.flagSnapshot(), StatureConfig.tuningSnapshot());
            }
        });

        LOG.info("Stature loaded");
    }
}
