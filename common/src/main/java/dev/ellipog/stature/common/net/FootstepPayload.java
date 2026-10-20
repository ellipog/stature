package dev.ellipog.stature.common.net;

import dev.ellipog.stature.api.Stature;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One macro footstep, on the wire: who stepped, where it landed and at what scale.
 *
 * <p>{@code entityId} lets a client tell its own step from another entity's. That matters because
 * the camera sits above the walker's own feet, so proximity alone would treat every own step as a
 * maximum-strength impact. Clients convert it to camera trauma by distance for everyone else, so
 * this needs no reply and no cap beyond its fixed size. A corrupt packet throws at decode rather
 * than shaking anyone.
 */
public record FootstepPayload(int entityId, double x, double y, double z, float scale)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FootstepPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Stature.MOD_ID, "footstep"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FootstepPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeDouble(payload.x);
                buffer.writeDouble(payload.y);
                buffer.writeDouble(payload.z);
                buffer.writeFloat(payload.scale);
            },
            buffer -> new FootstepPayload(
                    buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
