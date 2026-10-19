package dev.ellipog.stature.common.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.ellipog.stature.common.client.StatureRenderState;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales what a shaped entity holds (plan §14.9's {@code held_item}).
 *
 * <p>Off by default, deliberately: a big hand with a small sword is a choice, so an unset channel
 * draws exactly vanilla and only an override bends the item. The scale lands inside the layer's
 * own push and pop, after its translate, so the item pivots around the hand point it already
 * computed rather than sliding off it.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(
            method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void stature$scaleHeldItem(
            ArmedEntityRenderState state,
            ItemStackRenderState item,
            ItemStack itemStack,
            HumanoidArm arm,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            CallbackInfo info) {
        if (state instanceof StatureRenderState render) {
            float factor = render.stature$heldFactor();
            if (factor != 1.0F) {
                poseStack.scale(factor, factor, factor);
            }
        }
    }
}
