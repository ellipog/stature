package dev.ellipog.stature.common.mixin;

import java.util.function.Predicate;

import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens vanilla's selector-option registration method.
 *
 * <p>26.3 keeps {@code EntitySelectorOptions.register} private, so a mod cannot add an option by
 * calling it directly. This invoker calls the same method the built-ins use, which keeps the option
 * in the vanilla map: it supports suggestions, the normal unknown-option error and the same
 * range parser as every other selector option, instead of installing a parallel parser.
 */
@Mixin(EntitySelectorOptions.class)
public interface EntitySelectorOptionsInvoker {

    @Invoker("register")
    static void stature$register(
            String name,
            EntitySelectorOptions.Modifier modifier,
            Predicate<EntitySelectorParser> canUse,
            Component description) {
        throw new AssertionError("mixin invoker");
    }
}
