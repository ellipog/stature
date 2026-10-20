package dev.ellipog.stature.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Threshold mechanics: what very small and very large entities get away with (plan §14.3).
 *
 * <p>Every mechanic checks its flag first, then its tier, so an ordinary entity pays one map
 * lookup per tick at most and a customized one pays a frame lookup. Climbing runs on both
 * sides for prediction; breaking and hurting are server-only.
 */
public final class Thresholds {

    /** The convention tag that covers modded panes alongside vanilla's two pane blocks. */
    private static final TagKey<net.minecraft.world.level.block.Block> GLASS_PANES =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "glass_panes"));

    private Thresholds() {
    }

    /** Whether this entity may climb any wall it is pressed against. */
    public static boolean canClimbWalls(LivingEntity entity) {
        return entity.horizontalCollision
                && StatureConfig.enabled(entity, StatureConfig.SURFACE_CLIMBING)
                && StatureConfig.isMicro(entity);
    }

    /** Whether this entity slips through bars, fences and bamboo. */
    public static boolean canPassThrough(LivingEntity entity) {
        return StatureConfig.enabled(entity, StatureConfig.POROUS_TRAVERSAL)
                && StatureConfig.isMicro(entity);
    }

    /**
     * Louder steps for macro entities, vanilla volume for everything else.
     *
     * <p>The cap is four rather than one on purpose: vanilla's {@code SoundEvent.getRange} gives
     * 16 blocks at a volume of one and grows above it, so the old cap silently stopped a 16x step
     * from being heard past 16 blocks. Four is 64 blocks, which is where the camera shake and the
     * footstep packet stop too.
     */
    public static float stepVolume(LivingEntity entity, float base) {
        if (!StatureConfig.enabled(entity, StatureConfig.SEISMIC_FOOTSTEPS)
                || !StatureConfig.isMacro(entity)) {
            return base;
        }
        return stepVolumeFor(base, ScaleService.stateOf(entity).scale());
    }

    /** The capped volume math, separated from the entity lookup so a unit test can drive it. */
    static float stepVolumeFor(float base, float scale) {
        return Math.min(base * scale, 4.0F);
    }

    /** Deeper steps for macro entities, vanilla pitch for everything else. */
    public static float stepPitch(LivingEntity entity, float base) {
        if (!StatureConfig.enabled(entity, StatureConfig.SEISMIC_FOOTSTEPS)
                || !StatureConfig.isMacro(entity)) {
            return base;
        }
        return (float) (base / Math.sqrt(ScaleService.stateOf(entity).scale()));
    }

    /** Breaking and hurting. Server-only, called from the entity tick hook. */
    public static void tick(LivingEntity entity) {
        trample(entity);
        doorway(entity);
    }

    /**
     * Breaks the fragile blocks a giant's feet are standing in.
     *
     * <p>Every fourth tick, staggered by entity id, because the scan is O(box volume) and at 16x
     * that is thousands of positions. Only the lower third plus the foot layer is read: leaves and
     * crops at head height are not something a foot touched, and the old whole-box scan broke them.
     * A per-scan cap bounds the work and the surprise; the platform seam decides whether the break
     * is allowed at all (claims, spawn protection, mob griefing).
     */
    private static void trample(LivingEntity entity) {
        if (!StatureConfig.enabled(entity, StatureConfig.TRAMPLING) || !StatureConfig.isMacro(entity)) {
            return;
        }
        if ((entity.tickCount + entity.getId()) % 4 != 0) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB box = entity.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        // The foot layer is the block just below the box: a 16x zombie standing on leaves has its
        // box at y = top of the leaf, so the block at floor(minY) is air and the one at
        // floor(minY) - 1 is the leaf it is actually standing on.
        int minY = (int) Math.floor(box.minY) - 1;
        int maxY = (int) Math.floor(box.minY + box.getYsize() / 3.0);

        int broken = 0;
        int cap = StatureConfig.trampleMaxPerScan();
        boolean drops = StatureConfig.trampleDrops();
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (broken >= cap) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!isFragile(state)) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            if (!StaturePlatform.mayBreak(level, immutable, state, entity)) {
                continue;
            }
            if (level.destroyBlock(immutable, drops, entity)) {
                broken++;
            }
        }
    }

    private static boolean isFragile(BlockState state) {
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.CROPS) || state.is(GLASS_PANES)) {
            return true;
        }
        return state.getBlock() == Blocks.GLASS_PANE || state.getBlock() instanceof StainedGlassPaneBlock;
    }

    /**
     * Hurts a macro entity whose head is inside a full block.
     *
     * <p>Runs once per second, skips crouching and crawling because the pose already shrank the
     * box, and skips {@code isInWall} so a suffocating entity is not charged twice in the same
     * tick. The damage type is Stature's own, so the death message says compression rather than
     * "suffocated in a wall".
     */
    private static void doorway(LivingEntity entity) {
        if (entity.tickCount % 20 != 0
                || !StatureConfig.enabled(entity, StatureConfig.DOORWAY_COLLISION)
                || !StatureConfig.isMacro(entity)) {
            return;
        }
        if (entity.getPose() == Pose.CROUCHING || entity.getPose() == Pose.SWIMMING) {
            return;
        }
        if (entity.isInWall()) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos eye = BlockPos.containing(entity.getEyePosition());
        if (!level.getBlockState(eye).isCollisionShapeFullBlock(level, eye)) {
            return;
        }
        entity.hurtOrSimulate(StatureDamageTypes.compression(level), 2.0F);
    }
}
