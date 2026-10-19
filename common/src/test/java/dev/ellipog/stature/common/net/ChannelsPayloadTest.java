package dev.ellipog.stature.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.junit.jupiter.api.Test;

/**
 * The channel map on the wire, held to the same standard as the size packet: what goes in comes
 * back out, and a corrupt count throws at decode time rather than allocating whatever it claims.
 */
class ChannelsPayloadTest {

    private static ChannelsPayload roundTrip(ChannelsPayload payload) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ChannelsPayload.STREAM_CODEC.encode(buffer, payload);
        return ChannelsPayload.STREAM_CODEC.decode(buffer);
    }

    @Test
    void entriesSurviveTheWire() {
        ChannelsPayload decoded =
                roundTrip(new ChannelsPayload(11, Map.of("stature:motion", 2.0F, "pack:wing_span", 0.5F)));
        assertEquals(11, decoded.entityId());
        assertEquals(Map.of("stature:motion", 2.0F, "pack:wing_span", 0.5F), decoded.channels());
    }

    @Test
    void anEmptyMapSurvivesTheWire() {
        ChannelsPayload decoded = roundTrip(new ChannelsPayload(11, Map.of()));
        assertTrue(decoded.channels().isEmpty());
    }

    @Test
    void anAbsurdCountThrowsRatherThanAllocating() {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeVarInt(11);
        buffer.writeVarInt(ChannelsPayload.MAX_ENTRIES + 1);
        assertThrows(IllegalArgumentException.class, () -> ChannelsPayload.STREAM_CODEC.decode(buffer));
    }
}
