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

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void uniformScaleSurvivesSave(GameTestHelper helper) {
        StatureGameTests.uniformScaleSurvivesSave(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void trampleRespectsMobGriefing(GameTestHelper helper) {
        StatureGameTests.trampleRespectsMobGriefing(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void trampleCappedPerScan(GameTestHelper helper) {
        StatureGameTests.trampleCappedPerScan(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void growInTunnelEndsInAir(GameTestHelper helper) {
        StatureGameTests.growInTunnelEndsInAir(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void dangerousModelResetOnPlainSet(GameTestHelper helper) {
        StatureGameTests.dangerousModelResetOnPlainSet(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void resetClearsEverything(GameTestHelper helper) {
        StatureGameTests.resetClearsEverything(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void foreignScaleModifierNoDrift(GameTestHelper helper) {
        StatureGameTests.foreignScaleModifierNoDrift(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void apiGrowsZombieAndFiresScaleChanged(GameTestHelper helper) {
        StatureGameTests.apiGrowsZombieAndFiresScaleChanged(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 5)
    public void projectileScaleInheritsShooter(GameTestHelper helper) {
        StatureGameTests.projectileScaleInheritsShooter(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 5)
    public void projectileScaleDisabledIsVanilla(GameTestHelper helper) {
        StatureGameTests.projectileScaleDisabledIsVanilla(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 8)
    public void pickupRangeScales(GameTestHelper helper) {
        StatureGameTests.pickupRangeScales(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 8)
    public void pickupRangeDisabledIsVanilla(GameTestHelper helper) {
        StatureGameTests.pickupRangeDisabledIsVanilla(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 8)
    public void scaleSelectorOptionFiltersByScale(GameTestHelper helper) {
        StatureGameTests.scaleSelectorOptionFiltersByScale(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 8)
    public void ridingOffsetFollowsScaleAndDisables(GameTestHelper helper) {
        StatureGameTests.ridingOffsetFollowsScaleAndDisables(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void explosionScaleReachesFurther(GameTestHelper helper) {
        StatureGameTests.explosionScaleReachesFurther(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void explosionScaleDisabledIsVanilla(GameTestHelper helper) {
        StatureGameTests.explosionScaleDisabledIsVanilla(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 8)
    public void dropsScaleMultipliesLootCounts(GameTestHelper helper) {
        StatureGameTests.dropsScaleMultipliesLootCounts(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 8)
    public void dropsScaleDisabledIsVanilla(GameTestHelper helper) {
        StatureGameTests.dropsScaleDisabledIsVanilla(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void nearOneWithFrameTierStillRuns(GameTestHelper helper) {
        StatureGameTests.nearOneWithFrameTierStillRuns(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 8)
    public void frameCacheAndChannelsStayConsistent(GameTestHelper helper) {
        StatureGameTests.frameCacheAndChannelsStayConsistent(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void builtInPresetsApply(GameTestHelper helper) {
        StatureGameTests.builtInPresetsApply(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void configPresetsAddAndOverride(GameTestHelper helper) {
        StatureGameTests.configPresetsAddAndOverride(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void disabledPresetsAreUnavailable(GameTestHelper helper) {
        StatureGameTests.disabledPresetsAreUnavailable(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void selfShortcutsUseTheCommandEntity(GameTestHelper helper) {
        StatureGameTests.selfShortcutsUseTheCommandEntity(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void growAndShrinkClampToTheNormalEnvelope(GameTestHelper helper) {
        StatureGameTests.growAndShrinkClampToTheNormalEnvelope(helper);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100, padding = 16)
    public void oldCommandSyntaxStillWorks(GameTestHelper helper) {
        StatureGameTests.oldCommandSyntaxStillWorks(helper);
    }
}
