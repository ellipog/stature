package dev.ellipog.stature.common.channel;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The saved channel list: the NBT shape, and what an old id means.
 *
 * <p>Separate from {@link ChannelService} because that class owns attribute effects and so boots
 * the game's registries when it is first touched. Persistence touches only ids and numbers, which
 * keeps the old-save path testable without a running game.
 */
final class ChannelSaveCodec {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    private static final String KEY_CHANNELS = "stature_channels";

    /**
     * The pre-M1 id for {@code stature:below_name_distance}. Saved overrides under the old id
     * load onto the new channel, so a world written before the rename keeps its value.
     */
    private static final String LEGACY_NAME_TAG_HEIGHT = "stature:name_tag_height";

    /** Whether the rename alias above has been logged: the note fires once, not per entity. */
    private static final AtomicBoolean LEGACY_ALIAS_LOGGED = new AtomicBoolean(false);

    private ChannelSaveCodec() {
    }

    /**
     * Restores every channel this version still declares, skipping the rest.
     *
     * <p>A saved id can name a mod that is no longer installed, or a channel this version dropped.
     * An old save must still load, and it must not warn once per entity about an id that can never
     * be set again. Anything still declared lands; anything unknown is ignored quietly and is gone
     * on the next save.
     *
     * @return true when the save named any channels at all, even ones since dropped
     */
    static boolean restore(ValueInput input, Map<String, Float> overrides) {
        ChannelRegistry registry = ChannelRegistry.instance();
        boolean[] seen = new boolean[1];

        input.childrenListOrEmpty(KEY_CHANNELS).forEach(child -> {
            seen[0] = true;
            String id = child.getString("id").orElse(null);
            float value = child.getFloatOr("value", Float.NaN);
            if (LEGACY_NAME_TAG_HEIGHT.equals(id)) {
                if (LEGACY_ALIAS_LOGGED.compareAndSet(false, true)) {
                    LOG.info("loading a saved stature:name_tag_height override as "
                            + "stature:below_name_distance (renamed in M1)");
                }
                id = "stature:below_name_distance";
            }
            if (id == null || registry.get(id) == null) {
                return;
            }
            if (!Float.isFinite(value)) {
                LOG.warn("ignoring a saved channel value that is not a value ({}); it stays off", id);
                return;
            }
            overrides.put(id, value);
        });

        return seen[0];
    }

    /** Saves overrides only: easing progress is transient, targets travel. */
    static void save(Map<String, Float> overrides, ValueOutput output) {
        if (overrides.isEmpty()) {
            return;
        }

        ValueOutput.ValueOutputList list = output.childrenList(KEY_CHANNELS);
        for (Map.Entry<String, Float> entry : overrides.entrySet()) {
            ValueOutput child = list.addChild();
            child.putString("id", entry.getKey());
            child.putFloat("value", entry.getValue());
        }
    }
}
