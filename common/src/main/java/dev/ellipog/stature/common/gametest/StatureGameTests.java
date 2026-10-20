package dev.ellipog.stature.common.gametest;

import java.util.Map;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ResolvedThresholds;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.parts.PartService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The in-game tests, shared by both loaders (improvement plan M4).
 *
 * <p>Each method is one test body: it takes the loader-agnostic
 * {@link GameTestHelper}, drives the real services against a real level, and
 * ends in {@code succeed}. The loaders only register them -- Fabric scans for
 * its {@code @GameTest} annotations in the gametest source set, NeoForge
 * registers {@code FunctionGameTestInstance}s behind the
 * {@code stature.gametest} property -- so the bodies cannot drift apart
 * between loaders.
 *
 * <p>They ship in the jar (a few KB, inert unless a loader registers them):
 * that is the plan's Q13 recommendation, chosen over a shared source set
 * because it is less Gradle for the same guarantee.
 *
 * <p>Zombies, not players, throughout: a zombie carries the living attributes
 * this suite checks (including the physics constants and the camera
 * distance), and spawning one never trips the login/sync paths a mock player
 * would. Player-only attributes such as block reach and attack speed stay
 * covered by the channel table's unit tests, not here.
 */
public final class StatureGameTests {

    private StatureGameTests() {
    }

    /**
     * M2-1: an entity shaped only by {@code /stature part} keeps its factors
     * through a save and a load.
     */
    public static void partsOnlySurviveSave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        PartService.setPart(zombie, "head", new ScaleAxes(2.0F, 2.0F, 2.0F), 0, Easing.LINEAR);

        TagValueOutput out =
                TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        zombie.saveWithoutId(out);
        Zombie fresh = new Zombie(EntityTypes.ZOMBIE, level);
        fresh.load(TagValueInput.create(
                ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));

