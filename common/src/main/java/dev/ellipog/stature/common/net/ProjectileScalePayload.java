package dev.ellipog.stature.common.net;

import dev.ellipog.stature.api.Stature;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One projectile's inherited size, on the wire: its entity id and the factor.
 *
 * <p>Living entities do not need this: their uniform scale is the game's own synced attribute and
 * their shape rides {@code AxesPayload}. A projectile's factor is a Stature value derived from its
 * owner, and the client may not have that owner when the spawn packet arrives -- a late join, a
 * deflected shot, or an owner that has since logged out. The server therefore sends the snapshot
 * it actually used, rather than letting the client re-derive a different number.
 *
 * <p>The factor is clamped rather than trusted on receipt. A packet is bytes from a peer, and a
 * non-positive or non-finite factor would otherwise scale a hitbox to nothing or a NaN.
 */
public record ProjectileScalePayload(int entityId, float scale) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProjectileScalePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Stature.MOD_ID, "projectile_scale"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileScalePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.entityId);
                        buffer.writeFloat(payload.scale);
                    },
                    buffer -> new ProjectileScalePayload(buffer.readVarInt(), buffer.readFloat()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * The usable factor: exactly 1.0 when the wire value cannot be a size.
     *
     * <p>One packet per projectile rather than per tick, so an early rejection would leave the
     * client at vanilla size for the rest of the flight. Neutral is the only safe reading of a
     * corrupt value.
     */
    public float factor() {
        return Float.isFinite(this.scale) && this.scale > 0.0F ? this.scale : 1.0F;
    }
}
