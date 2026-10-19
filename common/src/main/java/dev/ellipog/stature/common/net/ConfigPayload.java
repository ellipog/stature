package dev.ellipog.stature.common.net;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.ellipog.stature.api.Stature;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Threshold tiers and flags, on the wire: two edges plus the flag map.
 *
 * <p>Sent to each client on login and to everyone on reload, so movement gating agrees on
 * both sides. Small and capped like the other maps; a corrupt packet throws at decode
 * rather than poisoning movement.
 */
public record ConfigPayload(float micro, float macro, Map<String, Boolean> flags) implements CustomPacketPayload {

    static final int MAX_ENTRIES = 16;

    public static final CustomPacketPayload.Type<ConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Stature.MOD_ID, "config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeFloat(payload.micro);
                buffer.writeFloat(payload.macro);
                buffer.writeVarInt(payload.flags.size());
                for (Map.Entry<String, Boolean> entry : payload.flags.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeBoolean(entry.getValue());
                }
            },
            buffer -> {
                float micro = buffer.readFloat();
                float macro = buffer.readFloat();
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) {
                    throw new IllegalArgumentException(
                            "config packet claims " + count + " entries, at most " + MAX_ENTRIES);
                }
                Map<String, Boolean> flags = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    flags.put(buffer.readUtf(), buffer.readBoolean());
                }
                return new ConfigPayload(micro, macro, Map.copyOf(flags));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
