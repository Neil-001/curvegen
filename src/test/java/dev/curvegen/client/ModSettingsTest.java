package dev.curvegen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModSettingsTest {
    @AfterEach
    void restore() { ModSettings.reset(); }

    private static void assertDefaults() {
        assertEquals(3.0, ModSettings.holdSeconds);
        assertEquals(0.3, ModSettings.barDelaySeconds);
        assertFalse(ModSettings.radialToggle);
        assertFalse(ModSettings.radialRound, "the stretched ring is the default");
        assertEquals(List.of(), ModSettings.radialOrder);
        assertEquals(0.25, ModSettings.handleSize);
        assertEquals(0.4, ModSettings.pickRadius);
        assertEquals(0.45, ModSettings.hologramOpacity);
        assertEquals(30000, ModSettings.hologramBlockLimit);
        assertFalse(ModSettings.invertDragScroll);
        assertTrue(ModSettings.showKeyHints);
        assertTrue(ModSettings.showShapeInfo);
    }

    @Test
    void startsWithTheDefaults() { assertDefaults(); }

    @Test
    void everySettingSurvivesSavingAndLoading(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("curvegen").resolve("settings.json");
        ModSettings.holdSeconds = 1.5;
        ModSettings.barDelaySeconds = 0;
        ModSettings.radialToggle = true;
        ModSettings.radialRound = true;
        ModSettings.radialOrder = new ArrayList<>(List.of("place", "blocks"));
        ModSettings.handleSize = 0.5;
        ModSettings.pickRadius = 1.25;
        ModSettings.hologramOpacity = 0.8;
        ModSettings.hologramBlockLimit = 5000;
        ModSettings.invertDragScroll = true;
        ModSettings.showKeyHints = false;
        ModSettings.showShapeInfo = false;
        ModSettings.save(file);
        try (var files = Files.list(file.getParent())) {
            assertEquals(List.of(file), files.toList(), "the temporary file is gone");
        }

        ModSettings.reset();
        ModSettings.load(file);
        assertEquals(1.5, ModSettings.holdSeconds);
        assertEquals(0.0, ModSettings.barDelaySeconds);
        assertTrue(ModSettings.radialToggle);
        assertTrue(ModSettings.radialRound);
        assertEquals(List.of("place", "blocks"), ModSettings.radialOrder);
        assertEquals(0.5, ModSettings.handleSize);
        assertEquals(1.25, ModSettings.pickRadius);
        assertEquals(0.8, ModSettings.hologramOpacity);
        assertEquals(5000, ModSettings.hologramBlockLimit);
        assertTrue(ModSettings.invertDragScroll);
        assertFalse(ModSettings.showKeyHints);
        assertFalse(ModSettings.showShapeInfo);
    }

    @Test
    void aMissingFileLeavesTheDefaults(@TempDir Path dir) {
        ModSettings.holdSeconds = 7;
        ModSettings.load(dir.resolve("settings.json"));
        assertDefaults();
    }

    @Test
    void badValuesFallBackOneByOne() {
        assertTrue(ModSettings.read("""
                {"holdSeconds": "long", "barDelaySeconds": -1, "radialToggle": 1, "radialRound": "yes", "radialOrder": ["a", 3, "a", null, "b"],
                 "handleSize": 50, "pickRadius": 0.75, "hologramOpacity": null, "hologramBlockLimit": 12.5,
                 "invertDragScroll": true, "showKeyHints": "no", "showShapeInfo": false, "somethingNewer": {"x": 1}}"""));
        assertEquals(3.0, ModSettings.holdSeconds);
        assertEquals(0.3, ModSettings.barDelaySeconds);
        assertFalse(ModSettings.radialToggle);
        assertFalse(ModSettings.radialRound);
        assertEquals(List.of("a", "b"), ModSettings.radialOrder);
        assertEquals(0.25, ModSettings.handleSize);
        assertEquals(0.75, ModSettings.pickRadius);
        assertEquals(0.45, ModSettings.hologramOpacity);
        assertEquals(30000, ModSettings.hologramBlockLimit);
        assertTrue(ModSettings.invertDragScroll);
        assertTrue(ModSettings.showKeyHints);
        assertFalse(ModSettings.showShapeInfo);
    }

    @Test
    void anOrderThatIsNotAListIsEmpty() {
        assertTrue(ModSettings.read("{\"radialOrder\": \"place\", \"hologramBlockLimit\": 0}"));
        assertEquals(List.of(), ModSettings.radialOrder);
        assertEquals(0, ModSettings.hologramBlockLimit);
    }

    @Test
    void textThatIsNotASettingsObjectLeavesTheDefaults(@TempDir Path dir) throws Exception {
        ModSettings.holdSeconds = 7;
        assertFalse(ModSettings.read("{\"holdSeconds\": 2"));
        assertDefaults();
        assertFalse(ModSettings.read("[1, 2]"));
        assertFalse(ModSettings.read(""));
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "not json {");
        ModSettings.pickRadius = 1;
        ModSettings.load(file);
        assertDefaults();
    }

    @Test
    void theOrderListCanBeChangedAfterLoading() {
        ModSettings.read("{\"radialOrder\": [\"a\"]}");
        ModSettings.radialOrder.add("b");
        assertEquals(List.of("a", "b"), ModSettings.radialOrder);
    }
}
