package dev.ellipog.stature.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ellipog.stature.api.ScaleState;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.junit.jupiter.api.Test;

/**
 * The one part of the sync path a headless run can execute: the bytes.
 *
 * <p>The packet is written on a dedicated server and read on a client, and neither side of that
 * pairing exists in a test, but the codec between them is pure arithmetic on a buffer. These tests
 * pin it down: what goes in comes back out, and what comes in corrupt comes out clamped rather than
 * thrown, because the receiver runs on the network thread where an exception is a disconnect.
 */
class AxesPayloadTest {

    private static AxesPayload roundTrip(AxesPayload payload) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        AxesPayload.STREAM_CODEC.encode(buffer, payload);
        return AxesPayload.STREAM_CODEC.decode(buffer);
    }

    @Test
    void valuesSurviveTheWire() {
        AxesPayload decoded = roundTrip(new AxesPayload(7, 2.0F, 0.5F, 1.2F, 2.0F, 0.8F));
        assertEquals(7, decoded.entityId());
        assertEquals(2.0F, decoded.scale());
        assertEquals(0.5F, decoded.shapeWidth());
        assertEquals(1.2F, decoded.shapeHeight());
        assertEquals(2.0F, decoded.shapeDepth());
        assertEquals(0.8F, decoded.eyeHeight());
    }

    @Test
    void absentEyeSurvivesTheWire() {
        AxesPayload decoded = roundTrip(new AxesPayload(7, 1.0F, 1.0F, 1.0F, 1.0F, AxesPayload.NO_EYE));
        assertEquals(AxesPayload.NO_EYE, decoded.eyeHeight());
    }

    @Test
    void corruptValuesAreClampedRatherThanThrown() {
        ScaleState state = roundTrip(new AxesPayload(9, 99.0F, -2.0F, Float.NaN, 1.0F, 0.8F)).state();
        assertEquals(16.0F, state.scale());
        assertEquals(0.0625F, state.axes().width());
        assertEquals(1.0F, state.axes().height());
        assertEquals(1.0F, state.axes().depth());
    }
}
