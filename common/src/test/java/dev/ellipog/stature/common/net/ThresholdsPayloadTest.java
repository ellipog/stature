package dev.ellipog.stature.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.junit.jupiter.api.Test;

class ThresholdsPayloadTest {

    private static ThresholdsPayload roundTrip(ThresholdsPayload payload) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ThresholdsPayload.STREAM_CODEC.encode(buffer, payload);
        return ThresholdsPayload.STREAM_CODEC.decode(buffer);
    }

    @Test
    void valuesSurviveTheWire() {
        ThresholdsPayload decoded = roundTrip(new ThresholdsPayload(7, 0.5F, 2.5F,
                Map.of("porous_traversal", true, "surface_climbing", false)));
        assertEquals(7, decoded.entityId());
        assertEquals(0.5F, decoded.micro());
        assertEquals(2.5F, decoded.macro());
        assertEquals(Map.of("porous_traversal", true, "surface_climbing", false), decoded.flags());
    }

    @Test
    void anEmptyFlagMapSurvivesTheWire() {
        ThresholdsPayload decoded = roundTrip(new ThresholdsPayload(7, 0.25F, 2.5F, Map.of()));
        assertTrue(decoded.flags().isEmpty());
    }

    @Test
    void anAbsurdCountThrowsRatherThanAllocating() {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeVarInt(7);
        buffer.writeFloat(0.25F);
        buffer.writeFloat(2.5F);
        buffer.writeVarInt(ThresholdsPayload.MAX_ENTRIES + 1);
        assertThrows(IllegalArgumentException.class, () -> ThresholdsPayload.STREAM_CODEC.decode(buffer));
    }
}
