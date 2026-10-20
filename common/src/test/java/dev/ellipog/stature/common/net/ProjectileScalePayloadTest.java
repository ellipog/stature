package dev.ellipog.stature.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.junit.jupiter.api.Test;

/**
 * The projectile factor on the wire. The server sends the snapshot it actually used, so what goes
 * in has to come back exactly; a corrupt float degrades to vanilla rather than to a NaN matrix.
 */
class ProjectileScalePayloadTest {

    private static ProjectileScalePayload roundTrip(ProjectileScalePayload payload) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ProjectileScalePayload.STREAM_CODEC.encode(buffer, payload);
        return ProjectileScalePayload.STREAM_CODEC.decode(buffer);
    }

    @Test
    void theFactorSurvivesTheWire() {
        ProjectileScalePayload decoded = roundTrip(new ProjectileScalePayload(23, 4.0F));
        assertEquals(23, decoded.entityId());
        assertEquals(4.0F, decoded.scale());
        assertEquals(4.0F, decoded.factor());
    }

    @Test
    void vanillaSizeSurvivesTheWire() {
        assertEquals(1.0F, roundTrip(new ProjectileScalePayload(7, 1.0F)).factor());
    }

    @Test
    void corruptFactorsDegradeToNeutral() {
        assertEquals(1.0F, new ProjectileScalePayload(7, Float.NaN).factor());
        assertEquals(1.0F, new ProjectileScalePayload(7, Float.POSITIVE_INFINITY).factor());
        assertEquals(1.0F, new ProjectileScalePayload(7, 0.0F).factor());
        assertEquals(1.0F, new ProjectileScalePayload(7, -2.0F).factor());
    }
}
