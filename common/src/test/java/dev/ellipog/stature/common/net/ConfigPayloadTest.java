package dev.ellipog.stature.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.junit.jupiter.api.Test;

class ConfigPayloadTest {

    private static ConfigPayload roundTrip(ConfigPayload payload) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ConfigPayload.STREAM_CODEC.encode(buffer, payload);
        return ConfigPayload.STREAM_CODEC.decode(buffer);
    }

    @Test
    void valuesSurviveTheWire() {
        ConfigPayload decoded = roundTrip(new ConfigPayload(0.5F, 1.5F, Map.of("trampling", false)));
        assertEquals(0.5F, decoded.micro());
        assertEquals(1.5F, decoded.macro());
        assertEquals(Map.of("trampling", false), decoded.flags());
    }

    @Test
    void anEmptyMapSurvivesTheWire() {
        assertTrue(roundTrip(new ConfigPayload(0.25F, 2.5F, Map.of())).flags().isEmpty());
    }

    @Test
    void anAbsurdCountThrowsRatherThanAllocating() {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeFloat(0.25F);
        buffer.writeFloat(2.5F);
        buffer.writeVarInt(ConfigPayload.MAX_ENTRIES + 1);
        assertThrows(IllegalArgumentException.class, () -> ConfigPayload.STREAM_CODEC.decode(buffer));
    }
}
