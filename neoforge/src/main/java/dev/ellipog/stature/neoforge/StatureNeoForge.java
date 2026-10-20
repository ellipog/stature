package dev.ellipog.stature.neoforge;

import dev.ellipog.armature.api.ArmatureApi;
import dev.ellipog.armature.api.net.ArmatureNetwork;
import dev.ellipog.stature.api.Stature;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureProjectile;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.command.StatureCommands;
import dev.ellipog.stature.common.command.StatureEntitySelectors;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.StatureDataReload;
import dev.ellipog.stature.common.frame.StatureFrames;
import dev.ellipog.stature.common.net.StaturePackets;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.parts.RigRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

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
 * <p>The payload declarations are common; Armature's NeoForge backend registers them inside
 * {@link RegisterPayloadHandlersEvent}, on both sides, so one registration serves both directions.
 * A client handler runs only on a client, which is why naming a client class in its lambda is safe
 * on a dedicated server: the reference resolves when the handler runs, not when it is declared.
 */
@Mod(Stature.MOD_ID)
public final class StatureNeoForge {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    public StatureNeoForge(IEventBus modEventBus) {
        // Gametests (plan M4): registers nothing in a normal run. The
        // gameTestServer run sets -Dstature.gametest=true, and only then do
        // the shared bodies become test instances.
        NeoForgeStatureGameTests.register(modEventBus);

        // Declare before install: the backend records declarations and the event registers them.
        StaturePackets.declare();
        ArmatureNetwork.install(new NeoForgeNetworkBackend());
        modEventBus.addListener(RegisterPayloadHandlersEvent.class,
                NeoForgeNetworkBackend::onRegisterPayloads);

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            // After vanilla's Bootstrap, before the tree that can contain the selector.
            StatureEntitySelectors.register();
            StatureCommands.register(event.getDispatcher());
        });

        // Frames are datapack data, so vanilla /reload must refresh them the same way
        // /stature reload does. This is not a mod-bus event, so it listens on the game
        // bus; the startup load below stays as the fallback and guarantees frames
        // exist either way.
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) ->
                event.addListener(StatureDataReload.ID, StatureDataReload.listener()));

        // A viewer who arrives after the shape was set has never been told it.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            if (event.getTarget() instanceof Projectile projectile) {
                if (projectile instanceof StatureProjectile duck && duck.stature$projectileScaled()) {
                    // NeoForge fires this after the spawn packet, so the client already has the
                    // entity and can apply the factor immediately.
                    StaturePackets.sendProjectileScaleTo(
                            player, projectile, duck.stature$projectileScale());
                }
                return;
            }
            if (event.getTarget() instanceof LivingEntity living) {
                StaturePackets.sendTo(player, living, ScaleService.stateOf(living));
                if (ChannelService.hasSyncedState(living)) {
                    StaturePackets.sendTo(player, living, ChannelService.syncMap(living));
                }
                if (PartService.hasParts(living) || ((StatureScale) living).stature$firstPersonVanilla()) {
                    StaturePackets.sendPartsTo(player, living, PartService.syncMap(living));
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
            StatureConfig.load(ArmatureApi.platform().configDir());
        });

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                StaturePackets.sendConfigTo(player,
                        StatureConfig.microThreshold(), StatureConfig.macroThreshold(),
                        StatureConfig.flagSnapshot(), StatureConfig.tuningSnapshot());
            }
        });

        LOG.info("Stature loaded");
    }
}
