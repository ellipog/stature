package dev.ellipog.stature.common;

import java.util.ArrayList;
import java.util.List;

import dev.ellipog.stature.common.channel.ChannelTuning;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Multiplies the count of loot-table drops from a scaled entity, without duplicating items it
 * could not legitimately stack.
 *
 * <p>Only stackable items are touched: a stack whose maximum size is one (a sword, a saddle, a
 * shulker box) is never copied, because "a giant dropped two of the unique thing" is a
 * duplication exploit rather than a size effect. Counts are rounded, never lowered below the
 * vanilla count, and a result larger than the item's stack size is split into several stacks that
 * preserve the original components rather than cloned into a single impossible one.
 *
 * <p>The factor only comes from {@code stature:drops_scale}, which defaults to a square-root curve
 * and is clamped by the server config. Equipment and inventory drops do not pass through the loot
 * table consumer and are deliberately untouched.
 */
public final class DropScaling {

    private DropScaling() {
    }

    /** The configured factor for this entity, exactly 1.0 when the channel is off. */
    public static float factor(LivingEntity entity) {
        return ChannelTuning.factor(entity, StatureConfig.DROPS_SCALE_CHANNEL);
    }

    /** One vanilla stack, split and multiplied according to {@code factor}. */
    public static List<ItemStack> scale(ItemStack stack, float factor) {
        if (stack.isEmpty()) {
            return List.of(stack);
        }
        List<Integer> counts = counts(stack.getCount(), stack.getMaxStackSize(), factor);
        if (counts.size() == 1 && counts.get(0) == stack.getCount()) {
            return List.of(stack);
        }
        List<ItemStack> stacks = new ArrayList<>(counts.size());
        for (int count : counts) {
            stacks.add(stack.copyWithCount(count));
        }
        return List.copyOf(stacks);
    }

    /**
     * The counts to spawn for one vanilla stack.
     *
     * <p>Package-private and game-free so the arithmetic is testable without bootstrapping the
     * item registry. A non-stackable item, a disabled factor and any factor at or below one all
     * produce the original single count.
     */
    static List<Integer> counts(int original, int maxStackSize, float factor) {
        if (original <= 0 || maxStackSize <= 1 || !Float.isFinite(factor) || factor <= 1.0F) {
            return List.of(Math.max(original, 0));
        }
        int wanted = Math.max(original, Math.round(original * factor));
        if (wanted == original) {
            return List.of(original);
        }
        List<Integer> counts = new ArrayList<>();
        int left = wanted;
        while (left > 0) {
            int count = Math.min(left, maxStackSize);
            counts.add(count);
            left -= count;
        }
        return List.copyOf(counts);
    }
}
