package dev.ellipog.stature.common.net;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleLimits;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.api.Stature;
import dev.ellipog.stature.common.StatureScale;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One entity's size, on the wire: its id, its scale, the three axis multipliers, and the frame's
 * eye height.
 *
 * <p>The uniform scale is already one of the game's own synced attributes, so strictly this packet
 * only has to carry the shape. It carries the scale too because the two are one value on the server
 * and a receiver that has to reconcile them from two sources can end up one tick apart from the
 * sender, which is a disagreement about where the hitbox is -- the thing plan §16 refuses. The eye
 * rides along for the same reason in the other direction: the camera and the nameplates read it on
 * the client, and a client left on the vanilla-derived value looks from the wrong height.
 *
 * <p>Written against vanilla's {@link CustomPacketPayload} rather than either loader's copy of it,
 * because both loaders register vanilla's type in 26.x. That is the whole reason a single payload
 * class can live in {@code stature-common}.
 */
public record AxesPayload(
        int entityId, float scale, float shapeWidth, float shapeHeight, float shapeDepth, float eyeHeight)
        implements CustomPacketPayload {

    /** No eye-height override on the wire, matching {@link StatureScale#NO_EYE_HEIGHT}. */
    public static final float NO_EYE = -1.0F;

    public static final CustomPacketPayload.Type<AxesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Stature.MOD_ID, "size"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AxesPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeFloat(payload.scale);
                buffer.writeFloat(payload.shapeWidth);
                buffer.writeFloat(payload.shapeHeight);
                buffer.writeFloat(payload.shapeDepth);
                buffer.writeFloat(payload.eyeHeight);
            },
            buffer -> new AxesPayload(
                    buffer.readVarInt(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * The state this packet claims, pulled into the envelope.
     *
     * <p>Clamped rather than trusted: a packet is bytes from a peer, and a value outside the
     * envelope would otherwise throw from a record constructor inside the network thread, which
     * turns a corrupt packet into a crash instead of an ignored one.
     */
    public ScaleState state() {
        return new ScaleState(
                ScaleLimits.clamp(this.scale),
                new ScaleAxes(
                        ScaleLimits.clamp(this.shapeWidth),
                        ScaleLimits.clamp(this.shapeHeight),
                        ScaleLimits.clamp(this.shapeDepth)));
    }
}
