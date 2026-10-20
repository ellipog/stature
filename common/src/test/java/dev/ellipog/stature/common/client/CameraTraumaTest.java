package dev.ellipog.stature.common.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraTraumaTest {

    @Test
    void theRadiusIsCappedAtFortyEight() {
        assertEquals(48.0, CameraTrauma.range(16.0F), 1e-9);
        assertEquals(40.0, CameraTrauma.range(2.5F), 1e-9);
    }

    @Test
    void falloffIsSmoothAndDecreasesWithDistance() {
        float near = CameraTrauma.strength(0.0, 16.0F, 1.0F);
        float mid = CameraTrauma.strength(24.0, 16.0F, 1.0F);
        float edge = CameraTrauma.strength(47.9, 16.0F, 1.0F);
        assertTrue(near > mid && mid > edge,
                "strength is not monotonic: " + near + ", " + mid + ", " + edge);
        assertEquals(0.5F, mid, 1e-5F);
    }

    @Test
    void theScreenEffectSliderMultipliesTheShake() {
        assertEquals(0.0F, CameraTrauma.strength(0.0, 16.0F, 0.0F), 1e-6F);
        assertEquals(1.0F, CameraTrauma.strength(0.0, 16.0F, 0.5F), 1e-6F);
    }

    @Test
    void nothingIsFeltOutsideTheRadius() {
        assertEquals(0.0F, CameraTrauma.strength(48.1, 16.0F, 1.0F), 1e-6F);
        assertEquals(0.0F, CameraTrauma.strength(1.0, 0.0F, 1.0F), 1e-6F);
    }
}
