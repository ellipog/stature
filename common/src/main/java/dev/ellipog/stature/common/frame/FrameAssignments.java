package dev.ellipog.stature.common.frame;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.Identifier;

/**
 * Resolves the plan's frame precedence without touching the game.
 *
 * <p>The rule, most specific first, is explicit choice, then entity tag, then entity type, then the
 * vanilla default. This class only sees ids, so the rule itself can be unit tested; the
 * game-specific work of turning an entity into those ids stays in {@link StatureFrames}.
 */
public final class FrameAssignments {

    /** A frame's claim, stripped of everything but the two lists the rule reads. */
    public record Rule(Identifier frame, List<Identifier> entities, List<Identifier> tags) {
        public Rule {
            Objects.requireNonNull(frame, "frame");
            entities = List.copyOf(entities);
            tags = List.copyOf(tags);
        }

        /** The same claim read from a parsed frame file. */
        public static Rule from(FrameFile file) {
            return new Rule(
                    file.id(),
                    file.entities(),
                    file.entityTags().stream().map(tag -> tag.location()).toList());
        }
    }

    /** An automatic winner and the level that chose it. */
    public record Selection(Identifier frame, FrameSource source) {
        public Selection {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(source, "source");
            if (source != FrameSource.TAG && source != FrameSource.TYPE) {
                throw new IllegalArgumentException("automatic selections are TAG or TYPE, not " + source);
            }
        }
    }

    /** Deterministic lookup tables plus the duplicate reports that built them. */
    public record Index(Map<Identifier, Identifier> byEntityType, Map<Identifier, Identifier> byTag, List<String> warnings) {
        public Index {
            byEntityType = Map.copyOf(byEntityType);
            byTag = Map.copyOf(byTag);
            warnings = List.copyOf(warnings);
        }

        /** What an empty registry resolves: nothing, with nothing to report. */
        public static Index empty() {
            return new Index(Map.of(), Map.of(), List.of());
        }
    }

    private FrameAssignments() {
    }

    /**
     * Builds the lookup tables from frame rules.
     *
     * <p>Rules are read in frame-id order so the winner is stable: the first claim for a type or tag
     * wins, and every later claim is reported rather than silently replacing it.
     */
    public static Index index(Collection<Rule> rules) {
        List<Rule> ordered = new ArrayList<>(rules);
        ordered.sort(Comparator.comparing(rule -> rule.frame().toString()));

        Map<Identifier, Identifier> byEntityType = new LinkedHashMap<>();
        Map<Identifier, Identifier> byTag = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (Rule rule : ordered) {
            for (Identifier entityType : rule.entities()) {
                Identifier winner = byEntityType.putIfAbsent(entityType, rule.frame());
                if (winner != null && !winner.equals(rule.frame())) {
                    warnings.add("frame " + rule.frame() + " and " + winner + " both claim entity "
                            + entityType + "; using " + winner);
                }
            }
            for (Identifier tag : rule.tags()) {
                Identifier winner = byTag.putIfAbsent(tag, rule.frame());
                if (winner != null && !winner.equals(rule.frame())) {
                    warnings.add("frame " + rule.frame() + " and " + winner + " both claim tag "
                            + tag + "; using " + winner);
                }
            }
        }

        return new Index(byEntityType, byTag, warnings);
    }

    /**
     * Chooses the automatic winner for one entity type and its tags.
     *
     * <p>Tags outrank an exact type here, which is the plan's order rather than the usual
     * most-specific-first intuition. Among tag matches, the smallest frame id wins so the answer does
     * not depend on pack load order or on which tag was read first.
     */
    public static Optional<Selection> automatic(
            Identifier entityType, Set<Identifier> entityTags, Index index) {
        List<Identifier> tagWinners = entityTags.stream()
                .map(index.byTag()::get)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
        if (!tagWinners.isEmpty()) {
            return Optional.of(new Selection(tagWinners.get(0), FrameSource.TAG));
        }

        if (entityType != null) {
            Identifier winner = index.byEntityType().get(entityType);
            if (winner != null) {
                return Optional.of(new Selection(winner, FrameSource.TYPE));
            }
        }

        return Optional.empty();
    }
}
