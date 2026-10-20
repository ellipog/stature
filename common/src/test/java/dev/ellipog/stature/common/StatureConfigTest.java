package dev.ellipog.stature.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.JsonParser;

import dev.ellipog.stature.api.ScaleAxes;

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
                  "camera_shake_strength": 1.0,
                  "camera_shake_self_strength": 0.35,
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
                  "riding_offset_min": 0.0625,
                  "riding_offset_max": 16.0,
                  "explosion_scale": true,
                  "explosion_scale_strength": 1.0,
                  "explosion_scale_min": 0.25,
                  "explosion_scale_max": 3.0,
                  "drops_scale": true,
                  "drops_scale_strength": 1.0,
                  "drops_scale_min": 1.0,
                  "drops_scale_max": 3.0,
                  "presets_enabled": true,
                  "presets_builtin": true,
                  "presets_disabled": [],
                  "presets_custom": {},
                  "relative_commands_enabled": true,
                  "self_shortcuts_enabled": true,
                  "self_command_permission_level": 2
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
    void loadReadsCustomPresets(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stature.json"), """
                {
                  "presets_custom": {"kaiju": 6.0},
                  "presets_disabled": ["titan"]
                }
                """);
        StatureConfig.load(dir);
        assertEquals(6.0F, StatureConfig.preset("kaiju").scale());
        assertTrue(StatureConfig.preset("titan") == null,
                "a disabled built-in survived a file load");
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

    @Test
    void newFeatureFlagsAndTuningLoad() {
        StatureConfig.apply(JsonParser.parseString("""
                {
                  "projectile_scale": false,
                  "projectile_scale_strength": 0.5,
                  "projectile_scale_max": 2.0,
                  "drops_scale_min": 1.0,
                  "drops_scale_max": 2.0,
                  "explosion_scale_strength": 0.0
                }
                """).getAsJsonObject(), "test");
        assertEquals(Boolean.FALSE, StatureConfig.flagSnapshot().get("projectile_scale"));
        assertEquals(0.5F, StatureConfig.tuning("projectile_scale_strength"));
        assertEquals(2.0F, StatureConfig.tuning("projectile_scale_max"));
        assertEquals(2.0F, StatureConfig.tuning("drops_scale_max"));
        assertEquals(0.0F, StatureConfig.tuning("explosion_scale_strength"));
    }

    @Test
    void cameraShakeTuningLoadsAndZeros() {
        assertEquals(1.0F, StatureConfig.cameraShakeStrength());
        assertEquals(0.35F, StatureConfig.cameraShakeSelfStrength());

        StatureConfig.apply(JsonParser.parseString("""
                {
                  "camera_shake_strength": 0.0,
                  "camera_shake_self_strength": 0.0
                }
                """).getAsJsonObject(), "test");
        assertEquals(0.0F, StatureConfig.cameraShakeStrength());
        assertEquals(0.0F, StatureConfig.cameraShakeSelfStrength());
    }

    @Test
    void aBadCameraShakeNumberKeepsThePreviousOne() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"camera_shake_strength\": 9.0}").getAsJsonObject(), "test");
        assertEquals(1.0F, StatureConfig.cameraShakeStrength());
    }

    @Test
    void aBadTuningNumberKeepsThePreviousOne() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"projectile_scale_max\": 200.0}").getAsJsonObject(), "test");
        assertEquals(4.0F, StatureConfig.tuning("projectile_scale_max"));
    }

    @Test
    void aTuningMinimumAboveItsMaximumKeepsTheWholePreviousPair() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"projectile_scale_min\": 5.0}").getAsJsonObject(), "test");
        // The min is individually valid but would invert the pair, so the pair stays.
        assertEquals(0.25F, StatureConfig.tuning("projectile_scale_min"));
        assertEquals(4.0F, StatureConfig.tuning("projectile_scale_max"));
    }

    @Test
    void applySyncedRejectsAnUnknownTuningKeyWithoutApplyingAnyOfIt() {
        assertFalse(StatureConfig.applySynced(0.5F, 3.0F,
                Map.of("trampling", false),
                Map.of("projectile_scale_strength", 0.5F, "hoverboards", 1.0F)));
        assertEquals(1.0F, StatureConfig.tuning("projectile_scale_strength"));
        assertEquals(Boolean.TRUE, StatureConfig.flagSnapshot().get("trampling"));
    }

    @Test
    void applySyncedCarriesTuning() {
        assertTrue(StatureConfig.applySynced(0.5F, 3.0F,
                Map.of("drops_scale", false),
                Map.of("drops_scale_max", 2.5F)));
        assertEquals(Boolean.FALSE, StatureConfig.flagSnapshot().get("drops_scale"));
        assertEquals(2.5F, StatureConfig.tuning("drops_scale_max"));
    }

    @Test
    void aTuningMinAndMaxMayMoveTogether() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"drops_scale_min\": 2.0, \"drops_scale_max\": 4.0}")
                .getAsJsonObject(), "test");
        assertEquals(2.0F, StatureConfig.tuning("drops_scale_min"));
        assertEquals(4.0F, StatureConfig.tuning("drops_scale_max"));
    }

    @Test
    void theDefaultFileWritesEveryNewFeature() throws Exception {
        Path dir = Files.createTempDirectory("stature-config");
        try {
            StatureConfig.load(dir);
            String written = Files.readString(dir.resolve("stature.json"));
            for (String key : new String[]{
                    "projectile_scale", "item_pickup_range", "riding_offset",
                    "explosion_scale", "drops_scale", "projectile_scale_min",
                    "camera_shake_strength", "camera_shake_self_strength",
                    "drops_scale_max", "presets_enabled", "presets_builtin",
                    "presets_disabled", "presets_custom", "relative_commands_enabled",
                    "self_shortcuts_enabled", "self_command_permission_level"}) {
                assertTrue(written.contains("\"" + key + "\""), key);
            }
        } finally {
            try (var files = Files.list(dir)) {
                for (Path path : files.toList()) {
                    Files.deleteIfExists(path);
                }
            }
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void builtInPresetsAreAvailable() {
        assertEquals(9, StatureConfig.presetCount());
        assertEquals(0.25F, StatureConfig.preset("tiny").scale());
        assertEquals(1.0F, StatureConfig.preset("normal").scale());
        assertEquals(8.0F, StatureConfig.preset("titan").scale());
        assertEquals(new ScaleAxes(0.75F, 1.5F, 0.75F),
                StatureConfig.preset("tall").shape());
        assertTrue(StatureConfig.preset("missing") == null);
    }

    @Test
    void customPresetsAddAndOverrideBuiltIns() {
        StatureConfig.apply(JsonParser.parseString("""
                {
                  "presets_custom": {
                    "kaiju": 6.0,
                    "giant": {"scale": 5.0},
                    "lanky": {"shape": [0.7, 1.4, 0.7]},
                    "boss": {"scale": 3.0, "shape": [1.2, 1.1, 1.2]}
                  }
                }
                """).getAsJsonObject(), "test");

        assertEquals(12, StatureConfig.presetCount());
        assertEquals(6.0F, StatureConfig.preset("kaiju").scale());
        assertEquals(5.0F, StatureConfig.preset("giant").scale(),
                "a custom preset did not override the built-in with the same name");
        assertEquals(new ScaleAxes(0.7F, 1.4F, 0.7F),
                StatureConfig.preset("lanky").shape());
        assertEquals(3.0F, StatureConfig.preset("boss").scale());
        assertEquals(new ScaleAxes(1.2F, 1.1F, 1.2F),
                StatureConfig.preset("boss").shape());
    }

    @Test
    void disabledNamesWinOverBuiltInsAndCustomOverrides() {
        StatureConfig.apply(JsonParser.parseString("""
                {
                  "presets_custom": {"giant": 5.0},
                  "presets_disabled": ["titan", "giant"]
                }
                """).getAsJsonObject(), "test");

        assertEquals(7, StatureConfig.presetCount());
        assertTrue(StatureConfig.preset("titan") == null, "a disabled built-in is still available");
        assertTrue(StatureConfig.preset("giant") == null,
                "a disabled name survived a custom override");
    }

    @Test
    void builtInsCanBeSwitchedOffWhileCustomPresetsStay() {
        StatureConfig.apply(JsonParser.parseString("""
                {
                  "presets_builtin": false,
                  "presets_custom": {"kaiju": 6.0}
                }
                """).getAsJsonObject(), "test");

        assertEquals(1, StatureConfig.presetCount());
        assertTrue(StatureConfig.preset("giant") == null);
        assertEquals(6.0F, StatureConfig.preset("kaiju").scale());
    }

    @Test
    void badPresetEntriesAreSkipped() {
        StatureConfig.apply(JsonParser.parseString("""
                {
                  "presets_custom": {
                    "too_high": 100.0,
                    "bad_shape_length": {"shape": [1.0, 2.0]},
                    "bad_axis": {"shape": [1.0, 100.0, 1.0]},
                    "bad_type": true,
                    "empty": {},
                    "good": 2.0
                  }
                }
                """).getAsJsonObject(), "test");

        assertEquals(10, StatureConfig.presetCount());
        assertEquals(2.0F, StatureConfig.preset("good").scale());
        for (String bad : new String[]{
                "too_high", "bad_shape_length", "bad_axis", "bad_type", "empty"}) {
            assertTrue(StatureConfig.preset(bad) == null, bad + " was not skipped");
        }
    }

    @Test
    void presetAndSelfSwitchesLoad() {
        StatureConfig.apply(JsonParser.parseString("""
                {
                  "presets_enabled": false,
                  "relative_commands_enabled": false,
                  "self_shortcuts_enabled": false,
                  "self_command_permission_level": 0
                }
                """).getAsJsonObject(), "test");

        assertFalse(StatureConfig.presetsEnabled());
        assertFalse(StatureConfig.relativeCommandsEnabled());
        assertFalse(StatureConfig.selfShortcutsEnabled());
        assertEquals(0, StatureConfig.selfCommandPermissionLevel());
    }

    @Test
    void anOutOfRangeSelfPermissionKeepsThePreviousLevel() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"self_command_permission_level\": 7}").getAsJsonObject(), "test");
        assertEquals(2, StatureConfig.selfCommandPermissionLevel());
    }

    @Test
    void aMalformedOrEmptyBoxLeavesThePreviousPresetList() {
        StatureConfig.apply(JsonParser.parseString(
                "{\"presets_custom\": {\"kaiju\": 6.0}}").getAsJsonObject(), "test");
        StatureConfig.apply(JsonParser.parseString(
                "{\"presets_custom\": []}").getAsJsonObject(), "test");
        assertEquals(6.0F, StatureConfig.preset("kaiju").scale(),
                "a malformed presets_custom replaced the previous list");
    }
}
