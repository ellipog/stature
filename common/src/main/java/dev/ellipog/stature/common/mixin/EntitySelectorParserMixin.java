package dev.ellipog.stature.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;

import net.minecraft.commands.arguments.selector.EntitySelectorParser;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets {@code stature:scale} be written unquoted in a selector.
 *
 * <p>Brigadier's {@code readString} stops at a colon, so a vanilla selector option name cannot
 * contain its namespace without quotes. The selector option table can hold a namespaced key, but
 * the parser never gets far enough to look it up. Reading the colon as part of the key is the
 * smallest fix that leaves every built-in option untouched, and it applies only to the
 * {@code stature} namespace.
 */
@Mixin(EntitySelectorParser.class)
public abstract class EntitySelectorParserMixin {

    @WrapOperation(
            method = "parseOptions",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readString()Ljava/lang/String;"))
    private String stature$readStatureOptionKey(StringReader reader, Operation<String> original) {
        String first = original.call(reader);
        if ("stature".equals(first) && reader.canRead() && reader.peek() == ':') {
            reader.skip();
            return first + ":" + original.call(reader);
        }
        return first;
    }
}
