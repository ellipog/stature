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

import dev.ellipog.stature.common.api.StatureEvents;
import dev.ellipog.stature.common.frame.FrameFile;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.Nullable;

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

    public static final String PROJECTILE_SCALE = "projectile_scale";

    public static final String ITEM_PICKUP_RANGE = "item_pickup_range";

    public static final String RIDING_OFFSET = "riding_offset";

    public static final String EXPLOSION_SCALE = "explosion_scale";

    public static final String DROPS_SCALE = "drops_scale";

    public static final String PROJECTILE_SCALE_CHANNEL = "stature:" + PROJECTILE_SCALE;

    public static final String ITEM_PICKUP_RANGE_CHANNEL = "stature:" + ITEM_PICKUP_RANGE;

    public static final String RIDING_OFFSET_CHANNEL = "stature:" + RIDING_OFFSET;

    public static final String EXPLOSION_SCALE_CHANNEL = "stature:" + EXPLOSION_SCALE;

    public static final String DROPS_SCALE_CHANNEL = "stature:" + DROPS_SCALE;

    private static Path file;

    private static float microThreshold = 0.25F;

    private static float macroThreshold = 2.5F;

    private static int trampleMaxPerScan = 8;

    private static boolean trampleDrops = true;

    private static final Map<String, Boolean> FLAGS = new LinkedHashMap<>();

    static {
        FLAGS.put(POROUS_TRAVERSAL, true);
        FLAGS.put(SURFACE_CLIMBING, true);
        FLAGS.put(AMPLIFIED_KNOCKBACK, true);
        FLAGS.put(TRAMPLING, true);
        FLAGS.put(SEISMIC_FOOTSTEPS, true);
        FLAGS.put(DOORWAY_COLLISION, true);
        FLAGS.put(PROJECTILE_SCALE, true);
        FLAGS.put(ITEM_PICKUP_RANGE, true);
        FLAGS.put(RIDING_OFFSET, true);
        FLAGS.put(EXPLOSION_SCALE, true);
        FLAGS.put(DROPS_SCALE, true);
    }

    /**
     * Numeric tuning for the channels Stature applies itself, rather than through an attribute.
     *
     * <p>Every channel has a strength multiplier for its followed value and a min/max clamp for the
     * resolved factor. The projectile channel also has damage and speed strengths; both default to
     * a sane multiplication and can be zeroed without disabling the size inheritance.
     */
    private static final Map<String, Float> TUNING = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("projectile_scale_strength", 1.0F),
            Map.entry("projectile_scale_min", 0.25F),
            Map.entry("projectile_scale_max", 4.0F),
            Map.entry("projectile_damage_strength", 1.0F),
            Map.entry("projectile_speed_strength", 0.5F),
            Map.entry("item_pickup_range_strength", 1.0F),
            Map.entry("item_pickup_range_min", 0.25F),
            Map.entry("item_pickup_range_max", 4.0F),
            Map.entry("riding_offset_strength", 1.0F),
            Map.entry("riding_offset_min", 0.25F),
            Map.entry("riding_offset_max", 4.0F),
            Map.entry("explosion_scale_strength", 1.0F),
            Map.entry("explosion_scale_min", 0.25F),
            Map.entry("explosion_scale_max", 3.0F),
            Map.entry("drops_scale_strength", 1.0F),
            Map.entry("drops_scale_min", 1.0F),
            Map.entry("drops_scale_max", 3.0F)));

    /** Inclusive bounds for one numeric tuning key. */
    private record Bound(float min, float max) {
    }

    private static final Map<String, Bound> BOUNDS = Map.ofEntries(
            Map.entry("projectile_scale_strength", new Bound(0.0F, 4.0F)),
            Map.entry("projectile_scale_min", new Bound(0.001F, 16.0F)),
            Map.entry("projectile_scale_max", new Bound(0.001F, 16.0F)),
            Map.entry("projectile_damage_strength", new Bound(0.0F, 4.0F)),
            Map.entry("projectile_speed_strength", new Bound(0.0F, 2.0F)),
            Map.entry("item_pickup_range_strength", new Bound(0.0F, 4.0F)),
            Map.entry("item_pickup_range_min", new Bound(0.001F, 16.0F)),
            Map.entry("item_pickup_range_max", new Bound(0.001F, 16.0F)),
            Map.entry("riding_offset_strength", new Bound(0.0F, 4.0F)),
            Map.entry("riding_offset_min", new Bound(0.001F, 16.0F)),
            Map.entry("riding_offset_max", new Bound(0.001F, 16.0F)),
            Map.entry("explosion_scale_strength", new Bound(0.0F, 4.0F)),
            Map.entry("explosion_scale_min", new Bound(0.001F, 16.0F)),
            Map.entry("explosion_scale_max", new Bound(0.001F, 16.0F)),
            Map.entry("drops_scale_strength", new Bound(0.0F, 4.0F)),
            Map.entry("drops_scale_min", new Bound(0.001F, 16.0F)),
            Map.entry("drops_scale_max", new Bound(0.001F, 16.0F)));

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

    /**
     * Applies values received from the server. Client-side only.
     *
     * <p>The whole packet is validated before anything is written: a packet with one bad edge or
     * one unknown flag is rejected and the previous values stay. Half-applying a packet is how a
     * client ends up gating climbing on one edge and breaking on another.
     *
     * @return true when the packet was applied, false when it was rejected
     */
    public static synchronized boolean applySynced(
            float micro, float macro, Map<String, Boolean> flags) {
        return applySynced(micro, macro, flags, Map.of());
    }

    /**
     * Applies values received from the server, including the numeric tuning.
     *
     * <p>As with the edges and flags, the whole packet is validated before anything is written:
     * one bad tuning value rejects the packet and leaves every previous value in place.
     */
    public static synchronized boolean applySynced(
            float micro, float macro, Map<String, Boolean> flags, Map<String, Float> tuning) {
        if (!Float.isFinite(micro) || !Float.isFinite(macro) || !(micro > 0.0F) || !(macro > micro)) {
            LOG.warn("rejecting synced config: edges must satisfy 0 < micro < macro, got {} and {}",
                    micro, macro);
            return false;
        }
        if (flags == null || tuning == null) {
            LOG.warn("rejecting synced config: missing flag or tuning map");
            return false;
        }
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !FLAGS.containsKey(entry.getKey())) {
                LOG.warn("rejecting synced config: unknown threshold flag \"{}\"", entry.getKey());
                return false;
            }
        }
        Map<String, Float> nextTuning = new LinkedHashMap<>(TUNING);
        for (Map.Entry<String, Float> entry : tuning.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                LOG.warn("rejecting synced config: malformed tuning entry");
                return false;
            }
            if (!validTuning(entry.getKey(), entry.getValue())) {
                LOG.warn("rejecting synced config: bad tuning value {} for \"{}\"",
                        entry.getValue(), entry.getKey());
                return false;
            }
            nextTuning.put(entry.getKey(), entry.getValue());
        }
        if (!validTuningRanges(nextTuning)) {
            LOG.warn("rejecting synced config: a tuning minimum is above its maximum");
            return false;
        }
        microThreshold = micro;
        macroThreshold = macro;
        FLAGS.putAll(flags);
        TUNING.clear();
        TUNING.putAll(nextTuning);
        return true;
    }

    /** How many fragile blocks one trample scan may destroy. */
    public static synchronized int trampleMaxPerScan() {
        return trampleMaxPerScan;
    }

    /** Whether trampled blocks drop their items. */
    public static synchronized boolean trampleDrops() {
        return trampleDrops;
    }

    /** A numeric tuning value, or its documented default when the key is unknown. */
    public static synchronized float tuning(String key) {
        return TUNING.getOrDefault(key, 1.0F);
    }

    /** The strength multiplier for one tuned channel. */
    public static float channelStrength(String channelId) {
        return tuning(tuningKey(channelId, "_strength"));
    }

    /** The lower clamp for one tuned channel. */
    public static float channelMin(String channelId) {
        return tuning(tuningKey(channelId, "_min"));
    }

    /** The upper clamp for one tuned channel. */
    public static float channelMax(String channelId) {
        return tuning(tuningKey(channelId, "_max"));
    }

    /** Whether this channel id has a min/max/strength family in the config. */
    public static boolean isTunedChannel(String channelId) {
        return TUNING.containsKey(tuningKey(channelId, "_min"));
    }

    /** A channel id's config key: the namespace is not part of the JSON key. */
    private static String tuningKey(String channelId, String suffix) {
        String key = channelId.startsWith("stature:")
                ? channelId.substring("stature:".length())
                : channelId;
        return key + suffix;
    }

    /** The damage strength for projectiles, linear against the size factor. */
    public static float projectileDamageStrength() {
        return tuning("projectile_damage_strength");
    }

    /** The speed exponent for projectiles. */
    public static float projectileSpeedStrength() {
        return tuning("projectile_speed_strength");
    }

    /** Whether this id is one of the numeric tuning keys. */
    public static synchronized boolean isTuningKey(String key) {
        return TUNING.containsKey(key);
    }

    /** A snapshot for packets: micro, macro, then the flags. */
    public static synchronized Map<String, Boolean> flagSnapshot() {
        return Map.copyOf(FLAGS);
    }

    /** A snapshot of the numeric tuning for packets. */
    public static synchronized Map<String, Float> tuningSnapshot() {
        return Map.copyOf(TUNING);
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
        // The server's resolved answer wins on both sides: the client never sees the
        // frame registry, so without this the two sides would gate movement differently.
        ResolvedThresholds resolved = ((StatureScale) entity).stature$thresholds();
        if (resolved != null) {
            Boolean stored = resolved.flags().get(mechanic);
            if (stored != null) {
                return stored;
            }
        }
        FrameFile frame = FrameService.frameFileOf(entity);
        if (frame != null) {
            Boolean override = frame.thresholdFlags().get(mechanic);
            if (override != null) {
                return override;
            }
        }
        synchronized (StatureConfig.class) {
            return FLAGS.getOrDefault(mechanic, false);
        }
    }

    /** The micro edge for this entity: its frame may move it. */
    public static float microOf(LivingEntity entity) {
        ResolvedThresholds resolved = ((StatureScale) entity).stature$thresholds();
        if (resolved != null) {
            return resolved.micro();
        }
        Float override = frameThreshold(entity);
        if (override != null) {
            return override;
        }
        synchronized (StatureConfig.class) {
            return microThreshold;
        }
    }

    public static float macroOf(LivingEntity entity) {
        ResolvedThresholds resolved = ((StatureScale) entity).stature$thresholds();
        if (resolved != null) {
            return resolved.macro();
        }
        Float override = frameMacro(entity);
        if (override != null) {
            return override;
        }
        synchronized (StatureConfig.class) {
            return macroThreshold;
        }
    }

    /**
     * Resolves this entity's tiers and flags the way the server sends them: the frame
     * overrides unioned over the configured values.
     *
     * <p>Returns null when no frame overrides this entity, in which case the configured
     * values apply as-is and there is nothing to store or send. Server-side only: it
     * reads the frame registry, which is empty on the client.
     */
    public static @Nullable ResolvedThresholds resolveThresholds(LivingEntity entity) {
        Identifier frameId = FrameService.frameOf(entity);
        if (frameId == null) {
            return null;
        }
        FrameFile frame = FrameService.frameFileOf(entity);
        if (frame == null) {
            return null;
        }
        if (frame.thresholdMicro() == null && frame.thresholdMacro() == null
                && frame.thresholdFlags().isEmpty()) {
            return null;
        }
        float micro;
        float macro;
        Map<String, Boolean> flags = new LinkedHashMap<>();
        synchronized (StatureConfig.class) {
            micro = frame.thresholdMicro() != null ? frame.thresholdMicro() : microThreshold;
            macro = frame.thresholdMacro() != null ? frame.thresholdMacro() : macroThreshold;
            for (String key : FLAGS.keySet()) {
                Boolean override = frame.thresholdFlags().get(key);
                flags.put(key, override != null ? override : FLAGS.get(key));
            }
        }
        if (!(micro > 0.0F && macro > micro)) {
            LOG.warn("frame {} declares unusable thresholds (micro {}, macro {}); using the configured edges",
                    frameId, micro, macro);
            synchronized (StatureConfig.class) {
                micro = microThreshold;
                macro = macroThreshold;
            }
        }
        return new ResolvedThresholds(micro, macro, flags);
    }

    /**
     * Stores this entity's resolved thresholds and broadcasts them when they changed.
     *
     * <p>Change-detected, so per-tick callers stay quiet: only a new frame, an edited
     * frame, or a config reload moves anything. An entity whose frame carries no
     * overrides stores null and -- when it previously stored something -- broadcasts
     * the configured values once, so clients drop the stale answer. Server-side only.
     */
    public static void pushThresholds(LivingEntity entity) {
        pushThresholds(entity, StatureEvents.Cause.API);
    }

    /**
     * Stores this entity's resolved thresholds and reports a tier change after the store.
     *
     * <p>Internal: the frame, command and load paths pass the cause that made the thresholds move.
     */
    public static void pushThresholds(LivingEntity entity, StatureEvents.Cause cause) {
        if (entity.level().isClientSide()) {
            return;
        }
        StatureEvents.batch(() -> {
            StatureScale duck = (StatureScale) entity;
            ResolvedThresholds resolved = resolveThresholds(entity);
            if (java.util.Objects.equals(duck.stature$thresholds(), resolved)) {
                return;
            }
            StatureEvents.ThresholdTier before = StatureEvents.tierOf(entity);
            duck.stature$setThresholds(resolved);
            StatureNetworking.broadcastThresholds(entity, wireValues(entity, resolved));
            StatureEvents.fireTierChanged(entity, before, cause);
        });
    }

    /**
     * Sends this entity's thresholds to one player, storing the resolved answer first.
     *
     * <p>Unlike {@link #pushThresholds}, this always sends: a viewer who just started
     * tracking has nothing yet, so "unchanged since last time" is the wrong question.
     * Only call it for framed entities; anything else reads the config packet the
     * client already got on login. Server-side only.
     */
    public static void sendThresholds(ServerPlayer player, LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        ResolvedThresholds resolved = resolveThresholds(entity);
        if (resolved == null) {
            return;
        }
        StatureEvents.batch(() -> {
            StatureEvents.ThresholdTier before = StatureEvents.tierOf(entity);
            ((StatureScale) entity).stature$setThresholds(resolved);
            StatureNetworking.sendThresholdsTo(player, entity, resolved);
            StatureEvents.fireTierChanged(entity, before, StatureEvents.Cause.SYNC);
        });
    }

    /**
     * Stores thresholds received by a client and reports a tier change, if the new edges or flags
     * moved the entity across one.
     *
     * <p>Client-side; the payload handlers call it on the client's main thread.
     */
    public static void applyFromServer(LivingEntity entity, ResolvedThresholds resolved) {
        StatureEvents.batch(() -> {
            StatureEvents.ThresholdTier before = StatureEvents.tierOf(entity);
            ((StatureScale) entity).stature$setThresholds(resolved);
            StatureEvents.fireTierChanged(entity, before, StatureEvents.Cause.CLIENT);
        });
    }

    /**
     * Re-resolves every living entity's thresholds, broadcasting those that changed.
     *
     * <p>Runs after a frame or config reload: an edited frame can drop overrides a
     * client still holds, and new configured edges move every entity whose frame does
     * not pin that edge. Quiet when nothing changed.
     */
    public static void refreshAllThresholds(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living) {
                    ((StatureScale) living).stature$clearFrameFileCache();
                    pushThresholds(living, StatureEvents.Cause.COMMAND);
                }
            }
        }
    }

    /** The values to put on the wire: the resolved answer, or today's configured ones. */
    private static ResolvedThresholds wireValues(LivingEntity entity, @Nullable ResolvedThresholds resolved) {
        if (resolved != null) {
            return resolved;
        }
        synchronized (StatureConfig.class) {
            return new ResolvedThresholds(microThreshold, macroThreshold, Map.copyOf(FLAGS));
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
        FrameFile frame = FrameService.frameFileOf(entity);
        return frame == null ? null : frame.thresholdMicro();
    }

    private static Float frameMacro(LivingEntity entity) {
        FrameFile frame = FrameService.frameFileOf(entity);
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
    public static synchronized void apply(JsonObject json, String source) {
        float micro = number(json, "micro_threshold", microThreshold);
        float macro = number(json, "macro_threshold", macroThreshold);
        if (micro > 0.0F && macro > micro) {
            microThreshold = micro;
            macroThreshold = macro;
        } else {
            LOG.warn("config {} needs 0 < micro_threshold < macro_threshold; keeping previous edges", source);
        }
        Map<String, Float> nextTuning = new LinkedHashMap<>(TUNING);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.equals("micro_threshold") || key.equals("macro_threshold")
                    || key.equals("comment")) {
                // "comment" is the one key earlier versions wrote into their own default file.
                // It is accepted and ignored rather than reported, so an existing config does
                // not log an unknown-key line on every start.
                continue;
            }
            if (TUNING.containsKey(key)) {
                JsonElement element = entry.getValue();
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    float value = element.getAsFloat();
                    if (validTuning(key, value)) {
                        nextTuning.put(key, value);
                    } else {
                        LOG.warn("config {} tuning \"{}\" must be within its allowed range; keeping {}",
                                source, key, TUNING.get(key));
                    }
                } else {
                    LOG.warn("config {} tuning \"{}\" must be a number; keeping {}",
                            source, key, TUNING.get(key));
                }
                continue;
            }
            if (key.equals("trample_max_per_scan")) {
                if (entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    double raw = entry.getValue().getAsDouble();
                    if (raw >= 1.0 && raw == Math.rint(raw) && raw <= Integer.MAX_VALUE) {
                        trampleMaxPerScan = (int) raw;
                    } else {
                        LOG.warn("config {} needs a whole trample_max_per_scan >= 1; keeping {}",
                                source, trampleMaxPerScan);
                    }
                } else {
                    LOG.warn("config {} trample_max_per_scan must be a whole number; keeping {}",
                            source, trampleMaxPerScan);
                }
                continue;
            }
            if (key.equals("trample_drops")) {
                if (entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                    trampleDrops = entry.getValue().getAsBoolean();
                } else {
                    LOG.warn("config {} trample_drops must be true or false; keeping {}",
                            source, trampleDrops);
                }
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
        if (validTuningRanges(nextTuning)) {
            TUNING.clear();
            TUNING.putAll(nextTuning);
        } else {
            LOG.warn("config {} has a tuning minimum above its maximum; keeping the previous tuning", source);
        }
    }

    /** Whether one number is finite and within its declared bounds. */
    private static boolean validTuning(String key, float value) {
        Bound bound = BOUNDS.get(key);
        return bound != null && Float.isFinite(value)
                && value >= bound.min() && value <= bound.max();
    }

    /** Every min/max pair still satisfies min <= max. */
    private static boolean validTuningRanges(Map<String, Float> values) {
        for (Map.Entry<String, Float> entry : values.entrySet()) {
            if (!entry.getKey().endsWith("_min")) {
                continue;
            }
            String stem = entry.getKey().substring(0, entry.getKey().length() - 4);
            Float max = values.get(stem + "_max");
            if (max != null && entry.getValue() > max) {
                return false;
            }
        }
        return true;
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
                          "micro_threshold": 0.25,
                          "macro_threshold": 2.5,
                          "porous_traversal": true,
                          "surface_climbing": true,
                          "amplified_knockback": true,
                          "trampling": true,
                          "trample_max_per_scan": 8,
                          "trample_drops": true,
                          "seismic_footsteps": true,
                          "doorway_collision": true,
                          "projectile_scale": true,
                          "projectile_scale_strength": 1.0,
                          "projectile_scale_min": 0.25,
                          "projectile_scale_max": 4.0,
                          "projectile_damage_strength": 1.0,
                          "projectile_speed_strength": 0.5,
                          "item_pickup_range": true,
                          "item_pickup_range_strength": 1.0,
                          "item_pickup_range_min": 0.25,
                          "item_pickup_range_max": 4.0,
                          "riding_offset": true,
                          "riding_offset_strength": 1.0,
                          "riding_offset_min": 0.25,
                          "riding_offset_max": 4.0,
                          "explosion_scale": true,
                          "explosion_scale_strength": 1.0,
                          "explosion_scale_min": 0.25,
                          "explosion_scale_max": 3.0,
                          "drops_scale": true,
                          "drops_scale_strength": 1.0,
                          "drops_scale_min": 1.0,
                          "drops_scale_max": 3.0
                        }
                        """);
            }
        } catch (IOException e) {
            LOG.warn("config {} could not be created: {}", file, e.toString());
        }
    }
}
