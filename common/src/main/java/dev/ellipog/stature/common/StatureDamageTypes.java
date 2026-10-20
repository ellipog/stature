package dev.ellipog.stature.common;

import dev.ellipog.stature.api.Stature;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Stature's own damage type: being crushed by a box that outgrew its space.
 *
 * <p>The type is data, not code: {@code data/stature/damage_type/compression.json} plus a lang key
 * for the death message (improvement plan M5). Wrapping it here keeps the lookup in one place and
 * degrades to vanilla {@code in_wall} damage if a pack has somehow removed the type, because a
 * missing data file must not throw inside an entity tick.
 */
public final class StatureDamageTypes {

    /** The datapack key for compression damage. */
    public static final ResourceKey<DamageType> COMPRESSION = ResourceKey.create(
            Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Stature.MOD_ID, "compression"));

    private StatureDamageTypes() {
    }

    public static DamageSource compression(Level level) {
        Optional<Holder.Reference<DamageType>> type =
                level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(COMPRESSION);
        return type.map(DamageSource::new).orElseGet(() -> level.damageSources().inWall());
    }
}
