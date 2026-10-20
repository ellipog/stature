package dev.ellipog.stature.common.command;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.mixin.EntitySelectorOptionsInvoker;

import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The {@code stature:scale} entity-selector option.
 *
 * <p>Vanilla owns the selector-option table; this class calls the same registration method the
 * built-ins use (through a mixin invoker, because 26.3 made it private), rather than installing a
 * parallel parser.
 * The option uses vanilla's numeric range grammar, so {@code 0.5}, {@code 0.5..2}, {@code ..0.5}
 * and {@code 2..} all mean what they mean everywhere else in a command.
 *
 * <p>Registration waits for command registration rather than a mod constructor: vanilla
 * bootstraps the selector table early, and registering before that would make its own {@code
 * bootStrap} see a non-empty map and skip every built-in option. Registration is idempotent
 * because a server can be started more than once in one process.
 *
 * <p>Only living entities have a Stature scale; any other entity simply never matches, which is
 * why no separate error type is needed.
 */
public final class StatureEntitySelectors {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private StatureEntitySelectors() {
    }

    /** Adds the option once, at a point where vanilla has already bootstrapped its own table. */
    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        EntitySelectorOptionsInvoker.stature$register(
                "stature:scale",
                parser -> {
                    MinMaxBounds.Doubles range =
                            MinMaxBounds.Doubles.fromReader(parser.getReader());
                    parser.addPredicate(entity -> matchesScale(entity, range));
                },
                parser -> true,
                Component.translatable("argument.entity.options.stature.scale.description"));
    }

    private static boolean matchesScale(Entity entity, MinMaxBounds.Doubles range) {
        return entity instanceof LivingEntity living
                && range.matches(ScaleService.stateOf(living).scale());
    }
}
