package dev.ellipog.stature.common.net;

import dev.ellipog.stature.api.Stature;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The §16 handshake: an empty question one way, an empty answer back.
 *
 * <p>Presence is the proof. The server asks each join; a client carrying the mod answers
 * from its play receiver without thinking. Ten seconds of silence means a peer without
 * Stature, which is refused rather than left to render and collide against wrong sizes.
 * Anything that goes wrong inside the check fails open toward joining, never toward a
 * lockout: the worst mistake here must be a vanilla client getting in, not a modded one
 * kept out.
 */
public record HandshakePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HandshakePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Stature.MOD_ID, "handshake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
            },
            buffer -> new HandshakePayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
