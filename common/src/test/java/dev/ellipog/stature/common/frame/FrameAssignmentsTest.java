package dev.ellipog.stature.common.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

class FrameAssignmentsTest {

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static FrameAssignments.Rule rule(String frame, List<String> entities, List<String> tags) {
        return new FrameAssignments.Rule(
                Identifier.parse(frame),
                entities.stream().map(value -> Identifier.parse(value)).toList(),
                tags.stream().map(value -> Identifier.parse(value)).toList());
    }

    @Test
    void aTagBeatsAnExactType() {
        FrameAssignments.Index index = FrameAssignments.index(List.of(
                rule("pack:type", List.of("minecraft:cow"), List.of()),
                rule("pack:tag", List.of(), List.of("pack:herd"))));

        var winner = FrameAssignments.automatic(
                id("minecraft", "cow"), Set.of(id("pack", "herd")), index);

        assertTrue(winner.isPresent());
        assertEquals(id("pack", "tag"), winner.orElseThrow().frame());
        assertEquals(FrameSource.TAG, winner.orElseThrow().source());
    }

    @Test
    void anExactTypeBeatsTheVanillaDefault() {
        FrameAssignments.Index index = FrameAssignments.index(List.of(
                rule("pack:coblyn", List.of("minecraft:bat"), List.of())));

        var winner = FrameAssignments.automatic(id("minecraft", "bat"), Set.of(), index);

        assertTrue(winner.isPresent());
        assertEquals(id("pack", "coblyn"), winner.orElseThrow().frame());
        assertEquals(FrameSource.TYPE, winner.orElseThrow().source());
    }

    @Test
    void nothingMatchingMeansVanilla() {
        FrameAssignments.Index index = FrameAssignments.index(List.of(
                rule("pack:coblyn", List.of("minecraft:bat"), List.of())));

        assertTrue(FrameAssignments.automatic(id("minecraft", "cow"), Set.of(), index).isEmpty());
    }

    @Test
    void theSmallestFrameIdWinsBetweenTwoTags() {
        FrameAssignments.Index index = FrameAssignments.index(List.of(
                rule("pack:zebra", List.of(), List.of("pack:striped")),
                rule("pack:apple", List.of(), List.of("pack:edible"))));

        var winner = FrameAssignments.automatic(
                id("minecraft", "cow"), Set.of(id("pack", "striped"), id("pack", "edible")), index);

        assertTrue(winner.isPresent());
        assertEquals(id("pack", "apple"), winner.orElseThrow().frame());
    }

    @Test
    void aDuplicateTypeClaimIsReportedAndTheFirstFrameWins() {
        FrameAssignments.Index index = FrameAssignments.index(List.of(
                rule("pack:second", List.of("minecraft:bat"), List.of()),
                rule("pack:first", List.of("minecraft:bat"), List.of())));

        assertEquals(Map.of(id("minecraft", "bat"), id("pack", "first")), index.byEntityType());
        assertEquals(1, index.warnings().size());
        assertTrue(index.warnings().get(0).contains("minecraft:bat"));
    }
}
