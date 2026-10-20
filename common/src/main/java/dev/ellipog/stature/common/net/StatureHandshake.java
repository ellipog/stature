package dev.ellipog.stature.common.net;

import dev.ellipog.armature.api.net.ArmatureNetwork;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The play-phase handshake that refuses a client without Stature.
 *
 * <p>NeoForge refuses through the loader's own required-mod metadata before any of this runs.
 * Fabric has no required-mod declaration that can do the same for a client, so each join is asked
 * a question and the answer removes it from the pending map. A vanilla peer ignores the question,
 * which is exactly what gives it away, and the tick check refuses whatever never answered.
 *
 * <p>Everything here fails open: a broken check must let a modded client in, never keep one out.
 * The payload itself is declared in {@link StaturePackets}; this class owns the deadline and the
 * disconnect, neither of which is a payload concern.
 */
public final class StatureHandshake {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    /** Joins asked but not yet answered, by deadline tick. */
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();

    private StatureHandshake() {
    }

    /** Asks one joining player. A send that fails allows the join. */
    public static void ask(ServerPlayer player, MinecraftServer server) {
        try {
            ArmatureNetwork.sendToPlayer(player, new HandshakePayload());
            PENDING.put(player.getUUID(), server.getTickCount() + 200);
        }
        catch (RuntimeException e) {
            LOG.warn("handshake question could not be sent; allowing the join", e);
        }
    }

    /** The answer arrived. */
    public static void answered(UUID player) {
        PENDING.remove(player);
    }

    /** The player left before answering. */
    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    /** Refuses whoever has been silent for the whole ten-second window. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        for (Map.Entry<UUID, Integer> entry : Map.copyOf(PENDING).entrySet()) {
            if (now < entry.getValue()) {
                continue;
            }
            PENDING.remove(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            try {
                player.connection.disconnect(Component.translatable("stature.handshake.required"));
            }
            catch (RuntimeException e) {
                LOG.warn("handshake refusal could not be delivered", e);
            }
        }
    }
}
