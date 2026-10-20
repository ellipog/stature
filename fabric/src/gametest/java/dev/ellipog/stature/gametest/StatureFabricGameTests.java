package dev.ellipog.stature.gametest;

import dev.ellipog.stature.common.gametest.StatureGameTests;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric's half of the shared gametest bodies.
 *
 * <p>The {@code fabric-gametest} entrypoint in this source set's
 * {@code fabric.mod.json} names this class, and gametest-api-v1 scans it for
 * {@code @GameTest} methods -- the entrypoint is required, and so is a
 * public no-argument constructor because the loader instantiates the class
 * before reflecting over it. Each method delegates straight to
 * {@link StatureGameTests}, which is where the bodies live so NeoForge runs
 * the same code. Nothing gameplay-related belongs here: this class is
 * registration, and the bodies are the test.
 *
 * <p>The empty structure plus padding keeps every test in open air: the
 * 16x zombie in the defaults test needs room no shipped structure provides
 * yet (see the arena note in the NeoForge twin).
 */
public final class StatureFabricGameTests {

    /** Instantiated by the loader to scan the {@code @GameTest} methods. */
    public StatureFabricGameTests() {
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 5)
    public void partsOnlySurviveSave(GameTestHelper helper) {
        StatureGameTests.partsOnlySurviveSave(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 5)
    public void scaleDefaultsLeavePhysicsVanilla(GameTestHelper helper) {
        StatureGameTests.scaleDefaultsLeavePhysicsVanilla(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 5)
    public void eyeInsideBoxForEveryPose(GameTestHelper helper) {
        StatureGameTests.eyeInsideBoxForEveryPose(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 5)
    public void framedThresholdsResolvedPerEntity(GameTestHelper helper) {
        StatureGameTests.framedThresholdsResolvedPerEntity(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 5)
    public void uniformScaleSurvivesSave(GameTestHelper helper) {
        StatureGameTests.uniformScaleSurvivesSave(helper);
    }
}
