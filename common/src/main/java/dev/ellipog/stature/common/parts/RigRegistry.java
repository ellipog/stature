package dev.ellipog.stature.common.parts;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Named part groups and the model paths they cover (plan §14.7).
 *
 * <p>A rig says {@code arm.left} means {@code left_arm} so packs write anatomy, not file internals.
 * Groups resolve to dot paths server-side, where models exist only as names; the client walks
 * those paths down real trees. Anything unresolvable stays a path and warns once where it fails,
 * so a quadruped file naming arms loads anyway.
 */
public final class RigRegistry {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    private static final String DIRECTORY = "stature/rigs";

    private static final String SUFFIX = ".json";

    private static final String DEFAULT_ID = "stature:humanoid";

    /** The whole entity box, used when a rig entry names no box of its own. */
    public static final List<Float> FULL_BOX = List.of(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);

    private static final RigRegistry INSTANCE = new RigRegistry();

    private final Map<String, Map<String, RigGroup>> rigs = new LinkedHashMap<>();

    public static RigRegistry instance() {
        return INSTANCE;
    }

    public RigRegistry() {
        Map<String, RigGroup> humanoid = new LinkedHashMap<>();
        humanoid.put("head", new RigGroup(List.of("head", "hat"), List.of(0.0F, 0.72F, 0.0F, 1.0F, 1.0F, 1.0F)));
        humanoid.put("torso", new RigGroup(List.of("body"), List.of(0.2F, 0.35F, 0.2F, 0.8F, 0.72F, 0.8F)));
        humanoid.put("arm.left", new RigGroup(List.of("left_arm"), List.of(0.8F, 0.35F, 0.3F, 1.0F, 0.7F, 0.7F)));
        humanoid.put("arm.right", new RigGroup(List.of("right_arm"), List.of(0.0F, 0.35F, 0.3F, 0.2F, 0.7F, 0.7F)));
        humanoid.put("leg.left", new RigGroup(List.of("left_leg"), List.of(0.55F, 0.0F, 0.3F, 0.8F, 0.35F, 0.7F)));
        humanoid.put("leg.right", new RigGroup(List.of("right_leg"), List.of(0.2F, 0.0F, 0.3F, 0.45F, 0.35F, 0.7F)));
        humanoid.put("all", new RigGroup(List.of(""), FULL_BOX));
        this.rigs.put(DEFAULT_ID, humanoid);
    }

    /** Reads every rig in every pack, replacing what was there. Returns how many loaded. */
    public int load(ResourceManager resources) {
        Map<String, Map<String, RigGroup>> loaded = new LinkedHashMap<>();
        loaded.put(DEFAULT_ID, this.rigs.get(DEFAULT_ID));
        int ignored = 0;

        Map<Identifier, Resource> files =
                resources.listResources(DIRECTORY, path -> path.getPath().endsWith(SUFFIX));
        List<Identifier> ordered =
                files.keySet().stream().sorted().toList();
        for (Identifier file : ordered) {
            String id = file.getNamespace() + ":"
                    + file.getPath().substring(DIRECTORY.length() + 1, file.getPath().length() - SUFFIX.length());
            String source = "data/" + file.getNamespace() + "/" + file.getPath();
            if (DEFAULT_ID.equals(id)) {
                ignored++;
                LOG.warn("{}: the default humanoid rig cannot be replaced; extend it with a parent instead", source);
                continue;
            }
            try (BufferedReader reader = files.get(file).openAsReader()) {
                Map<String, RigGroup> rig = parse(source, reader);
                if (rig == null) {
                    ignored++;
                    continue;
                }
                loaded.put(id, rig);
            } catch (RigFormatException e) {
                ignored++;
                LOG.warn("rig ignored: {}", e.getMessage());
            } catch (IOException e) {
                ignored++;
                LOG.warn("rig {} could not be read: {}", id, e.toString());
            }
        }

        synchronized (this) {
            this.rigs.clear();
            this.rigs.putAll(loaded);
        }
        LOG.info("Loaded {} rig(s){}", loaded.size() - 1, ignored == 0 ? "" : " (" + ignored + " ignored)");
        return loaded.size() - 1;
    }

