package dev.ellipog.stature.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatureConfigTest {

    @BeforeEach
    void defaults() {
        StatureConfig.apply(JsonParser.parseString("""
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
                  "doorway_collision": true
                }
                """).getAsJsonObject(), "test");
    }

    @Test
    void customValuesMergeOverDefaults() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"micro_threshold\": 0.5, \"trampling\": false}").getAsJsonObject(), "test");
        assertEquals(0.5F, StatureConfig.microThreshold());
        assertEquals(2.5F, StatureConfig.macroThreshold());
    }

    @Test
    void invertedEdgesKeepTheirs() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"micro_threshold\": 5.0, \"macro_threshold\": 2.0}").getAsJsonObject(), "test");
        assertEquals(0.25F, StatureConfig.microThreshold());
        assertEquals(2.5F, StatureConfig.macroThreshold());
    }

    @Test
    void unknownKeysAreIgnored() {
        StatureConfig.apply(JsonParser.parseString("{\"hoverboards\": true}").getAsJsonObject(), "test");
        assertEquals(0.25F, StatureConfig.microThreshold());
    }

    @Test
    void loadCreatesDefaultsWhenAbsent(@TempDir Path dir) {
        StatureConfig.load(dir);
        assertTrue(Files.isRegularFile(dir.resolve("stature.json")));
        assertEquals(0.25F, StatureConfig.microThreshold());
    }

    @Test
    void loadReadsWhatIsThere(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stature.json"), "{\"macro_threshold\": 3.0, \"doorway_collision\": false}");
        StatureConfig.load(dir);
        assertEquals(3.0F, StatureConfig.macroThreshold());
    }

    @Test
    void malformedKeepsPrevious(@TempDir Path dir) throws Exception {
        StatureConfig.load(dir);
        Files.writeString(dir.resolve("stature.json"), "not json at all {{{");
        StatureConfig.load(dir);
        assertEquals(0.25F, StatureConfig.microThreshold());
        assertEquals(2.5F, StatureConfig.macroThreshold());
    }

    @Test
    void trampleKeysLoad() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"trample_max_per_scan\": 3, \"trample_drops\": false}").getAsJsonObject(), "test");
        assertEquals(3, StatureConfig.trampleMaxPerScan());
        assertFalse(StatureConfig.trampleDrops());
    }

    @Test
    void aBadTrampleCapKeepsThePreviousOne() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"trample_max_per_scan\": 0}").getAsJsonObject(), "test");
        assertEquals(8, StatureConfig.trampleMaxPerScan());
    }

    @Test
    void theCommentKeyIsAcceptedAndIgnored() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"comment\": \"old default\", \"micro_threshold\": 0.5}").getAsJsonObject(), "test");
        assertEquals(0.5F, StatureConfig.microThreshold());
    }

    @Test
    void defaultsFileDoesNotWriteTheCommentKey(@TempDir Path dir) throws Exception {
        StatureConfig.load(dir);
        String written = Files.readString(dir.resolve("stature.json"));
        assertFalse(written.contains("\"comment\""), "the default file still writes a comment key");
        assertTrue(written.contains("trample_max_per_scan"));
        assertTrue(written.contains("trample_drops"));
    }

    @Test
    void applySyncedIsAllOrNothing() {
        assertTrue(StatureConfig.applySynced(0.5F, 3.0F,
                Map.of("trampling", false)), "a valid packet was rejected");
        assertEquals(0.5F, StatureConfig.microThreshold());
        assertEquals(Boolean.FALSE, StatureConfig.flagSnapshot().get("trampling"));

        assertFalse(StatureConfig.applySynced(0.4F, 2.0F,
                        Map.of("trampling", true, "hoverboards", true)),
                "a packet with one unknown flag was applied");
        assertEquals(0.5F, StatureConfig.microThreshold(),
                "the rejected packet moved the edges");
        assertEquals(Boolean.FALSE, StatureConfig.flagSnapshot().get("trampling"),
                "the rejected packet moved a flag it also carried");
    }

    @Test
    void applySyncedRejectsInvertedEdges() {
        assertFalse(StatureConfig.applySynced(3.0F, 2.0F, Map.of()));
        assertEquals(0.25F, StatureConfig.microThreshold());
        assertEquals(2.5F, StatureConfig.macroThreshold());
    }
}
