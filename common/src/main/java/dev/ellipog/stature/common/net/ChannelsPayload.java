package dev.ellipog.stature.common.net;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.ellipog.stature.api.Stature;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One entity's channel overrides, on the wire: its id plus the map of what is set.
 *
 * <p>Only what is set travels -- an empty map is never sent, because there is nothing in it a
 * client does not already know (an unset channel follows the base scale, which the client has).
 * The receiver replaces its whole map rather than merging: a removal on the server must read as a
 * removal on the client, and a merge would keep the dead key forever.
 *
 * <p>Decoded entry counts are capped well above anything legitimate: a count is attacker-controlled
 * bytes, and an unbounded loop over it turns a corrupt packet into an allocation failure instead
 * of an ignored one.
 */
public record ChannelsPayload(int entityId, Map<String, Float> channels) implements CustomPacketPayload {

    /** More entries than any sane mod declares; above this the packet is corrupt, not large. */
    static final int MAX_ENTRIES = 64;

    public static final CustomPacketPayload.Type<ChannelsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Stature.MOD_ID, "channels"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChannelsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeVarInt(payload.channels.size());
                for (Map.Entry<String, Float> entry : payload.channels.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeFloat(entry.getValue());
                }
            },
            buffer -> {
                int entityId = buffer.readVarInt();
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) {
                    throw new IllegalArgumentException(
                            "channels packet claims " + count + " entries, at most " + MAX_ENTRIES);
                }
                Map<String, Float> channels = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    channels.put(buffer.readUtf(), buffer.readFloat());
                }
                return new ChannelsPayload(entityId, Map.copyOf(channels));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
