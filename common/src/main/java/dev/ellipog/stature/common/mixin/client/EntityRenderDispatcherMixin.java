package dev.ellipog.stature.common.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.ellipog.stature.common.StatureProjectile;
import dev.ellipog.stature.common.client.ProjectileRenderScaling;
import dev.ellipog.stature.common.client.StatureProjectileRenderState;
import dev.ellipog.stature.common.client.StatureRenderState;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two things the dispatcher knows before any renderer does: whether an entity should be seen,
 * and how large its projectile model should be.
 *
 * <p>Visibility is one gate at the dispatcher's funnel rather than three scattered ones: cancelling
 * here skips the model, the nameplate and the shadow together, so a scale that should go unseen
 * leaves no floating plate or stray shadow behind. Anything not hidden passes through untouched,
 * and non-living states never implement the interface so they cannot trip the check.
 *
 * <p>Projectile size is copied from the entity after extraction and applied around the renderer's
 * own submission. That is one hook for arrows, tridents, thrown items, fireballs, wind charges and
 * modded projectiles, instead of one per renderer. The pose stack is already translated to the
 * entity's render origin by the time the renderer runs; scaling there keeps that origin fixed, so
 * the box grows around the same floor and centre line the vanilla box uses. A stuck arrow's head
 * stays in the block instead of being pushed out or pulled through.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void stature$copyProjectileScale(
            Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (state instanceof StatureProjectileRenderState render) {
            float factor = entity instanceof StatureProjectile projectile
                    ? ProjectileRenderScaling.factor(projectile)
                    : 1.0F;
            render.stature$setProjectileScale(factor);
        }
    }

    /**
     * Scales one projectile's submission, not the dispatcher's shadow, flame or nameplate work.
     *
     * <p>The wrap is around the renderer call alone, so vanilla's shadow and fire animation keep
     * using the dimensions they already read from the state. Wrapping rather than redirecting lets
     * a second mod's wrapper compose around this one instead of conflicting with it.
     */
    @WrapOperation(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void stature$scaleProjectile(
            EntityRenderer<?, ?> renderer,
            EntityRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            Operation<Void> original) {
        float factor = renderState instanceof StatureProjectileRenderState render
                ? render.stature$projectileScale()
                : 1.0F;
        if (factor == 1.0F) {
            original.call(renderer, renderState, poseStack, submitNodeCollector, camera);
            return;
        }
        poseStack.pushPose();
        poseStack.scale(factor, factor, factor);
        original.call(renderer, renderState, poseStack, submitNodeCollector, camera);
        poseStack.popPose();
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void stature$skipHidden(
            EntityRenderState renderState,
            CameraRenderState camera,
            double x,
            double y,
            double z,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CallbackInfo info) {
        if (renderState instanceof StatureRenderState render && render.stature$hidden()) {
            info.cancel();
        }
    }
}
