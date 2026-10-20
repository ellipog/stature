package dev.ellipog.stature.common.net;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.ellipog.stature.api.Stature;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Threshold tiers, flags and numeric tuning, on the wire.
 *
 * <p>Sent to each client on login and to everyone on reload, so movement gating and the
 * channels that have no attribute behind them agree on both sides. Small and capped like
 * the other maps; a corrupt packet throws at decode rather than poisoning movement.
 */
public record ConfigPayload(
        float micro, float macro, Map<String, Boolean> flags, Map<String, Float> tuning)
        implements CustomPacketPayload {

    static final int MAX_ENTRIES = 32;

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
                buffer.writeVarInt(payload.tuning.size());
                for (Map.Entry<String, Float> entry : payload.tuning.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeFloat(entry.getValue());
                }
            },
            buffer -> {
                float micro = buffer.readFloat();
                float macro = buffer.readFloat();
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) {
                    throw new IllegalArgumentException(
                            "config packet claims " + count + " flags, at most " + MAX_ENTRIES);
                }
                Map<String, Boolean> flags = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    flags.put(buffer.readUtf(), buffer.readBoolean());
                }
                int tuningCount = buffer.readVarInt();
                if (tuningCount < 0 || tuningCount > MAX_ENTRIES) {
                    throw new IllegalArgumentException(
                            "config packet claims " + tuningCount + " tuning values, at most " + MAX_ENTRIES);
                }
                Map<String, Float> tuning = new LinkedHashMap<>();
                for (int i = 0; i < tuningCount; i++) {
                    tuning.put(buffer.readUtf(), buffer.readFloat());
                }
                return new ConfigPayload(micro, macro, Map.copyOf(flags), Map.copyOf(tuning));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
