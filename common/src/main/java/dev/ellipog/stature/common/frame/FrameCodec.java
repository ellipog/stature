package dev.ellipog.stature.common.frame;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.FrameSpec;
import dev.ellipog.stature.common.StatureConfig;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Reads a frame from its JSON (plan §14.8).
 *
 * <pre>
 * {
 *   "size": [ 0.55, 1.71 ],
 *   "scale": 0.95,
 *   "eye_height": 1.62,
 *   "easing": "ease-out-cubic",
 *   "entities": [ "minecraft:zombie" ],
 *   "entity_tags": [ "minecraft:raiders" ],
 *   "comment": "what this frame is for"
 * }
 * </pre>
 *
 * <p><b>{@code size} is required</b>, with two or three numbers: width and height, and optionally
 * depth. Two numbers means "as deep as it is wide", which is what vanilla already is and what most
 * frames mean; three is the only way to ask for a frame that is deeper than it is wide. A frame with
 * no size at all is not a frame -- it is a scale, and {@code /stature set} is the tool for that.
 *
 * <p><b>{@code entities} and {@code entity_tags} are optional</b> and are what a frame uses to say
 * where it belongs: an entity type id, or an entity-type tag id written with or without its leading
 * {@code #}. Naming one makes the frame apply itself to matching entities as they appear, which is
 * the plan's precedence rule below an explicit choice.
 *
 * <p><b>Unknown fields are ignored with a note</b> rather than refused. Frames are the place the rest
 * of the plan arrives (channels, parts, regions, thresholds), so a frame written for a later Stature
 * must not break an older one: the older build applies the size it understands and says what it did
 * not. Refusing instead would make every pack a version-locked artifact.
 */
public final class FrameCodec {

    /** Fields this build understands. Anything else is reported once per file. */
    private static final Set<String> KNOWN = Set.of(
            "size", "scale", "eye_height", "easing", "entities", "entity_tags",
            "rig", "parts", "hitbox_from_parts", "regions", "thresholds",
            "first_person_arms", "comment", "$schema");

    /** Conventionally a schema pointer or prose, and never a frame's business. */
    private static final Set<String> IGNORED_SILENTLY = Set.of("comment", "$schema");

    private FrameCodec() {
    }

    /**
     * Parses one frame.
     *
     * @param source a human-readable name for where this came from, used in every message
     * @param id     the frame's id, already derived from the file's name
     * @param reader the file, closed by the caller
     * @throws FrameFormatException if the JSON is not an object, or a field is the wrong shape
     */
    public static FrameFile parse(String source, Identifier id, Reader reader) throws FrameFormatException {
        JsonObject json;
        try {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new FrameFormatException(source + ": a frame must be a JSON object, got " + kindOf(root));
            }
            json = root.getAsJsonObject();
        } catch (FrameFormatException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FrameFormatException(source + ": not readable as JSON (" + e.getMessage() + ")", e);
        }

        float[] size = size(source, json);
        float scale = optionalNumber(source, json, "scale", 1.0F);
        float eyeHeight = optionalNumber(source, json, "eye_height", FrameSpec.NO_EYE_HEIGHT);
        if (eyeHeight != FrameSpec.NO_EYE_HEIGHT && !(eyeHeight > 0.0F)) {
            // Zero and negative heights are never a camera: fail the file here, where the
            // message names the field, rather than in the spec constructor. The loader
            // logs this as an ignored frame, so the pack author finds it in the log.
            throw new FrameFormatException(source + ": \"eye_height\" must be a positive number of blocks, got "
                    + eyeHeight);
        }
        Easing easing = easing(source, json);
        List<Identifier> entities = ids(source, json, "entities");
        List<TagKey<EntityType<?>>> tags = tags(source, json, "entity_tags");
        String rig = optionalId(source, json, "rig", "stature:humanoid");
        Map<String, List<Float>> parts = parts(source, json);
        boolean hitboxFromParts = optionalFlag(source, json, "hitbox_from_parts");
        List<FrameRegion> regions = regions(source, json);
        Thresholds thresholds = thresholds(source, json);
        Boolean firstPersonArms = optionalFlagBoxed(source, json, "first_person_arms");

        StringBuilder unknown = new StringBuilder();
        for (String key : json.keySet()) {
            if (!KNOWN.contains(key) && !IGNORED_SILENTLY.contains(key)) {
                unknown.append(unknown.isEmpty() ? "" : ", ").append(key);
            }
        }
        if (!unknown.isEmpty()) {
            // Said out loud so a pack author knows why their field did nothing, rather than
            // concluding the mod is broken.
            StatureFrames.LOG.info("{}: ignoring field(s) this version does not use yet: {}", source, unknown);
        }

        try {
            return new FrameFile(id, new FrameSpec(scale, size[0], size[1], size[2], eyeHeight, easing),
                    entities, tags, rig, parts, hitboxFromParts, regions,
                    thresholds.micro(), thresholds.macro(), thresholds.flags(), firstPersonArms);
        } catch (IllegalArgumentException e) {
            throw new FrameFormatException(source + ": " + e.getMessage(), e);
        }
    }

    private static float[] size(String source, JsonObject json) throws FrameFormatException {
        JsonElement element = json.get("size");
        if (element == null) {
            throw new FrameFormatException(source + ": \"size\" is required, as [ width, height ] or "
                    + "[ width, height, depth ] in blocks");
        }
        if (!element.isJsonArray()) {
            throw new FrameFormatException(source + ": \"size\" must be an array, got " + kindOf(element));
        }

        JsonArray array = element.getAsJsonArray();
        if (array.size() != 2 && array.size() != 3) {
            throw new FrameFormatException(source + ": \"size\" must hold two or three numbers, got " + array.size());
        }

        float width = arrayNumber(source, array, 0, "width");
        float height = arrayNumber(source, array, 1, "height");
        // Two numbers means a square footprint, which is what vanilla is: the third is only there
        // for the frames that need a box deeper than it is wide.
        float depth = array.size() == 3 ? arrayNumber(source, array, 2, "depth") : width;

        return new float[] {width, height, depth};
    }

    private static float arrayNumber(String source, JsonArray array, int index, String field) throws FrameFormatException {
        JsonElement element = array.get(index);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new FrameFormatException(source + ": \"size\" " + field + " must be a number, got " + kindOf(element));
        }
        return element.getAsFloat();
    }

    private static float optionalNumber(String source, JsonObject json, String field, float fallback)
            throws FrameFormatException {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new FrameFormatException(source + ": \"" + field + "\" must be a number, got " + kindOf(element));
        }
        return element.getAsFloat();
    }

    private static Easing easing(String source, JsonObject json) throws FrameFormatException {
        JsonElement element = json.get("easing");
        if (element == null || element.isJsonNull()) {
            return FrameSpec.DEFAULT_EASING;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new FrameFormatException(source + ": \"easing\" must be a string, got " + kindOf(element));
        }

        // Both spellings, because JSON has no enum and both read naturally: ease-out-cubic and
        // ease_out_cubic name the same curve.
        String name = element.getAsString().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Easing candidate : Easing.values()) {
            if (candidate.name().equals(name) || shortName(candidate).equals(name)) {
                return candidate;
            }
        }
        throw new FrameFormatException(source + ": \"easing\" is not a curve Stature has: " + element.getAsString()
                + " (try " + String.join(", ", List.of("linear", "cubic", "elastic")) + ")");
    }

    /** An array of ids, or empty when the field is absent. */
    private static List<Identifier> ids(String source, JsonObject json, String field) throws FrameFormatException {
        List<String> raw = strings(source, json, field);
        List<Identifier> parsed = new ArrayList<>(raw.size());
        for (String value : raw) {
            Identifier id = Identifier.tryParse(value);
            if (id == null) {
                throw new FrameFormatException(source + ": \"" + field + "\" holds something that is not an id: " + value);
            }
            parsed.add(id);
        }
        return parsed;
    }

    /** An array of entity-type tag ids, tolerant of the leading {@code #} that tags are written with. */
    private static List<TagKey<EntityType<?>>> tags(String source, JsonObject json, String field)
            throws FrameFormatException {
        List<String> raw = strings(source, json, field);
        List<TagKey<EntityType<?>>> parsed = new ArrayList<>(raw.size());
        for (String value : raw) {
            String trimmed = value.startsWith("#") ? value.substring(1) : value;
            Identifier id = Identifier.tryParse(trimmed);
            if (id == null) {
                throw new FrameFormatException(source + ": \"" + field + "\" holds something that is not a tag id: " + value);
            }
            parsed.add(TagKey.create(Registries.ENTITY_TYPE, id));
        }
        return parsed;
    }

    private static List<String> strings(String source, JsonObject json, String field) throws FrameFormatException {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new FrameFormatException(source + ": \"" + field + "\" must be an array of ids, got " + kindOf(element));
        }

        JsonArray array = element.getAsJsonArray();
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new FrameFormatException(source + ": \"" + field + "\" must hold strings, got " + kindOf(entry));
            }
            values.add(entry.getAsString().trim());
        }
        return values;
    }

    /** An id string, defaulting when the field is absent. */
    private static String optionalId(String source, JsonObject json, String field, String fallback)
            throws FrameFormatException {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new FrameFormatException(source + ": \"" + field + "\" must be an id string, got " + kindOf(element));
        }
        return element.getAsString().trim();
    }

    /** A flag, off when absent. */
    private static boolean optionalFlag(String source, JsonObject json, String field)
            throws FrameFormatException {
        Boolean value = optionalFlagBoxed(source, json, field);
        return value != null && value;
    }

    /** A flag, null when absent so callers can tell unset from false. */
    private static Boolean optionalFlagBoxed(String source, JsonObject json, String field)
            throws FrameFormatException {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new FrameFormatException(source + ": \"" + field + "\" must be true or false, got " + kindOf(element));
        }
        return element.getAsBoolean();
    }

    /**
     * Part factors by group name, or empty when the field is absent. Values stay raw triplets
     * here: group resolution and factor validation happen at apply time, where the rig and the
     * entity are both in hand.
     */
    private static Map<String, List<Float>> parts(String source, JsonObject json) throws FrameFormatException {
        JsonElement element = json.get("parts");
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        if (!element.isJsonObject()) {
            throw new FrameFormatException(source + ": \"parts\" must be an object of name to [x, y, z], got "
                    + kindOf(element));
        }

        Map<String, List<Float>> parts = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonArray() || entry.getValue().getAsJsonArray().size() != 3) {
                throw new FrameFormatException(source + ": part \"" + entry.getKey()
                        + "\" must be [x, y, z], got " + kindOf(entry.getValue()));
            }
            List<Float> factors = new ArrayList<>(3);
            for (JsonElement number : entry.getValue().getAsJsonArray()) {
                if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()) {
                    throw new FrameFormatException(source + ": part \"" + entry.getKey()
                            + "\" must hold numbers, got " + kindOf(number));
                }
                factors.add(number.getAsFloat());
            }
            parts.put(entry.getKey(), List.copyOf(factors));
        }
        return Map.copyOf(parts);
    }

    /**
     * Hurt regions, or empty when the field is absent. Each entry names a rig group, a damage
     * multiplier and a tag; malformed entries fail the file, because a silently wrong weak point
     * is worse than a loud one.
     */
    private static List<FrameRegion> regions(String source, JsonObject json) throws FrameFormatException {
        JsonElement element = json.get("regions");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new FrameFormatException(source + ": \"regions\" must be a list of {part, multiplier, tag}, got "
                    + kindOf(element));
        }

        List<FrameRegion> regions = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                throw new FrameFormatException(source + ": a region must be {part, multiplier, tag}, got "
                        + kindOf(entry));
            }
            JsonObject region = entry.getAsJsonObject();
            String part = region.has("part") && region.get("part").isJsonPrimitive()
                    && region.getAsJsonPrimitive("part").isString()
                    ? region.getAsJsonPrimitive("part").getAsString().trim()
                    : null;
            String tag = region.has("tag") && region.get("tag").isJsonPrimitive()
                    && region.getAsJsonPrimitive("tag").isString()
                    ? region.getAsJsonPrimitive("tag").getAsString().trim()
                    : null;
            float multiplier = region.has("multiplier") && region.get("multiplier").isJsonPrimitive()
                    && region.getAsJsonPrimitive("multiplier").isNumber()
                    ? region.getAsJsonPrimitive("multiplier").getAsFloat()
                    : Float.NaN;
            try {
                regions.add(new FrameRegion(part, multiplier, tag));
            } catch (IllegalArgumentException e) {
                throw new FrameFormatException(source + ": " + e.getMessage(), e);
            }
        }
        return List.copyOf(regions);
    }

    /**
     * Per-frame threshold overrides: optional tier edges plus named mechanic flags. Absent
     * entirely more often than not; unknown flag keys are reported and skipped.
     */
    private static Thresholds thresholds(String source, JsonObject json) throws FrameFormatException {
        JsonElement element = json.get("thresholds");
        if (element == null || element.isJsonNull()) {
            return new Thresholds(null, null, Map.of());
        }
        if (!element.isJsonObject()) {
            throw new FrameFormatException(source + ": \"thresholds\" must be an object, got "
                    + kindOf(element));
        }
        JsonObject thresholds = element.getAsJsonObject();

        Float micro = null;
        Float macro = null;
        if (thresholds.has("micro")) {
            micro = thresholdEdge(source, thresholds, "micro");
        }
        if (thresholds.has("macro")) {
            macro = thresholdEdge(source, thresholds, "macro");
        }
        if (micro != null && macro != null && !(micro < macro)) {
            throw new FrameFormatException(source + ": \"thresholds\" needs micro below macro");
        }

        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : thresholds.entrySet()) {
            String key = entry.getKey();
            if (key.equals("micro") || key.equals("macro")) {
                continue;
            }
            if (!StatureConfig.flagKeys().contains(key)) {
                StatureFrames.LOG.info("{}: ignoring unknown threshold flag \"{}\"", source, key);
                continue;
            }
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                flags.put(key, entry.getValue().getAsBoolean());
            } else {
                throw new FrameFormatException(source + ": threshold flag \"" + key + "\" must be true or false");
            }
        }
        return new Thresholds(micro, macro, Map.copyOf(flags));
    }

    private static float thresholdEdge(String source, JsonObject json, String key) throws FrameFormatException {
        JsonElement element = json.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                || !Float.isFinite(element.getAsFloat()) || !(element.getAsFloat() > 0.0F)) {
            throw new FrameFormatException(source + ": \"thresholds\" edge \"" + key
                    + "\" must be a positive number");
        }
        return element.getAsFloat();
    }

    private record Thresholds(Float micro, Float macro, Map<String, Boolean> flags) {
    }

    private static String shortName(Easing easing) {        return switch (easing) {
            case LINEAR -> "LINEAR";
            case EASE_OUT_CUBIC -> "CUBIC";
            case ELASTIC -> "ELASTIC";
        };
    }

    private static String kindOf(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonArray()) {
            return "an array";
        }
        if (element.isJsonObject()) {
            return "an object";
        }
        return element.getAsJsonPrimitive().isNumber() ? "a number"
                : element.getAsJsonPrimitive().isString() ? "a string" : "a boolean";
    }
}
