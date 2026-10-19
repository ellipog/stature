package dev.ellipog.stature.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChannelSpecTest {

    private static final double DELTA = 0.000001;

    private static final ScaleAxes SHAPE = new ScaleAxes(0.5F, 1.2F, 2.0F);

    @Test
    void nonsenseIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelSpec("  ", ChannelFollow.BASE, 1.0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelSpec("pack:thing", ChannelFollow.BASE, Double.NaN, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelSpec("pack:thing", ChannelFollow.BASE, Double.POSITIVE_INFINITY, false));
    }

    @Test
    void anOverrideAlwaysWins() {
        ChannelSpec motion = new ChannelSpec("stature:motion", ChannelFollow.BASE, 0.5, false);
        assertEquals(3.0, motion.resolve(2.0F, SHAPE, 3.0F).orElseThrow(), DELTA);
    }

    @Test
    void unsetChannelsFollowTheirBasis() {
        assertEquals(2.0,
                new ChannelSpec("stature:reach", ChannelFollow.BASE, 1.0, false)
                        .resolve(2.0F, SHAPE, null).orElseThrow(),
                DELTA);
        assertEquals(Math.sqrt(2.0),
                new ChannelSpec("stature:motion", ChannelFollow.BASE, 0.5, false)
                        .resolve(2.0F, SHAPE, null).orElseThrow(),
                DELTA);
        assertEquals(0.5,
                new ChannelSpec("pack:girth", ChannelFollow.WIDTH, 1.0, true)
                        .resolve(2.0F, SHAPE, null).orElseThrow(),
                DELTA);
        assertEquals(1.2,
                new ChannelSpec("pack:stature", ChannelFollow.HEIGHT, 1.0, true)
                        .resolve(2.0F, SHAPE, null).orElseThrow(),
                DELTA);
    }

    @Test
    void aNegativeExponentShrinksAsTheEntityGrows() {
        assertEquals(1.0 / Math.sqrt(2.0),
                new ChannelSpec("stature:knockback", ChannelFollow.BASE, -0.5, false)
                        .resolve(2.0F, SHAPE, null).orElseThrow(),
                DELTA);
    }

    @Test
    void followingNothingMeansOffNotZero() {
        assertTrue(new ChannelSpec("pack:quiet", ChannelFollow.NONE, 1.0, true)
                .resolve(2.0F, SHAPE, null).isEmpty());
    }
}
