package dev.ellipog.stature.common.net;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.Stature;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One entity's part factors, on the wire: its id, path to triple, and whether first-person
 * hands stay vanilla for it.
 *
 * <p>Same rules as the channel map: only what is set travels, the receiver replaces whole, and
 * the entry count is capped against corrupt packets. Paths are already resolved server-side, so
 * the client walks them without knowing any rig.
 */
public record PartsPayload(int entityId, Map<String, ScaleAxes> parts, boolean firstPersonVanilla)
        implements CustomPacketPayload {

    static final int MAX_ENTRIES = 32;

    public static final CustomPacketPayload.Type<PartsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Stature.MOD_ID, "parts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeBoolean(payload.firstPersonVanilla);
                buffer.writeVarInt(payload.parts.size());
                for (Map.Entry<String, ScaleAxes> entry : payload.parts.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeFloat(entry.getValue().width());
                    buffer.writeFloat(entry.getValue().height());
                    buffer.writeFloat(entry.getValue().depth());
                }
            },
            buffer -> {
                int entityId = buffer.readVarInt();
                boolean firstPersonVanilla = buffer.readBoolean();
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) {
                    throw new IllegalArgumentException(
                            "parts packet claims " + count + " entries, at most " + MAX_ENTRIES);
                }
                Map<String, ScaleAxes> parts = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    String path = buffer.readUtf();
                    try {
                        parts.put(path, new ScaleAxes(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
                    } catch (IllegalArgumentException corrupt) {
                        throw new IllegalArgumentException("parts packet carries no size for " + path);
                    }
                }
                return new PartsPayload(entityId, Map.copyOf(parts), firstPersonVanilla);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
