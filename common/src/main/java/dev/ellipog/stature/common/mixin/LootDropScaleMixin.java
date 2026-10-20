package dev.ellipog.stature.common.mixin;

import java.util.function.Consumer;

import dev.ellipog.stature.common.DropScaling;
import dev.ellipog.stature.common.StatureConfig;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Multiplies the count of one entity's loot-table drops by its {@code drops_scale} channel.
 *
 * <p>The consumer is wrapped rather than the result list: vanilla's
 * {@code table.getRandomItems(params, seed, consumer)} streams each generated stack, and wrapping
 * the consumer keeps the roll count, the loot seed, luck and every loot modifier exactly as they
 * were. Only the stack's count is changed, and only for stackable items; equipment and inventory
 * drops never pass through this method and are untouched.
 */
@Mixin(LivingEntity.class)
public abstract class LootDropScaleMixin {

    @ModifyVariable(
            method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5)
    private Consumer<ItemStack> stature$scaleLootConsumer(Consumer<ItemStack> consumer) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!StatureConfig.enabled(self, StatureConfig.DROPS_SCALE)) {
            return consumer;
        }
        float factor = DropScaling.factor(self);
        if (factor <= 1.0F) {
            return consumer;
        }
        return stack -> {
            for (ItemStack scaled : DropScaling.scale(stack, factor)) {
                consumer.accept(scaled);
            }
        };
    }
}
