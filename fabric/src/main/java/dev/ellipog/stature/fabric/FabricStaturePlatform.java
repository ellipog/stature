package dev.ellipog.stature.fabric;

import dev.ellipog.stature.common.StaturePlatform;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Fabric's permissions half (improvement plan M5).
 *
 * <p>Players are checked exactly as a hand break would be: {@code mayBuild} covers adventure and
 * spectator rules, {@code mayInteract} covers spawn protection, and Fabric's
 * {@code PlayerBlockBreakEvents.BEFORE} is the event claim mods already hook. Mobs use vanilla's
 * griefing rule; Fabric has no second grief hook to consult.
 */
final class FabricStaturePlatform implements StaturePlatform.Hooks {

    @Override
    public boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state, LivingEntity breaker) {
        if (breaker instanceof Player player) {
            return player.mayBuild()
                    && level.mayInteract(player, pos)
                    && PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                            level, player, pos, state, level.getBlockEntity(pos));
        }
        return level.getGameRules().get(GameRules.MOB_GRIEFING);
    }
}
