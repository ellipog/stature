package dev.ellipog.stature.common.net;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.ellipog.stature.api.Stature;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One entity's resolved threshold tiers and flags, on the wire: its id, the micro
 * and macro edges, and the per-mechanic flags.
 *
 * <p>Frames are server datapack data, so the client can never resolve overrides
 * itself -- and player movement is client-authoritative, so gates like climbing
 * and porous traversal must agree on both sides. The server resolves the entity's
 * frame overrides over the configured values and sends the answer wherever the
 * size goes: on change, on start-tracking, and to the player themself. The
 * receiver stores it on the duck, where {@code StatureConfig} prefers it over the
 * file values.
 *
 * <p>Decoded entry counts are capped like the config packet: a count is
 * attacker-controlled bytes, and an unbounded loop over it turns a corrupt packet
 * into an allocation failure instead of an ignored one.
 */
public record ThresholdsPayload(int entityId, float micro, float macro, Map<String, Boolean> flags)
        implements CustomPacketPayload {

    static final int MAX_ENTRIES = 16;

    public static final CustomPacketPayload.Type<ThresholdsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Stature.MOD_ID, "thresholds"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ThresholdsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeFloat(payload.micro);
                buffer.writeFloat(payload.macro);
                buffer.writeVarInt(payload.flags.size());
                for (Map.Entry<String, Boolean> entry : payload.flags.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeBoolean(entry.getValue());
                }
            },
            buffer -> {
                int entityId = buffer.readVarInt();
                float micro = buffer.readFloat();
                float macro = buffer.readFloat();
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) {
                    throw new IllegalArgumentException(
                            "thresholds packet claims " + count + " entries, at most " + MAX_ENTRIES);
                }
                Map<String, Boolean> flags = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    flags.put(buffer.readUtf(), buffer.readBoolean());
                }
                return new ThresholdsPayload(entityId, micro, macro, Map.copyOf(flags));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
