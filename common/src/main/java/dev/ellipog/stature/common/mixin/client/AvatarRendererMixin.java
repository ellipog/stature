package dev.ellipog.stature.common.mixin.client;

import java.util.Map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.StatureScale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scales first-person arms with their part factors (plan §14.7).
 *
 * <p>Hands bypass model consumption entirely: the renderer submits the arm nodes directly, so
 * there is no model and no state in sight, and anything stashed for {@code prepareModel} is long
 * gone by the time the pose lands. The factor is therefore applied to the {@code PoseStack}
 * around the submission itself: translate to the arm pivot, scale, translate back. The pose is
 * captured at submit time, so this survives the deferral that made the old HEAD/RETURN handoff
 * a no-op. Sleeves ride along as children of the arms. A frame opting out keeps vanilla hands.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @WrapOperation(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/UvMapping;)V"))
    private void stature$scaleFirstPersonArm(
            SubmitNodeCollector instance,
            ModelPart arm,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            UvMapping uvMapping,
            Operation<Void> original) {
        ScaleAxes factor = stature$armFactor(arm);
        if (factor == null) {
            original.call(instance, arm, poseStack, renderType, lightCoords, overlayCoords, uvMapping);
            return;
        }
        // About the pivot, not the origin: scaling the stack as-is would swing the arm away
        // from the shoulder, while the pivot translate keeps the shoulder pinned and only the
        // length changes. Pushed and popped, so the hand renderer after us starts clean.
        poseStack.pushPose();
        poseStack.translate(arm.x / 16.0F, arm.y / 16.0F, arm.z / 16.0F);
        poseStack.scale(factor.width(), factor.height(), factor.depth());
        poseStack.translate(-arm.x / 16.0F, -arm.y / 16.0F, -arm.z / 16.0F);
        original.call(instance, arm, poseStack, renderType, lightCoords, overlayCoords, uvMapping);
        poseStack.popPose();
    }

    /**
     * This hand's part factor, or null for vanilla. The local player's own factors fill the gap
     * left by the missing state, resolved straight to the two arm nodes without any tree walk.
     */
    private ScaleAxes stature$armFactor(ModelPart arm) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        StatureScale duck = (StatureScale) player;
        if (duck.stature$firstPersonVanilla()) {
            return null;
        }
        Map<String, ScaleAxes> factors = duck.stature$partFactors();
        if (factors.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        PlayerModel model =
                ((LivingEntityRenderer<?, ?, PlayerModel>) (Object) this).getModel();
        String path = arm == model.rightArm ? "right_arm"
                : arm == model.leftArm ? "left_arm" : null;
        if (path == null) {
            return null;
        }
        ScaleAxes factor = factors.get(path);
        return factor != null && !factor.isUniform() ? factor : null;
    }
}
