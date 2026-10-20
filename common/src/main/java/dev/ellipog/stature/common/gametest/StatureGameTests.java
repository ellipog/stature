package dev.ellipog.stature.common.gametest;

import java.util.Map;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ResolvedThresholds;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureDamageTypes;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.Thresholds;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.parts.PartService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
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

    /**
     * M5: a 16x zombie in leaves breaks nothing while {@code mob_griefing} is false, and a capped
     * handful when it is true.
     */
    public static void trampleRespectsMobGriefing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(0, 2, 0));
        clearVolume(level, base, 8, 2, 35);
        fillLeaves(level, base, LEAF_RADIUS);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 0, 2, 0);
        ScaleService.apply(zombie, new ScaleState(16.0F, ScaleAxes.UNIFORM));
        zombie.tickCount = scanTick(zombie);

        boolean previous = level.getGameRules().get(GameRules.MOB_GRIEFING);
        try {
            level.getGameRules().set(GameRules.MOB_GRIEFING, false, level.getServer());
            Thresholds.tick(zombie);
            helper.assertValueEqual(countLeaves(level, base, LEAF_RADIUS), leafCells(),
                    "a 16x zombie broke leaves with mob_griefing false");

            level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
            Thresholds.tick(zombie);
            int broken = leafCells() - countLeaves(level, base, LEAF_RADIUS);
            helper.assertTrue(broken > 0,
                    "a 16x zombie broke no leaves with mob_griefing true");
            helper.assertTrue(broken <= 8,
                    "trample broke " + broken + " blocks in one scan, cap is 8");
        } finally {
            level.getGameRules().set(GameRules.MOB_GRIEFING, previous, level.getServer());
            clearLeaves(level, base, LEAF_RADIUS);
        }
        helper.succeed();
    }

    /** M5: one scan destroys at most the configured cap, even in a leaf field that fills the box. */
    public static void trampleCappedPerScan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(0, 2, 0));
        clearVolume(level, base, 8, 2, 35);
        fillLeaves(level, base, LEAF_RADIUS);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 0, 2, 0);
        ScaleService.apply(zombie, new ScaleState(16.0F, ScaleAxes.UNIFORM));
        zombie.tickCount = scanTick(zombie);

        boolean previous = level.getGameRules().get(GameRules.MOB_GRIEFING);
        try {
            level.getGameRules().set(GameRules.MOB_GRIEFING, true, level.getServer());
            Thresholds.tick(zombie);
            int broken = leafCells() - countLeaves(level, base, LEAF_RADIUS);
            helper.assertTrue(broken > 0, "trample broke nothing in a full leaf field");
            helper.assertTrue(broken <= 8,
                    "trample broke " + broken + " blocks in one scan, cap is 8");
        } finally {
            level.getGameRules().set(GameRules.MOB_GRIEFING, previous, level.getServer());
            clearLeaves(level, base, LEAF_RADIUS);
        }
        helper.succeed();
    }

    /**
     * M5: growing from 1x to 16x in a 3-high tunnel that opens in front ends outside the tunnel,
     * with at most one compression hit.
     *
     * <p>The roof is deliberately six layers thick so the straight-up probes cannot escape; the
     * only free whole-box position within the search budget is forward, out the open mouth. That
     * pins the shell search rather than a lucky upward teleport.
     */
    public static void growInTunnelEndsInAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(0, 2, 0));
        clearVolume(level, base, 10, 2, 40);
        buildTunnel(level, base);
        var compression = StatureDamageTypes.compression(level);
        helper.assertValueEqual(compression.type().msgId(), "compression",
                "stature:compression did not load from the datapack");
        helper.assertTrue(compression.is(DamageTypeTags.BYPASSES_ARMOR),
                "compression is not in the bypasses_armor tag");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 0, 2, 0);
        zombie.setHealth(zombie.getMaxHealth());

        ScaleService.apply(zombie, new ScaleState(16.0F, ScaleAxes.UNIFORM));
        zombie.tickCount = 0;
        Thresholds.tick(zombie);

        helper.assertTrue(level.noCollision(zombie, zombie.getBoundingBox()),
                "the grown zombie still intersects the tunnel");
        helper.assertTrue(zombie.getZ() > base.getZ() + 1.0,
                "the grown zombie did not leave through the open end");
        helper.assertTrue(zombie.getHealth() >= zombie.getMaxHealth() - 2.0F,
                "the grown zombie took more than one compression hit");
        clearTunnel(level, base);
        helper.succeed();
    }

    /** M5: a dangerous set's model does not outlive a plain set. */
    public static void dangerousModelResetOnPlainSet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 0, 2, 0);
        zombie.addTag("stature.dangerous");
        try {
            runCommand(level, "stature set @e[tag=stature.dangerous,limit=1] 100 --dangerous");
            helper.assertValueEqual(ScaleService.stateOf(zombie).scale(), 16.0F,
                    "dangerous set did not clamp the box to 16x");
            helper.assertValueEqual(
                    ChannelService.visualScale(zombie, ScaleService.stateOf(zombie).scale()), 100.0F,
                    "dangerous set did not put 100x on the model channel");
            helper.assertTrue(((StatureScale) zombie).stature$dangerousModel(),
                    "dangerous set did not mark the model as dangerous");

            runCommand(level, "stature set @e[tag=stature.dangerous,limit=1] 1");
            helper.assertValueEqual(ScaleService.stateOf(zombie).scale(), 1.0F,
                    "plain set did not return the box to 1x");
            helper.assertFalse(((StatureScale) zombie).stature$channelOverrides()
                            .containsKey(ChannelService.MODEL),
                    "plain set left the dangerous model channel behind");
            helper.assertValueEqual(
                    ChannelService.visualScale(zombie, ScaleService.stateOf(zombie).scale()), 1.0F,
                    "the model is still the dangerous size after a plain set");
        } finally {
            zombie.removeTag("stature.dangerous");
        }
        helper.succeed();
    }

    /** M5: {@code /stature reset} clears scale, shape, frame, channels and parts in one command. */
    public static void resetClearsEverything(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 0, 2, 0);
        zombie.addTag("stature.reset");
        try {
            runCommand(level, "stature set @e[tag=stature.reset,limit=1] 4");
            runCommand(level, "stature size @e[tag=stature.reset,limit=1] 2 1 1");
            runCommand(level, "stature channel @e[tag=stature.reset,limit=1] stature:model 8");
            runCommand(level, "stature part @e[tag=stature.reset,limit=1] head 2 2 2");
            helper.assertFalse(ScaleService.stateOf(zombie).axes().isUniform(),
                    "shape was not set before reset");
            helper.assertTrue(((StatureScale) zombie).stature$partFactors().containsKey("head"),
                    "part was not set before reset");
            helper.assertTrue(((StatureScale) zombie).stature$channelOverrides()
                            .containsKey(ChannelService.MODEL),
                    "model channel was not set before reset");

            runCommand(level, "stature reset @e[tag=stature.reset,limit=1]");
            helper.assertValueEqual(ScaleService.stateOf(zombie).scale(), 1.0F,
                    "reset left a non-vanilla uniform scale");
            helper.assertTrue(ScaleService.stateOf(zombie).axes().isUniform(),
                    "reset left a shaped box");
            helper.assertTrue(FrameService.frameOf(zombie) == null,
                    "reset left a frame");
            helper.assertFalse(ChannelService.hasSyncedState(zombie),
                    "reset left channel state");
            helper.assertTrue(((StatureScale) zombie).stature$partFactors().isEmpty(),
                    "reset left part factors");
            helper.assertFalse(((StatureScale) zombie).stature$dangerousModel(),
                    "reset left the dangerous model flag");
        } finally {
            zombie.removeTag("stature.reset");
        }
        helper.succeed();
    }

    /**
     * M5: a foreign {@code minecraft:scale} modifier multiplies on top of Stature's base and is not
     * folded into it by a later apply.
     */
    public static void foreignScaleModifierNoDrift(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 0, 2, 0);
        AttributeInstance scale = zombie.getAttribute(Attributes.SCALE);
        helper.assertTrue(scale != null, "zombie has no minecraft:scale attribute");
        Identifier foreign = Identifier.fromNamespaceAndPath("stature", "test_foreign_scale");
        scale.addOrUpdateTransientModifier(
                new AttributeModifier(foreign, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

        ScaleService.apply(zombie, new ScaleState(4.0F, ScaleAxes.UNIFORM));
        helper.assertValueEqual(ScaleService.stateOf(zombie).scale(), 4.0F,
                "Stature read the modified scale value instead of its base");
        helper.assertTrue(Math.abs(scale.getValue() - 6.0) < 1e-6,
                "the foreign modifier did not multiply on top: " + scale.getValue());

        ScaleService.apply(zombie, new ScaleState(2.0F, ScaleAxes.UNIFORM));
        helper.assertValueEqual(ScaleService.stateOf(zombie).scale(), 2.0F,
                "a second apply changed Stature's base instead of replacing it");
        helper.assertTrue(Math.abs(scale.getValue() - 3.0) < 1e-6,
                "the foreign modifier drifted after a second apply: " + scale.getValue());

        scale.removeModifier(foreign);
        ScaleService.apply(zombie, ScaleState.UNIFORM);
        helper.succeed();
    }

    /** The leaf field is 13x13, big enough to contain the 16x box's foot layer. */
    private static final int LEAF_RADIUS = 6;

    private static int leafCells() {
        int side = LEAF_RADIUS * 2 + 1;
        return side * side;
    }

    private static int scanTick(Zombie zombie) {
        // The scan is (tickCount + id) % 4 == 0. Pick the smallest non-negative tickCount that
        // satisfies it for this entity's id, so the test drives the same gate a tick would.
        return (4 - (zombie.getId() & 3)) & 3;
    }

    /**
     * Clears the world around a test base before building the fixture.
     *
     * <p>The gametest level is a real world with terrain, and the test origin can land inside it.
     * Without this the grown box intersects stone, displacement moves the entity somewhere else,
     * and a test that looks like it is about leaves or a tunnel is actually measuring the world.
     */
    private static void clearVolume(ServerLevel level, BlockPos base, int radius, int below, int above) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -below; dy <= above; dy++) {
                    level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void fillLeaves(ServerLevel level, BlockPos base, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.OAK_LEAVES.defaultBlockState());
            }
        }
    }

    private static int countLeaves(ServerLevel level, BlockPos base, int radius) {
        int leaves = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (level.getBlockState(base.offset(dx, -1, dz)).is(BlockTags.LEAVES)) {
                    leaves++;
                }
            }
        }
        return leaves;
    }

    private static void clearLeaves(ServerLevel level, BlockPos base, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * A stone block with a 3-high, 3-wide tunnel carved from the front face backwards, plus a
     * six-layer roof. The only whole-box escape within the search budget is forward, out of the
     * mouth.
     */
    private static void buildTunnel(ServerLevel level, BlockPos base) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -5; z <= 0; z++) {
                for (int y = -1; y <= 8; y++) {
                    boolean interior = x >= -1 && x <= 1 && y >= 0 && y <= 2 && z >= -4 && z <= 0;
                    if (!interior) {
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
    }

    private static void clearTunnel(ServerLevel level, BlockPos base) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -5; z <= 1; z++) {
                for (int y = -1; y <= 8; y++) {
                    level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void runCommand(ServerLevel level, String command) {
        // The source is put in the test level: createCommandSourceStack() starts in the respawn
        // dimension, and a gametest level need not be that dimension.
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withLevel(level), command);
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
