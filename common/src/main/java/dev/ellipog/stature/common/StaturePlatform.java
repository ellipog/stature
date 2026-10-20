package dev.ellipog.stature.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The loader seam for "may this entity break this block here" (improvement plan M5).
 *
 * <p>Claims, spawn protection and mob griefing live in the loader, not in common: Fabric's
 * {@code PlayerBlockBreakEvents.BEFORE}, NeoForge's {@code BreakBlockEvent}, and each loader's own
 * grief hooks. Stature's mechanics call this one method so no gameplay code names a loader event.
 *
 * <p>Installed at construction like {@link dev.ellipog.stature.common.net.StatureNetworking}, and
 * fail-open when it is not installed: a dedicated server with no seam is visibly more destructive,
 * not an entity tick that throws. A client never calls it.
 */
public final class StaturePlatform {

    /** The loader's half. One method per question gameplay needs answered. */
    public interface Hooks {

        /**
         * True when {@code breaker} may break {@code state} at {@code pos} right now.
         *
         * <p>Players pass {@code mayBuild} and {@code mayInteract} first, then the loader's break
         * event so claim and protection mods can veto. Mobs pass the vanilla griefing rules and the
         * loader's own grief hooks.
         */
        boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state, LivingEntity breaker);
    }

    private static volatile Hooks installed;

    private StaturePlatform() {
    }

    public static void install(Hooks hooks) {
        installed = hooks;
    }

    public static boolean mayBreak(
            ServerLevel level, BlockPos pos, BlockState state, LivingEntity breaker) {
        Hooks hooks = installed;
        return hooks == null || hooks.mayBreak(level, pos, state, breaker);
    }
}
