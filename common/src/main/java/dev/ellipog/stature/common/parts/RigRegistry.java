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

    private static final RigRegistry INSTANCE = new RigRegistry();

    private final Map<String, Map<String, List<String>>> rigs = new LinkedHashMap<>();

    public static RigRegistry instance() {
        return INSTANCE;
    }

    public RigRegistry() {
        Map<String, List<String>> humanoid = new LinkedHashMap<>();
        humanoid.put("head", List.of("head", "hat"));
        humanoid.put("torso", List.of("body"));
        humanoid.put("arm.left", List.of("left_arm"));
        humanoid.put("arm.right", List.of("right_arm"));
        humanoid.put("leg.left", List.of("left_leg"));
        humanoid.put("leg.right", List.of("right_leg"));
        humanoid.put("all", List.of(""));
        this.rigs.put(DEFAULT_ID, humanoid);
    }

    /** Reads every rig in every pack, replacing what was there. Returns how many loaded. */
    public int load(ResourceManager resources) {
        Map<String, Map<String, List<String>>> loaded = new LinkedHashMap<>();
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
                Map<String, List<String>> rig = parse(source, reader);
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
        Map<String, List<String>> rig = this.rigs.get(rigId);
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
        Map<String, List<String>> rig = this.rigs.get(rigId);
        if (rig == null) {
            return List.of();
        }
        List<String> paths = rig.get(name);
        if (paths != null) {
            return paths;
        }
        return validPath(name) ? List.of(name) : List.of();
    }

    /** Whether this rig names this group (as opposed to a raw path passing through). */
    public synchronized boolean hasGroup(String rigId, String name) {
        Map<String, List<String>> rig = this.rigs.get(rigId);
        return rig != null && rig.containsKey(name);
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

    private Map<String, List<String>> parse(String source, BufferedReader reader) throws RigFormatException {
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
    static Map<String, List<String>> build(
            JsonObject json, String source, java.util.function.Function<String, Map<String, List<String>>> parents)
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

        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("groups").entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                throw new RigFormatException(source + ": group \"" + entry.getKey() + "\" must be a list of paths");
            }
            List<String> paths = new ArrayList<>();
            for (JsonElement path : entry.getValue().getAsJsonArray()) {
                if (!path.isJsonPrimitive() || !path.getAsJsonPrimitive().isString()
                        || !validPath(path.getAsString())) {
                    throw new RigFormatException(source + ": group \"" + entry.getKey()
                            + "\" holds \"" + path + "\", which is not a path");
                }
                paths.add(path.getAsString());
            }
            groups.put(entry.getKey(), List.copyOf(paths));
        }

        Map<String, List<String>> merged = new LinkedHashMap<>();
        if (parent != null) {
            Map<String, List<String>> base = parents.apply(parent);
            if (base == null) {
                LOG.warn("{}: unknown parent rig \"{}\"; the file is ignored", source, parent);
                return null;
            }
            merged.putAll(base);
        }
        merged.putAll(groups);
        return Map.copyOf(merged);
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
