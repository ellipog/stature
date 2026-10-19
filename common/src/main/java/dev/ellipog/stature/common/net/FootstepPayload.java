package dev.ellipog.stature.common.net;

import dev.ellipog.stature.api.Stature;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One macro footstep, on the wire: where it landed and at what scale.
 *
 * <p>Clients convert it to camera trauma by distance, so this needs no reply and no cap
 * beyond its fixed size. A corrupt packet throws at decode rather than shaking anyone.
 */
public record FootstepPayload(double x, double y, double z, float scale) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FootstepPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Stature.MOD_ID, "footstep"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FootstepPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeDouble(payload.x);
                buffer.writeDouble(payload.y);
                buffer.writeDouble(payload.z);
                buffer.writeFloat(payload.scale);
            },
            buffer -> new FootstepPayload(
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
