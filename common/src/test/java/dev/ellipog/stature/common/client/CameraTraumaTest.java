package dev.ellipog.stature.common.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;

import dev.ellipog.stature.common.StatureConfig;

import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CameraTraumaTest {

    @BeforeEach
    void defaults() {
        StatureConfig.apply(JsonParser.parseString("""
                {
                  "camera_shake_strength": 1.0,
                  "camera_shake_self_strength": 0.35
                }
                """).getAsJsonObject(), "test");
        CameraTrauma.reset();
    }

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

    @Test
    void theScaleFactorStopsGrowingAtEightTimes() {
        assertEquals(1.6F, CameraTrauma.scaleFactor(4.0F), 1e-6F);
        assertEquals(2.0F, CameraTrauma.scaleFactor(8.0F), 1e-6F);
        assertEquals(2.0F, CameraTrauma.scaleFactor(16.0F), 1e-6F);
        assertEquals(CameraTrauma.strength(12.0, 8.0F, 1.0F),
                CameraTrauma.strength(12.0, 16.0F, 1.0F), 1e-6F);
    }

    @Test
    void ownStepsAreMuchGentlerThanNearbyOnes() {
        float own = CameraTrauma.selfStrength(4.0F, 1.0F);
        float nearby = CameraTrauma.strength(0.0, 4.0F, 1.0F);
        assertTrue(own < nearby * 0.15F, "own step " + own + " vs nearby " + nearby);
        assertTrue(CameraTrauma.selfCap(1.0F, 0.35F) < CameraTrauma.otherCap(1.0F));
    }

    @Test
    void ownStepsRampToTheGiantPresetAndHold() {
        assertEquals(0.09F, CameraTrauma.selfStrength(2.0F, 1.0F), 1e-6F);
        assertEquals(0.18F, CameraTrauma.selfStrength(4.0F, 1.0F), 1e-6F);
        assertEquals(0.18F, CameraTrauma.selfStrength(16.0F, 1.0F), 1e-6F);
    }

    @Test
    void theNearbyCapHoldsHoweverManyStepsLand() {
        assertEquals(0.6F, CameraTrauma.otherCap(1.0F), 1e-6F);
        float trauma = 0.0F;
        for (int i = 0; i < 100; i++) {
            trauma = CameraTrauma.accumulate(
                    trauma, CameraTrauma.otherStep(0.0, 16.0F, 1.0F, 1.0F),
                    CameraTrauma.otherCap(1.0F));
        }
        assertEquals(0.6F, trauma, 1e-6F);
    }

    @Test
    void theSelfCapHoldsHoweverManyStepsLand() {
        assertEquals(0.15F, CameraTrauma.selfCap(1.0F, 0.35F), 1e-6F);
        float trauma = 0.0F;
        for (int i = 0; i < 100; i++) {
            trauma = CameraTrauma.accumulate(
                    trauma, CameraTrauma.selfStep(16.0F, 1.0F, 1.0F, 4.0F),
                    CameraTrauma.selfCap(1.0F, 4.0F));
        }
        assertEquals(0.15F, trauma, 1e-6F);
    }

    @Test
    void theGlobalMultiplierScalesBothChannelsAndTheirCaps() {
        assertEquals(CameraTrauma.otherStep(12.0, 8.0F, 1.0F, 1.0F) * 0.5F,
                CameraTrauma.otherStep(12.0, 8.0F, 1.0F, 0.5F), 1e-6F);
        assertEquals(0.3F, CameraTrauma.otherCap(0.5F), 1e-6F);
        assertEquals(CameraTrauma.selfStep(8.0F, 1.0F, 1.0F, 0.35F) * 0.5F,
                CameraTrauma.selfStep(8.0F, 1.0F, 0.5F, 0.35F), 1e-6F);
    }

    @Test
    void theSelfMultiplierScalesOnlyOwnSteps() {
        assertEquals(CameraTrauma.selfStep(8.0F, 1.0F, 1.0F, 0.35F) * 0.5F,
                CameraTrauma.selfStep(8.0F, 1.0F, 1.0F, 0.175F), 1e-6F);
        assertEquals(0.0F, CameraTrauma.selfStep(8.0F, 1.0F, 1.0F, 0.0F), 1e-6F);
        assertEquals(0.0F, CameraTrauma.selfCap(1.0F, 0.0F), 1e-6F);
    }

    @Test
    void zeroOnTheGlobalSwitchTurnsEverythingOff() {
        assertEquals(0.0F, CameraTrauma.otherCap(0.0F), 1e-6F);
        assertEquals(0.0F, CameraTrauma.selfCap(0.0F, 0.35F), 1e-6F);
        assertEquals(0.0F, CameraTrauma.otherStep(0.0, 16.0F, 1.0F, 0.0F), 1e-6F);
        assertEquals(0.0F, CameraTrauma.selfStep(16.0F, 1.0F, 0.0F, 0.35F), 1e-6F);
        assertEquals(0.0F, CameraTrauma.accumulate(0.5F, 0.5F, 0.0F), 1e-6F);
    }

    @Test
    void badMultipliersAreTreatedAsOff() {
        assertEquals(0.0F, CameraTrauma.otherStep(0.0, 16.0F, 1.0F, Float.NaN), 1e-6F);
        assertEquals(0.0F, CameraTrauma.selfStep(16.0F, 1.0F, 1.0F, -1.0F), 1e-6F);
    }

    @Test
    void anOwnStepDoesNotSaturateTheCamera() {
        CameraTrauma.addShake(0.0, 0.0, 0.0, 16.0F, new Vec3(0.0, 25.92, 0.0), 1.0F, true);
        float own = CameraTrauma.sample();
        assertTrue(own < 0.08F, "one own step added " + own + " trauma");

        CameraTrauma.reset();
        CameraTrauma.addShake(0.0, 0.0, 0.0, 16.0F, new Vec3(0.0, 0.0, 0.0), 1.0F, false);
        float nearby = CameraTrauma.sample();
        assertEquals(0.6F, nearby, 1e-3F);
        assertTrue(own < nearby * 0.25F, "own " + own + " vs nearby " + nearby);
    }

    @Test
    void anOwnStepDoesNotLowerANearbyGiantsShake() {
        CameraTrauma.addShake(0.0, 0.0, 0.0, 16.0F, new Vec3(0.0, 0.0, 0.0), 1.0F, false);
        CameraTrauma.addShake(0.0, 0.0, 0.0, 16.0F, new Vec3(0.0, 25.92, 0.0), 1.0F, true);
        assertEquals(0.6F, CameraTrauma.sample(), 1e-3F);
    }

    @Test
    void aZeroSelfSettingSilencesOwnStepsButNotNearbyOnes() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"camera_shake_self_strength\": 0.0}").getAsJsonObject(), "test");
        CameraTrauma.addShake(0.0, 0.0, 0.0, 16.0F, new Vec3(0.0, 25.92, 0.0), 1.0F, true);
        assertEquals(0.0F, CameraTrauma.sample(), 1e-6F);
        CameraTrauma.addShake(0.0, 0.0, 0.0, 16.0F, new Vec3(0.0, 0.0, 0.0), 1.0F, false);
        assertEquals(0.6F, CameraTrauma.sample(), 1e-3F);
    }

    @Test
    void aZeroGlobalSettingSilencesBothChannels() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"camera_shake_strength\": 0.0}").getAsJsonObject(), "test");
        CameraTrauma.addShake(0.0, 0.0, 0.0, 16.0F, new Vec3(0.0, 25.92, 0.0), 1.0F, true);
        CameraTrauma.addShake(0.0, 0.0, 0.0, 16.0F, new Vec3(0.0, 0.0, 0.0), 1.0F, false);
        assertEquals(0.0F, CameraTrauma.sample(), 1e-6F);
    }
}
