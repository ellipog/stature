package dev.ellipog.stature.neoforge;

import java.util.List;
import java.util.function.Consumer;

import dev.ellipog.stature.api.Stature;
import dev.ellipog.stature.common.gametest.StatureGameTests;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge's half of the shared gametest bodies (improvement plan M4).
 *
 * <p>The same bodies Fabric runs, registered as
 * {@code FunctionGameTestInstance}s on the mod bus's
 * {@link RegisterGameTestsEvent}. Registration is gated behind
 * {@code -Dstature.gametest=true}, which only the {@code gameTestServer} run
 * sets: the shipped jar carries the bodies (a few KB, the plan's Q13
 * recommendation) but never registers them in a player's world.
 *
 * <p>There is no shipped arena structure yet: every test uses
 * {@code minecraft:empty} with padding and builds its own floor in code,
 * which all the current bodies need. The padding is wide enough for the
 * 13x13 leaf field and the tunnel the M5 tests build.
 */
public final class NeoForgeStatureGameTests {

    /** One shared body: its registry name and the method to run. */
    private record TestCase(String name, Consumer<GameTestHelper> body) {
    }

    /** The shared bodies, in registration order. */
    private static final List<TestCase> TESTS = List.of(
            new TestCase("parts_only_survive_save", StatureGameTests::partsOnlySurviveSave),
            new TestCase("scale_defaults_leave_physics_vanilla",
                    StatureGameTests::scaleDefaultsLeavePhysicsVanilla),
            new TestCase("eye_inside_box_for_every_pose", StatureGameTests::eyeInsideBoxForEveryPose),
            new TestCase("framed_thresholds_resolved_per_entity",
                    StatureGameTests::framedThresholdsResolvedPerEntity),
            new TestCase("uniform_scale_survives_save", StatureGameTests::uniformScaleSurvivesSave),
            new TestCase("trample_respects_mob_griefing", StatureGameTests::trampleRespectsMobGriefing),
            new TestCase("trample_capped_per_scan", StatureGameTests::trampleCappedPerScan),
            new TestCase("grow_in_tunnel_ends_in_air", StatureGameTests::growInTunnelEndsInAir),
            new TestCase("dangerous_model_reset_on_plain_set",
                    StatureGameTests::dangerousModelResetOnPlainSet),
            new TestCase("reset_clears_everything", StatureGameTests::resetClearsEverything),
            new TestCase("foreign_scale_modifier_no_drift",
                    StatureGameTests::foreignScaleModifierNoDrift),
            new TestCase("api_grows_zombie_and_fires_scale_changed",
                    StatureGameTests::apiGrowsZombieAndFiresScaleChanged));

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Stature.MOD_ID);

    static {
        for (TestCase test : TESTS) {
            FUNCTIONS.register(test.name(), test::body);
        }
    }

    private NeoForgeStatureGameTests() {
    }

    /**
     * Registers the test functions and instances, unless this is a normal
     * game run. Call once from the mod constructor.
     */
    public static void register(IEventBus modBus) {
        if (!Boolean.getBoolean("stature.gametest")) {
            return;
        }
        FUNCTIONS.register(modBus);
        modBus.addListener((RegisterGameTestsEvent event) -> {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                    Identifier.fromNamespaceAndPath(Stature.MOD_ID, "default"),
                    new TestEnvironmentDefinition.AllOf());
            for (TestCase test : TESTS) {
                Identifier id = Identifier.fromNamespaceAndPath(Stature.MOD_ID, test.name());
                ResourceKey<Consumer<GameTestHelper>> function =
                        ResourceKey.create(Registries.TEST_FUNCTION, id);
                event.registerTest(id, new FunctionGameTestInstance(function, new TestData<>(
                        environment,
                        Level.OVERWORLD,
                        Identifier.fromNamespaceAndPath("minecraft", "empty"),
                        100, 0, true, Rotation.NONE, false, 1, 1, false, 16)));
            }
        });
    }
}
