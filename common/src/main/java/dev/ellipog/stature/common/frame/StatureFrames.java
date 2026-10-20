package dev.ellipog.stature.common.frame;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.ellipog.stature.api.FrameSpec;
import dev.ellipog.stature.api.Stature;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every frame any datapack declares (plan §14.8).
 *
 * <p>Frames are data, read from {@code data/<namespace>/stature/frames/<name>.json} in every pack on
 * the server, and Stature ships none of its own: a name is whatever the pack called it, and nothing
 * here knows what a kin is. The whole registry is replaced on load rather than merged, so removing a
 * file removes a frame instead of leaving the previous one behind.
 *
 * <p>Loaded when the server starts and by {@code /stature reload}. A broken file is reported and
 * skipped -- one bad frame must not cost the rest of the pack -- and the count of what was ignored is
 * part of the log line, so "my frame is not there" has an answer in the log rather than in a
 * debugger.
 */
public final class StatureFrames {

    static final Logger LOG = LoggerFactory.getLogger("stature");

    /** Where frames live inside a data pack. */
    public static final String DIRECTORY = Stature.MOD_ID + "/frames";

    private static final String SUFFIX = ".json";

    /** Swapped whole on load, so a reader never sees a half-loaded registry. */
    private record Registry(Map<Identifier, FrameFile> files, FrameAssignments.Index assignments) {
    }

    private static volatile Registry registry =
            new Registry(Map.of(), FrameAssignments.index(List.of()));

    private StatureFrames() {
    }

    /**
     * Reads every frame in every pack, replacing what was there.
     *
     * @return how many frames loaded, which is what the command and the log both report
     */
    public static int load(ResourceManager resources) {
        Map<Identifier, FrameFile> loaded = new LinkedHashMap<>();
        int ignored = 0;

        Map<Identifier, Resource> files = resources.listResources(
                DIRECTORY, path -> path.getPath().endsWith(SUFFIX));

        for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
            Identifier id = frameId(entry.getKey());
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                loaded.put(id, FrameCodec.parse(source(entry.getKey()), id, reader));
            } catch (FrameFormatException e) {
                ignored++;
                LOG.warn("frame ignored: {}", e.getMessage());
            } catch (IOException e) {
                ignored++;
                LOG.warn("frame {} could not be read: {}", id, e.toString());
            }
        }

        FrameAssignments.Index assignments = FrameAssignments.index(
                loaded.values().stream().map(FrameAssignments.Rule::from).toList());
        for (String warning : assignments.warnings()) {
            LOG.warn("frame assignment ignored: {}", warning);
        }

        registry = new Registry(Map.copyOf(loaded), assignments);
        LOG.info("Loaded {} frame(s){}", loaded.size(), ignored == 0 ? "" : " (" + ignored + " ignored)");
        return loaded.size();
    }

    /** Forgets everything, which is what a server that has not loaded yet should see. */
    public static void clear() {
        registry = new Registry(Map.of(), FrameAssignments.index(List.of()));
    }

    /** The frame file with this name, or null. */
    public static FrameFile file(Identifier id) {
        return registry.files().get(id);
    }

    /** The frame with this name, or null. */
    public static FrameSpec get(Identifier id) {
        FrameFile file = file(id);
        return file == null ? null : file.spec();
    }

    /** Every loaded frame id, sorted, for tab-completion and {@code frame list}. */
    public static Set<Identifier> ids() {
        return registry.files().keySet();
    }

    public static int size() {
        return registry.files().size();
    }

    /**
     * Chooses the pack-declared winner for one entity type.
     *
     * <p>Tags outrank an exact type here, which is the plan's order rather than the usual
     * most-specific-first intuition. An absent type id matches nothing: it is a value vanilla could
     * not look up, not a wildcard.
     */
    public static Optional<FrameAssignments.Selection> declaredFor(EntityType<?> entityType) {
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        Set<Identifier> tagIds = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entityType).tags()
                .map(TagKey::location).collect(Collectors.toUnmodifiableSet());
        return FrameAssignments.automatic(typeId, tagIds, registry.assignments());
    }

    /**
     * {@code mypack:stature/frames/slender.json} becomes {@code mypack:slender}.
     *
     * <p>The file name is the frame's name, so a pack's own namespace is visible in the id it hands
     * around and two packs cannot collide.
     */
    private static Identifier frameId(Identifier file) {
        String path = file.getPath();
        String name = path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length());
        return Identifier.fromNamespaceAndPath(file.getNamespace(), name);
    }

    /** How a file is named in a log line: the pack path, which is what the author can go and look at. */
    private static String source(Identifier file) {
        return "data/" + file.getNamespace() + "/" + file.getPath();
    }
}