    /** Group names one rig knows, sorted. Empty when the rig is unknown. */
    public synchronized List<String> groups(String rigId) {
        Map<String, RigGroup> rig = this.rigs.get(rigId);
        if (rig == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(rig.keySet());
        names.sort(String::compareTo);
        return names;
    }

    /**
     * Resolves a group or raw path to model paths.
     *
     * <p>A known group yields its paths; anything else with valid path syntax passes through
     * untouched, because only the client's model knows whether it exists. Unknown rigs resolve
     * nothing.
     */
    public synchronized List<String> resolve(String rigId, String name) {
        Map<String, RigGroup> rig = this.rigs.get(rigId);
        if (rig == null) {
            return List.of();
        }
        RigGroup group = rig.get(name);
        if (group != null) {
            return group.paths();
        }
        return validPath(name) ? List.of(name) : List.of();
    }

    /** Whether this rig names this group (as opposed to a raw path passing through). */
    public synchronized boolean hasGroup(String rigId, String name) {
        Map<String, RigGroup> rig = this.rigs.get(rigId);
        return rig != null && rig.containsKey(name);
    }

    /**
     * One group's fractional box, or null when the rig or group is unknown. Callers fall back
     * to {@link #FULL_BOX}.
     */
    public synchronized List<Float> boxOf(String rigId, String group) {
        Map<String, RigGroup> rig = this.rigs.get(rigId);
        if (rig == null) {
            return null;
        }
        RigGroup entry = rig.get(group);
        return entry == null ? null : entry.box();
    }

    /** Dot-separated non-blank segments; the empty string addresses the root, null nothing. */
    public static boolean validPath(String name) {
        if (name == null) {
            return false;
        }
        if (name.isEmpty()) {
            return true;
        }
        for (String segment : name.split("\\.", -1)) {
            if (segment.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, RigGroup> parse(String source, BufferedReader reader) throws RigFormatException {
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (RuntimeException e) {
            throw new RigFormatException(source + ": not readable as JSON (" + e.getMessage() + ")", e);
        }
        if (!root.isJsonObject()) {
            throw new RigFormatException(source + ": a rig must be a JSON object");
        }
        synchronized (this) {
            return build(root.getAsJsonObject(), source, this.rigs::get);
        }
    }

    /**
     * Validates one rig file and merges it over its parent, if any.
     *
     * <p>Static and game-free apart from Gson so tests can drive it directly: the file reading
     * above is just transport. Returns null when the parent is unknown, which the caller reports
     * and skips.
     */
    static Map<String, RigGroup> build(
            JsonObject json, String source, java.util.function.Function<String, Map<String, RigGroup>> parents)
            throws RigFormatException {

        String parent = null;
        if (json.has("parent")) {
            if (!json.get("parent").isJsonPrimitive() || !json.getAsJsonPrimitive("parent").isString()) {
                throw new RigFormatException(source + ": \"parent\" must be a rig id string");
            }
            parent = json.getAsJsonPrimitive("parent").getAsString();
        }

        if (!json.has("groups") || !json.get("groups").isJsonObject()) {
            throw new RigFormatException(source + ": \"groups\" is required, as an object of name to path lists");
        }

        Map<String, RigGroup> groups = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("groups").entrySet()) {
            groups.put(entry.getKey(), group(source, entry.getKey(), entry.getValue()));
        }

        Map<String, RigGroup> merged = new LinkedHashMap<>();
        if (parent != null) {
            Map<String, RigGroup> base = parents.apply(parent);
            if (base == null) {
                LOG.warn("{}: unknown parent rig \"{}\"; the file is ignored", source, parent);
                return null;
            }
            merged.putAll(base);
        }
        merged.putAll(groups);
        return Map.copyOf(merged);
    }

    /**
     * One group entry: a bare path list, or an object with paths plus a fractional hit box.
     * A missing box means the whole entity box.
     */
    private static RigGroup group(String source, String name, JsonElement element) throws RigFormatException {
        JsonElement pathsElement = element;
        JsonElement boxElement = null;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            pathsElement = object.get("paths");
            boxElement = object.get("box");
            if (pathsElement == null) {
                throw new RigFormatException(source + ": group \"" + name + "\" needs a \"paths\" list");
            }
        }
        if (pathsElement == null || !pathsElement.isJsonArray()) {
            throw new RigFormatException(source + ": group \"" + name + "\" must be a list of paths");
        }
        List<String> paths = new ArrayList<>();
        for (JsonElement path : pathsElement.getAsJsonArray()) {
            if (!path.isJsonPrimitive() || !path.getAsJsonPrimitive().isString()
                    || !validPath(path.getAsString())) {
                throw new RigFormatException(source + ": group \"" + name
                        + "\" holds \"" + path + "\", which is not a path");
            }
            paths.add(path.getAsString());
        }
        return new RigGroup(List.copyOf(paths), box(source, name, boxElement));
    }

    private static List<Float> box(String source, String name, JsonElement element) throws RigFormatException {
        if (element == null || element.isJsonNull()) {
            return FULL_BOX;
        }
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 6) {
            throw new RigFormatException(source + ": group \"" + name
                    + "\" needs a box of six fractions [x0, y0, z0, x1, y1, z1]");
        }
        List<Float> box = new ArrayList<>(6);
        for (JsonElement number : element.getAsJsonArray()) {
            if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()
                    || !Float.isFinite(number.getAsFloat())) {
                throw new RigFormatException(source + ": group \"" + name + "\" box holds a non-number");
            }
            box.add(number.getAsFloat());
        }
        if (!(box.get(0) <= box.get(3)) || !(box.get(1) <= box.get(4)) || !(box.get(2) <= box.get(5))) {
            throw new RigFormatException(source + ": group \"" + name + "\" box minimums exceed maximums");
        }
        return List.copyOf(box);
    }

    static final class RigFormatException extends Exception {
        RigFormatException(String message) {
            super(message);
        }

        RigFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
