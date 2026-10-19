package dev.ellipog.stature.common.mixin;

import dev.ellipog.stature.common.Thresholds;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
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
 * Bars' half of porous traversal: they decide collision in the override, which never
 * calls the base implementation, so the base-level mixin cannot see them. Panes stay
 * solid on purpose, a window is a wall even to something small.
 */
@Mixin(CrossCollisionBlock.class)
public abstract class BarsPorousMixin {

    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"),
            cancellable = true)
    private void stature$letMicrosThrough(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        Block self = (Block) (Object) this;
        if (self == Blocks.GLASS_PANE || self instanceof StainedGlassPaneBlock) {
            return;
        }
        if (context instanceof EntityCollisionContext collision
                && collision.getEntity() instanceof LivingEntity living
                && Thresholds.canPassThrough(living)) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
