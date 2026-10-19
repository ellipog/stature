package dev.ellipog.stature.common.parts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.mixin.LivingEntityMixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.junit.jupiter.api.Test;

/**
 * Part persistence without a world: the save and load paths touch duck maps only, so the real
 * mixin class stands in for an entity and real NBT stands in for a disk.
 */
class PartSaveLoadTest {

    private static StatureScale freshDuck() {
        return new LivingEntityMixin() {
        };
    }

    private static CompoundTag saved(StatureScale duck) {
        TagValueOutput output =
                TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        PartService.save(duck, output);
        return output.buildResult();
    }

    private static ValueInput loaded(CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag);
    }

    @Test
    void factorsSurviveTheTrip() {
        StatureScale duck = freshDuck();
        duck.stature$partFactors().put("head", new ScaleAxes(2.0F, 2.0F, 2.0F));
        duck.stature$partFactors().put("body.left_arm", new ScaleAxes(1.5F, 1.0F, 1.0F));

        StatureScale reloaded = freshDuck();
        assertTrue(PartService.load(reloaded, loaded(saved(duck))));
        assertEquals(duck.stature$partFactors(), reloaded.stature$partFactors());
    }

    @Test
    void vanillaSavesNothingAndLoadsNothing() {
        StatureScale duck = freshDuck();
        CompoundTag tag = saved(duck);
        assertFalse(tag.contains("stature_parts"));

        StatureScale reloaded = freshDuck();
        assertFalse(PartService.load(reloaded, loaded(tag)));
        assertTrue(reloaded.stature$partFactors().isEmpty());
    }

    @Test
    void corruptEntriesDropWhileGoodOnesLand() {
        TagValueOutput output =
                TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        ValueOutput.ValueOutputList list = output.childrenList("stature_parts");
        ValueOutput good = list.addChild();
        good.putString("part", "head");
        good.putFloat("x", 2.0F);
        good.putFloat("y", 2.0F);
        good.putFloat("z", 2.0F);
        ValueOutput bad = list.addChild();
        bad.putString("part", "body");
        bad.putFloat("x", Float.NaN);
        bad.putFloat("y", 1.0F);
        bad.putFloat("z", 1.0F);

        StatureScale reloaded = freshDuck();
        assertTrue(PartService.load(reloaded, loaded(output.buildResult())));
        assertEquals(Map.of("head", new ScaleAxes(2.0F, 2.0F, 2.0F)), reloaded.stature$partFactors());
    }
}
