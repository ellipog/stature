package dev.ellipog.stature.common.parts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class RigRegistryTest {

    private static Map<String, List<String>> build(String json) throws Exception {
        return RigRegistry.build(
                JsonParser.parseString(json).getAsJsonObject(), "test", name -> null);
    }

    @Test
    void theDefaultHumanoidNamesAnatomy() {
        RigRegistry registry = new RigRegistry();
        assertEquals(List.of("head", "hat"), registry.resolve("stature:humanoid", "head"));
        assertEquals(List.of("left_arm"), registry.resolve("stature:humanoid", "arm.left"));
        assertEquals(List.of(""), registry.resolve("stature:humanoid", "all"));
        assertTrue(registry.hasGroup("stature:humanoid", "torso"));
        assertFalse(registry.hasGroup("stature:humanoid", "wing.left"));
    }

    @Test
    void rawPathsPassThroughWhileUnknownRigsResolveNothing() {
        RigRegistry registry = new RigRegistry();
        assertEquals(List.of("body.left_arm"), registry.resolve("stature:humanoid", "body.left_arm"));
        assertTrue(registry.resolve("pack:missing", "head").isEmpty());
    }

    @Test
    void groupsMergeOverTheParent() throws Exception {
        Map<String, List<String>> child = RigRegistry.build(
                JsonParser.parseString("{\"parent\": \"stature:humanoid\","
                        + " \"groups\": {\"head\": [\"skull\"], \"wing.left\": [\"left_wing\"]}}").getAsJsonObject(),
                "test",
                name -> "stature:humanoid".equals(name)
                        ? Map.of("head", List.of("head"), "torso", List.of("body"))
                        : null);
        assertEquals(List.of("skull"), child.get("head"));
        assertEquals(List.of("body"), child.get("torso"));
        assertEquals(List.of("left_wing"), child.get("wing.left"));
    }

    @Test
    void anUnknownParentIsReportedAsAbsent() throws Exception {
        assertNull(RigRegistry.build(
                JsonParser.parseString("{\"parent\": \"pack:missing\", \"groups\": {}}").getAsJsonObject(),
                "test",
                name -> null));
    }

    @Test
    void malformedFilesAreRejected() {
        assertThrows(Exception.class, () -> build("{\"groups\": {\"head\": \"skull\"}}"));
        assertThrows(Exception.class, () -> build("{\"groups\": {\"head\": [\"a..b\"]}}"));
        assertThrows(Exception.class, () -> build("{\"groups\": [\"head\"]}"));
        assertThrows(Exception.class, () -> build("[\"head\"]"));
    }

    @Test
    void pathSyntax() {
        assertTrue(RigRegistry.validPath(""));
        assertTrue(RigRegistry.validPath("head"));
        assertTrue(RigRegistry.validPath("body.left_arm"));
        assertFalse(RigRegistry.validPath(".head"));
        assertFalse(RigRegistry.validPath("head."));
        assertFalse(RigRegistry.validPath("a..b"));
        assertFalse(RigRegistry.validPath(null));
    }
}
