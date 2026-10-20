package dev.ellipog.stature.common.frame;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import dev.ellipog.stature.api.Stature;
import dev.ellipog.stature.common.StatureConfig;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * Answers vanilla {@code /reload} with the same refresh {@code /stature reload} does.
 *
 * <p>Frames are datapack JSON, so a pack edit followed by {@code /reload} must move
 * automatically assigned entities without anyone re-running the Stature command. Both
 * loaders register this one listener: Fabric through the resource-loader entrypoint,
 * NeoForge through {@code AddServerReloadListenersEvent} on the game bus. The
 * registry swap runs synchronously in {@code reload}, exactly like the command does
 * today; the entity re-evaluation waits for the barrier and then runs on the game
 * thread, because it touches the levels.
 *
 * <p>At startup the listener runs before the entrypoints record the server, so the
 * apply phase finds no server and skips the refresh: correct, because no level has
 * loaded yet and there is nothing to re-evaluate. The entrypoints keep their own
 * startup load as the fallback, so frames exist either way.
 */
public final class StatureDataReload implements PreparableReloadListener {

    /** Where this listener sits in the reload order, and its log name. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Stature.MOD_ID, "frames");

    private static final StatureDataReload INSTANCE = new StatureDataReload();

    /** The running server, recorded by each loader's startup handler. */
    private static volatile MinecraftServer server;

    private StatureDataReload() {
    }

    public static PreparableReloadListener listener() {
        return INSTANCE;
    }

    /** Records the running server so the apply phase can re-evaluate entities. */
    public static void setServer(MinecraftServer current) {
        server = current;
    }

    @Override
    public CompletableFuture<Void> reload(
            SharedState sharedState,
            Executor backgroundExecutor,
            PreparationBarrier barrier,
            Executor gameExecutor) {
        FrameService.reloadFrameData(sharedState.resourceManager());
        return barrier.<Void>wait(null).thenRunAsync(() -> {
            MinecraftServer current = server;
            if (current != null) {
                current.execute(() -> {
                    FrameService.refreshAutomatic(current);
                    StatureConfig.refreshAllThresholds(current);
                });
            }
        }, gameExecutor);
    }
}
