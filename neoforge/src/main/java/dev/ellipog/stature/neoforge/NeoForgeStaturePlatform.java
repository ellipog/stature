package dev.ellipog.stature.neoforge;

import dev.ellipog.stature.common.StaturePlatform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * NeoForge's permissions half (improvement plan M5).
 *
 * <p>Players are checked with {@code mayBuild} and {@code mayInteract}, then NeoForge's
 * {@code BreakBlockEvent} so claims and protection mods can cancel the break. Mobs pass
 * {@code canEntityGrief} (the loader's griefing rule, including its own hooks) and
 * {@code onEntityDestroyBlock}, which is where NeoForge's block-break hooks see a mob.
 */
final class NeoForgeStaturePlatform implements StaturePlatform.Hooks {

    @Override
    public boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state, LivingEntity breaker) {
        if (breaker instanceof Player player) {
            if (!player.mayBuild() || !level.mayInteract(player, pos)) {
                return false;
            }
            BreakBlockEvent event = new BreakBlockEvent(level, pos, state, player);
            return !NeoForge.EVENT_BUS.post(event).isCanceled();
        }
        return EventHooks.canEntityGrief(level, breaker)
                && EventHooks.onEntityDestroyBlock(breaker, pos, state);
    }
}
