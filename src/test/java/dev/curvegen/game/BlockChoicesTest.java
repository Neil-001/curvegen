package dev.curvegen.game;

import static org.junit.jupiter.api.Assertions.*;

import dev.curvegen.client.BlockChoices;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.ShapeSettings;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Saving and restoring the Blocks tab's choices and toggles, as blocks.json keeps them. */
class BlockChoicesTest {
    @BeforeAll
    static void loadBlocks() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void choicesComeBackAsTheyWere() {
        Map<Family, Block> chosen = new EnumMap<>(BlockChoices.CHOICE);
        chosen.put(Family.FULL, Blocks.OAK_PLANKS);
        chosen.put(Family.STAIRS, Blocks.OAK_STAIRS);
        chosen.put(Family.ROD, Blocks.END_ROD);
        ShapeSettings s = new ShapeSettings();
        s.stair = false;
        s.rod = true;
        Map<String, String> saved = BlockChoices.captureChoices(s, chosen);
        assertEquals("minecraft:oak_planks", saved.get("full"));
        assertEquals("false", saved.get("stairsUsed"));
        assertFalse(saved.containsKey("shelf"), "this game has no shelf block to save");

        Map<Family, Block> restored = new EnumMap<>(BlockChoices.CHOICE);
        ShapeSettings back = new ShapeSettings();
        BlockChoices.applyChoices(saved, back, restored);
        assertEquals(chosen, restored);
        for (Family f : BlockChoices.FAMILIES) assertEquals(s.allows(f), back.allows(f), f.name());
    }

    @Test
    void aFileSavedBeforeTheTogglesKeepsThem() {
        Map<String, String> d = new HashMap<>();
        d.put("full", "minecraft:oak_planks");
        d.put("wall", "minecraft:cobblestone_wall");   // only blocks, as before the toggles were saved
        ShapeSettings s = new ShapeSettings();
        s.slab = false;
        Map<Family, Block> choice = new EnumMap<>(BlockChoices.CHOICE);
        BlockChoices.applyChoices(d, s, choice);
        assertFalse(s.slab);
        assertFalse(s.shelf, "this game has no shelves, so they stay off");
        assertFalse(s.chain);
        assertEquals(Blocks.OAK_PLANKS, choice.get(Family.FULL));
    }

    @Test
    void unknownOrUnfittingBlocksKeepTheCurrentChoice() {
        Map<Family, Block> choice = new EnumMap<>(BlockChoices.CHOICE);
        Map<Family, Block> before = new EnumMap<>(choice);
        Map<String, String> d = new HashMap<>();
        d.put("full", "othermod:missing_block");        // a mod that isn't installed
        d.put("slab", "minecraft:oak_stairs");           // a stair is no slab
        d.put("wall", "not an id");
        d.put("pane", "minecraft:iron_bars");            // fine, and taken
        d.put("teapot", "minecraft:stone");              // a piece type that doesn't exist
        BlockChoices.applyChoices(d, new ShapeSettings(), choice);
        before.put(Family.PANE, Blocks.IRON_BARS);
        assertEquals(before, choice);
    }
}
