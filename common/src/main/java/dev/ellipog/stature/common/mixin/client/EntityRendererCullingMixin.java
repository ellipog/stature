package dev.ellipog.stature.common.mixin.client;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps oversized pictures from popping out of view (plan §14 dangerous-mode culling).
 *
 * <p>Vanilla culls on the <em>box</em>: {@code getBoundingBoxForCulling(entity, pt).inflate(0.5)}.
 * Past the envelope the picture separates from the box on purpose -- a dangerous-mode model override
 * or a limb part factor -- so a 100× model vanishes the moment its 16× box leaves the frustum.
 * The hook inflates the culling box by the visual excess: the model ratio above the box, or the
 * largest part overhang. At 1× with no factors the excess is zero and culling is bit-for-bit
 * vanilla. Absurd sizes inflate to effectively-uncullable, which is the honest answer for a
 * picture hundreds of blocks wide.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererCullingMixin {

    @Inject(
            method = "getBoundingBoxForCulling(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/world/phys/AABB;",
            at = @At("RETURN"),
            cancellable = true)
    private void stature$inflateForModel(
            Entity entity, float partialTicks, CallbackInfoReturnable<AABB> cir) {
        if (!(entity instanceof LivingEntity living) || !(entity instanceof StatureScale scaled)) {
            return;
        }
        float base = living.getScale();
        if (!(base > 0.0F) || !Float.isFinite(base)) {
            return;
        }
        float excess = 0.0F;
        float model = ChannelService.visualScale(living, base);
        if (model > base && Float.isFinite(model)) {
            excess = Math.max(
                    excess, (model / base - 1.0F) * Math.max(living.getBbWidth(), living.getBbHeight()) * 0.5F);
        }
        for (ScaleAxes factors : scaled.stature$partFactors().values()) {
            float limb = Math.max(factors.width(), Math.max(factors.height(), factors.depth()));
            if (limb > 1.0F) {
                excess = Math.max(excess, (limb - 1.0F) * living.getBbWidth());
            }
        }
        if (excess > 0.0F && Float.isFinite(excess)) {
            cir.setReturnValue(cir.getReturnValue().inflate(excess, excess, excess));
        }
    }
}
