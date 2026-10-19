package dev.ellipog.stature.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
