package dev.ellipog.stature.common;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
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

    /** Louder steps for macro entities, vanilla volume for everything else. */
    public static float stepVolume(LivingEntity entity, float base) {
        if (!StatureConfig.enabled(entity, StatureConfig.SEISMIC_FOOTSTEPS)
                || !StatureConfig.isMacro(entity)) {
            return base;
        }
        return Math.min(base * ScaleService.stateOf(entity).scale(), 1.0F);
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

    private static void trample(LivingEntity entity) {
        if (!StatureConfig.enabled(entity, StatureConfig.TRAMPLING) || !StatureConfig.isMacro(entity)) {
            return;
        }
        Level level = entity.level();
        AABB box = entity.getBoundingBox();
        List<BlockPos> fragile = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                (int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ))) {
            if (isFragile(level.getBlockState(pos))) {
                fragile.add(pos.immutable());
            }
        }
        for (BlockPos pos : fragile) {
            level.destroyBlock(pos, true, entity);
        }
    }

    private static boolean isFragile(BlockState state) {
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.CROPS)) {
            return true;
        }
        return state.getBlock() == Blocks.GLASS_PANE || state.getBlock() instanceof StainedGlassPaneBlock;
    }

    private static void doorway(LivingEntity entity) {
        if (entity.tickCount % 20 != 0
                || !StatureConfig.enabled(entity, StatureConfig.DOORWAY_COLLISION)
                || !StatureConfig.isMacro(entity)) {
            return;
        }
        Level level = entity.level();
        BlockPos eye = BlockPos.containing(entity.getEyePosition());
        if (level.getBlockState(eye).isCollisionShapeFullBlock(level, eye)) {
            entity.hurtOrSimulate(level.damageSources().inWall(), 2.0F);
        }
    }
}
