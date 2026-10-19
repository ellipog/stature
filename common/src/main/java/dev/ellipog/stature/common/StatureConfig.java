package dev.ellipog.stature.common;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.ellipog.stature.common.frame.FrameFile;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.StatureFrames;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Threshold tiers and mechanic toggles (plan §14.3).
 *
 * <p>One file, {@code stature.json} in the server's config dir, holds the two tier edges and a
 * flag per mechanic; a frame may override any of them for its entities. The server loads the
 * file at startup and on reload, then sends it to each client on login, so both sides gate
 * movement the same way. A missing file is created with defaults; a malformed one is reported
 * and ignored, leaving whatever was loaded before.
 */
public final class StatureConfig {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    private static final String FILE_NAME = "stature.json";

    /** Mechanic flag keys, shared by the file, frames and gates. */
    public static final String POROUS_TRAVERSAL = "porous_traversal";

    public static final String SURFACE_CLIMBING = "surface_climbing";

    public static final String AMPLIFIED_KNOCKBACK = "amplified_knockback";

    public static final String TRAMPLING = "trampling";

    public static final String SEISMIC_FOOTSTEPS = "seismic_footsteps";

    public static final String DOORWAY_COLLISION = "doorway_collision";

    private static Path file;

    private static float microThreshold = 0.25F;

    private static float macroThreshold = 2.5F;

    private static final Map<String, Boolean> FLAGS = new LinkedHashMap<>(Map.of(
            POROUS_TRAVERSAL, true,
            SURFACE_CLIMBING, true,
            AMPLIFIED_KNOCKBACK, true,
            TRAMPLING, true,
            SEISMIC_FOOTSTEPS, true,
            DOORWAY_COLLISION, true));

    private StatureConfig() {
    }

    /** Loads (or creates, then loads) the config in this directory. Returns the path used. */
    public static synchronized Path load(Path configDir) {
        file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            writeDefaults();
        }
        read();
        return file;
    }

    /** Re-reads the file loaded at startup. Silent when nothing was ever loaded. */
    public static synchronized void reload() {
        if (file != null) {
            read();
        }
    }

    /** Applies values received from the server. Client-side only. */
    public static synchronized void applySynced(float micro, float macro, Map<String, Boolean> flags) {
        if (micro > 0.0F && macro > micro) {
            microThreshold = micro;
            macroThreshold = macro;
        }
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (FLAGS.containsKey(entry.getKey())) {
                FLAGS.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** A snapshot for packets: micro, macro, then the flags. */
    public static synchronized Map<String, Boolean> flagSnapshot() {
        return Map.copyOf(FLAGS);
    }

    public static synchronized float microThreshold() {
        return microThreshold;
    }

    public static synchronized float macroThreshold() {
        return macroThreshold;
    }

    /** The known mechanic flag keys, in file order. */
    public static synchronized Set<String> flagKeys() {
        return Set.copyOf(FLAGS.keySet());
    }

    /** Whether this mechanic runs for this entity: its frame may override the file. */
    public static boolean enabled(LivingEntity entity, String mechanic) {
        Identifier frameId = FrameService.frameOf(entity);
        if (frameId != null) {
            FrameFile frame = StatureFrames.file(frameId);
            if (frame != null) {
                Boolean override = frame.thresholdFlags().get(mechanic);
                if (override != null) {
                    return override;
                }
            }
        }
        synchronized (StatureConfig.class) {
            return FLAGS.getOrDefault(mechanic, false);
        }
    }

    /** The micro edge for this entity: its frame may move it. */
    public static float microOf(LivingEntity entity) {
        Float override = frameThreshold(entity);
        if (override != null) {
            return override;
        }
        synchronized (StatureConfig.class) {
            return microThreshold;
        }
    }

    public static float macroOf(LivingEntity entity) {
        Float override = frameMacro(entity);
        if (override != null) {
            return override;
        }
        synchronized (StatureConfig.class) {
            return macroThreshold;
        }
    }

    /** Below the micro edge on the uniform scale. */
    public static boolean isMicro(LivingEntity entity) {
        return ScaleService.stateOf(entity).scale() < microOf(entity);
    }

    /** Above the macro edge on the uniform scale. */
    public static boolean isMacro(LivingEntity entity) {
        return ScaleService.stateOf(entity).scale() > macroOf(entity);
    }

    private static Float frameThreshold(LivingEntity entity) {
        Identifier frameId = FrameService.frameOf(entity);
        if (frameId == null) {
            return null;
        }
        FrameFile frame = StatureFrames.file(frameId);
        return frame == null ? null : frame.thresholdMicro();
    }

    private static Float frameMacro(LivingEntity entity) {
        Identifier frameId = FrameService.frameOf(entity);
        if (frameId == null) {
            return null;
        }
        FrameFile frame = StatureFrames.file(frameId);
        return frame == null ? null : frame.thresholdMacro();
    }

    private static void read() {
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOG.warn("config {} is not an object; keeping previous values", file);
                return;
            }
            json = root.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            LOG.warn("config {} could not be read ({}); keeping previous values", file, e.toString());
            return;
        }
        apply(json, file.toString());
    }

    /**
     * Merges one parsed file over the current values. Unknown keys are reported and skipped;
     * bad values keep their previous ones. Static and game-free so tests can drive it.
     */
    static void apply(JsonObject json, String source) {
        float micro = number(json, "micro_threshold", microThreshold);
        float macro = number(json, "macro_threshold", macroThreshold);
        if (micro > 0.0F && macro > micro) {
            microThreshold = micro;
            macroThreshold = macro;
        } else {
            LOG.warn("config {} needs 0 < micro_threshold < macro_threshold; keeping previous edges", source);
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.equals("micro_threshold") || key.equals("macro_threshold")) {
                continue;
            }
            if (!FLAGS.containsKey(key)) {
                LOG.info("{}: ignoring unknown threshold key \"{}\"", source, key);
                continue;
            }
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                FLAGS.put(key, entry.getValue().getAsBoolean());
            } else {
                LOG.warn("config {} flag \"{}\" must be true or false; keeping previous value", source, key);
            }
        }
    }

    private static float number(JsonObject json, String key, float fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        float value = element.getAsFloat();
        return Float.isFinite(value) ? value : fallback;
    }

    private static void writeDefaults() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write("""
                        {
                          "comment": "Tier edges and mechanic toggles. A frame may override any of these per entity.",
                          "micro_threshold": 0.25,
                          "macro_threshold": 2.5,
                          "porous_traversal": true,
                          "surface_climbing": true,
                          "amplified_knockback": true,
                          "trampling": true,
                          "seismic_footsteps": true,
                          "doorway_collision": true
                        }
                        """);
            }
        } catch (IOException e) {
            LOG.warn("config {} could not be created: {}", file, e.toString());
        }
    }
}
