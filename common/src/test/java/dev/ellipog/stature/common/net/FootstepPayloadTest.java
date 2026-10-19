package dev.ellipog.stature.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.junit.jupiter.api.Test;

class FootstepPayloadTest {

    @Test
    void valuesSurviveTheWire() {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        FootstepPayload.STREAM_CODEC.encode(buffer, new FootstepPayload(8.5, 100.0, -3.25, 3.0F));
        FootstepPayload decoded = FootstepPayload.STREAM_CODEC.decode(buffer);
        assertEquals(8.5, decoded.x());
        assertEquals(100.0, decoded.y());
        assertEquals(-3.25, decoded.z());
        assertEquals(3.0F, decoded.scale());
    }
}
