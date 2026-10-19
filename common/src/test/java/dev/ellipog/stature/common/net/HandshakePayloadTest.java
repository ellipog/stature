package dev.ellipog.stature.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.junit.jupiter.api.Test;

class HandshakePayloadTest {

    @Test
    void emptinessSurvivesTheWire() {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        HandshakePayload.STREAM_CODEC.encode(buffer, new HandshakePayload());
        assertEquals(new HandshakePayload(), HandshakePayload.STREAM_CODEC.decode(buffer));
    }
}