        ScaleAxes head = ((StatureScale) fresh).stature$partFactors().get("head");
        helper.assertTrue(new ScaleAxes(2.0F, 2.0F, 2.0F).equals(head),
                "parts-only entity lost its head factor on save");
        helper.succeed();
    }

    /**
     * M1: with no channel set, both ends of the envelope leave the
     * override-only attributes at their vanilla values, while the
     * body-physics channels move.
     *
     * <p>Both 0.0625x and 16x, because a default that happens to be right at
     * one end can still be an inversion at the other, and the tiny end is the
     * one that used to turn friction into ice.
     */
    public static void scaleDefaultsLeavePhysicsVanilla(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        assertDefaultsHold(helper, zombie, 0.0625F);
        assertDefaultsHold(helper, zombie, 16.0F);
        helper.succeed();
    }

    private static void assertDefaultsHold(GameTestHelper helper, Zombie zombie, float scale) {
        String at = scale + "x";

        double gravity = valueOf(zombie, Attributes.GRAVITY);
        double attack = valueOf(zombie, Attributes.ATTACK_DAMAGE);
        double health = valueOf(zombie, Attributes.MAX_HEALTH);
        double followRange = valueOf(zombie, Attributes.FOLLOW_RANGE);
        double motion = valueOf(zombie, Attributes.MOVEMENT_SPEED);
        double jump = valueOf(zombie, Attributes.JUMP_STRENGTH);
        double step = valueOf(zombie, Attributes.STEP_HEIGHT);
        // Entity reach, not block reach: every living entity carries this one, while
        // BLOCK_INTERACTION_RANGE is player-only (a zombie has no such attribute, and the
        // channel skips a missing attribute rather than inventing one).
        double reach = valueOf(zombie, Attributes.ENTITY_INTERACTION_RANGE);
        // The physics constants are on every living entity in 26.3, but read
        // defensively: a future entity that lacks one should not turn a
        // Stature default into an unrelated crash. The channel skips a missing
        // attribute the same way.
        Double friction = tryValueOf(zombie, Attributes.FRICTION_MODIFIER);
        Double airDrag = tryValueOf(zombie, Attributes.AIR_DRAG_MODIFIER);
        Double camera = tryValueOf(zombie, Attributes.CAMERA_DISTANCE);

        ScaleService.apply(zombie, new ScaleState(scale, ScaleAxes.UNIFORM));

        helper.assertValueEqual(gravity, valueOf(zombie, Attributes.GRAVITY),
                "gravity followed a" + at + " scale with no channel set");
        helper.assertValueEqual(attack, valueOf(zombie, Attributes.ATTACK_DAMAGE),
                "attack damage followed a" + at + " scale with no channel set");
        helper.assertValueEqual(health, valueOf(zombie, Attributes.MAX_HEALTH),
                "max health followed a" + at + " scale with no channel set");
        helper.assertValueEqual(followRange, valueOf(zombie, Attributes.FOLLOW_RANGE),
                "follow range followed a" + at + " scale with no channel set");
        if (friction != null) {
            helper.assertValueEqual(friction, valueOf(zombie, Attributes.FRICTION_MODIFIER),
                    "friction followed a" + at + " scale with no channel set");
        }
        if (airDrag != null) {
            helper.assertValueEqual(airDrag, valueOf(zombie, Attributes.AIR_DRAG_MODIFIER),
                    "air drag followed a" + at + " scale with no channel set");
        }
        if (camera != null) {
            helper.assertValueEqual(camera, valueOf(zombie, Attributes.CAMERA_DISTANCE),
                    "camera distance followed a" + at + " scale with no channel set");
        }

        helper.assertTrue(valueOf(zombie, Attributes.MOVEMENT_SPEED) != motion,
                "motion did not follow a" + at + " scale");
        helper.assertTrue(valueOf(zombie, Attributes.JUMP_STRENGTH) != jump,
                "jump strength did not follow a" + at + " scale");
        helper.assertTrue(valueOf(zombie, Attributes.STEP_HEIGHT) != step,
                "step height did not follow a" + at + " scale");
        helper.assertTrue(valueOf(zombie, Attributes.ENTITY_INTERACTION_RANGE) != reach,
                "reach did not follow a" + at + " scale");
    }

    /**
     * M2-4: a frame eye override stays inside the box in every pose, and is
     * exactly the frame eye at standing.
     */
    public static void eyeInsideBoxForEveryPose(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        ((StatureScale) zombie).stature$setEyeHeight(1.9F);
        zombie.refreshDimensions();

        for (Pose pose : Pose.values()) {
            EntityDimensions dimensions = zombie.getDimensions(pose);
            helper.assertTrue(dimensions.eyeHeight() <= dimensions.height() + 1e-6F,
                    "eye leaves the box in pose " + pose);
        }
        EntityDimensions standing = zombie.getDimensions(Pose.STANDING);
        helper.assertTrue(Math.abs(standing.eyeHeight() - 1.9F) < 1e-4F,
                "standing eye is not the frame eye");
        helper.succeed();
    }

    /**
     * M2-3, server half: a frame-less entity resolves nothing, a resolved
     * answer wins over the file, and dropping the overrides returns the
     * entity to the file values.
     *
     * <p>The numbers are the Coblyn case from the plan: scale 0.4 under a
     * 0.5 micro edge is micro.
     */
    public static void framedThresholdsResolvedPerEntity(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        helper.assertTrue(StatureConfig.resolveThresholds(zombie) == null,
                "frame-less entity resolved thresholds");

        ((StatureScale) zombie).stature$setThresholds(new ResolvedThresholds(0.5F, 3.0F, Map.of()));
        helper.assertValueEqual(0.5F, StatureConfig.microOf(zombie),
                "micro edge is not the resolved one");
        helper.assertValueEqual(3.0F, StatureConfig.macroOf(zombie),
                "macro edge is not the resolved one");

        ScaleService.apply(zombie, new ScaleState(0.4F, ScaleAxes.UNIFORM));
        helper.assertTrue(StatureConfig.isMicro(zombie),
                "a 0.4x entity under a 0.5 micro edge is not micro");

        StatureConfig.pushThresholds(zombie);
        helper.assertTrue(((StatureScale) zombie).stature$thresholds() == null,
                "dropping the frame overrides did not return the entity to the file values");
        helper.succeed();
    }

    /**
     * M2: the uniform scale rides vanilla's own attribute save, so it comes
     * back from a round-trip with the axes untouched.
     */
    public static void uniformScaleSurvivesSave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        ScaleService.apply(zombie, new ScaleState(4.0F, ScaleAxes.UNIFORM));

        TagValueOutput out =
                TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        zombie.saveWithoutId(out);
        Zombie fresh = new Zombie(EntityTypes.ZOMBIE, level);
        fresh.load(TagValueInput.create(
                ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));

        helper.assertValueEqual(4.0F, ScaleService.stateOf(fresh).scale(),
                "uniform scale did not survive save");
        helper.assertTrue(ScaleAxes.UNIFORM.equals(ScaleService.stateOf(fresh).axes()),
                "axes drifted through a uniform save");
        helper.succeed();
    }

    /** Fails the test when this entity lacks the attribute; zombies carry the core set. */
    private static double valueOf(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            throw new AssertionError("test entity has no attribute " + attribute.getRegisteredName());
        }
        return instance.getValue();
    }

    /** Null when this entity lacks the attribute; the channel skips it the same way. */
    private static Double tryValueOf(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? null : instance.getValue();
    }
}
