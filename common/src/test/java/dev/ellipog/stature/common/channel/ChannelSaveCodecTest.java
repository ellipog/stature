package dev.ellipog.stature.common.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.junit.jupiter.api.Test;

/**
 * The channel save codec, entity-free: old ids drop while the rest of the save still lands.
 *
 * <p>{@code stature:voice_pitch} was removed after worlds had already stored it, so the mixed list
 * here is the real shape of an old save: one dropped id followed by a channel that still exists.
 */
class ChannelSaveCodecTest {

    private static CompoundTag oldSave() {
        TagValueOutput output =
                TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        ValueOutput.ValueOutputList list = output.childrenList("stature_channels");

        ValueOutput voice = list.addChild();
        voice.putString("id", "stature:voice_pitch");
        voice.putFloat("value", -0.35F);

        ValueOutput motion = list.addChild();
        motion.putString("id", "stature:motion");
        motion.putFloat("value", 2.0F);

        return output.buildResult();
    }

    private static ValueInput loaded(CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag);
    }

    @Test
    void voicePitchFromAnOldSaveIsSkippedWhileLiveChannelsLoad() {
        Map<String, Float> overrides = new LinkedHashMap<>();

        assertTrue(ChannelSaveCodec.restore(loaded(oldSave()), overrides));

        assertFalse(overrides.containsKey("stature:voice_pitch"));
        assertEquals(Map.of("stature:motion", 2.0F), overrides);
    }
}
