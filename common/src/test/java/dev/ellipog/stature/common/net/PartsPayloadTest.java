package dev.ellipog.stature.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import dev.ellipog.stature.api.ScaleAxes;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.junit.jupiter.api.Test;

class PartsPayloadTest {

    private static PartsPayload roundTrip(PartsPayload payload) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        PartsPayload.STREAM_CODEC.encode(buffer, payload);
        return PartsPayload.STREAM_CODEC.decode(buffer);
    }

    @Test
    void factorsSurviveTheWire() {
        PartsPayload decoded = roundTrip(new PartsPayload(5,
                Map.of("head", new ScaleAxes(2.0F, 2.0F, 2.0F), "", ScaleAxes.UNIFORM)));
        assertEquals(5, decoded.entityId());
        assertEquals(
                Map.of("head", new ScaleAxes(2.0F, 2.0F, 2.0F), "", ScaleAxes.UNIFORM),
                decoded.parts());
    }

    @Test
    void anEmptyMapSurvivesTheWire() {
        PartsPayload decoded = roundTrip(new PartsPayload(5, Map.of()));
        assertTrue(decoded.parts().isEmpty());
    }

    @Test
    void anAbsurdCountThrowsRatherThanAllocating() {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeVarInt(5);
        buffer.writeVarInt(PartsPayload.MAX_ENTRIES + 1);
        assertThrows(IllegalArgumentException.class, () -> PartsPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void aCorruptTripleThrowsRatherThanHalfReading() {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeVarInt(5);
        buffer.writeVarInt(1);
        buffer.writeUtf("head");
        buffer.writeFloat(Float.NaN);
        buffer.writeFloat(1.0F);
        buffer.writeFloat(1.0F);
        assertThrows(IllegalArgumentException.class, () -> PartsPayload.STREAM_CODEC.decode(buffer));
    }
}
