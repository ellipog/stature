package dev.ellipog.stature.common.mixin;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.FrameSource;

import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries an explicit size across death (plan §14.8's winner travels with the entity).
 *
 * <p>Respawn builds a brand-new {@code ServerPlayer} and copies chosen fields across in
 * {@code restoreFrom}; anything not copied is reborn vanilla. A hand-set or command-applied size
 * is a choice in the same sense as the inventory rather than a buff in the sense of a potion
 * effect, so MANUAL, COMMAND and INTEGRATION travel and everything else re-resolves: an auto-declared entity
 * comes back through {@code ensureAssigned} exactly like a new spawn, and a vanilla one stays
 * vanilla. Carried in both keepInventory states, deliberately: flipping with a gamerule would make
 * death mean two different things with no one asking for either.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("TAIL"))
    private void stature$carrySizeAcrossDeath(ServerPlayer oldPlayer, boolean keepAllPlayerData, CallbackInfo info) {
        StatureScale oldDuck = (StatureScale) oldPlayer;
        FrameSource source = oldDuck.stature$frameSource();
        if (!FrameService.survivesDeath(source)) {
            return;
        }

        ServerPlayer self = (ServerPlayer) (Object) this;
        StatureScale duck = (StatureScale) self;
        ScaleState oldState = ScaleService.stateOf(oldPlayer);

        duck.stature$setFrame(oldDuck.stature$frame());
        duck.stature$setEyeHeight(oldDuck.stature$eyeHeight());
        duck.stature$setFrameSource(source);
        // Instant and linear: this is a restoration, not a change, so there is nothing to ease.
        // The apply broadcasts, which unknown-id receivers drop and tracking then repeats.
        ScaleService.setSize(self, oldState, 0, Easing.LINEAR);
        duck.stature$setNeedsSync(true);
    }
}
