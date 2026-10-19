package dev.ellipog.stature.common.mixin;

import dev.ellipog.stature.common.Thresholds;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets micro entities slip through fences (plan §14.3).
 *
 * <p>Fences inherit their collision from here; bars and bamboo override theirs, so they
 * get their own mixins on the override. Pathfinding reads the same shapes through the
 * same entity context, so micro mobs route through fences instead of around them.
 */
@Mixin(BlockBehaviour.class)
public abstract class PorousBlocksMixin {

    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"),
            cancellable = true)
    private void stature$letMicrosThrough(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        if (!((Object) this instanceof FenceBlock)) {
            return;
        }
        if (context instanceof EntityCollisionContext collision
                && collision.getEntity() instanceof LivingEntity living
                && Thresholds.canPassThrough(living)) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
