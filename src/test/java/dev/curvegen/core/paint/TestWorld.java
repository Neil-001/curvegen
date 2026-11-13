package dev.curvegen.core.paint;

import dev.curvegen.core.Pieces.Family;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A small made-up set of blocks for painting tests, so they don't need the game. */
final class TestWorld {
    private TestWorld() {}

    static String m(String id) { return "minecraft:" + id; }

    static Catalog catalog() { return catalog(Map.of()); }

    /** The catalog, with any of the Blocks tab's choices replaced. */
    static Catalog catalog(Map<Family, String> choices) {
        Map<Family, List<String>> allowed = new EnumMap<>(Family.class);
        Map<String, Integer> colours = new HashMap<>();
        Map<String, String> material = new HashMap<>();
        Map<String, Map<Family, String>> variants = new HashMap<>();
        // material, colour, then its pieces by family
        Object[][] mats = {
                {"stone_bricks", 0x7a7a7a, Family.SLAB, "stone_brick_slab", Family.STAIRS, "stone_brick_stairs", Family.WALL, "stone_brick_wall"},
                {"mossy_stone_bricks", 0x737d63, Family.SLAB, "mossy_stone_brick_slab", Family.STAIRS, "mossy_stone_brick_stairs", Family.WALL, "mossy_stone_brick_wall"},
                {"cracked_stone_bricks", 0x767676},
                {"oak_planks", 0xa2834f, Family.SLAB, "oak_slab", Family.STAIRS, "oak_stairs", Family.FENCE, "oak_fence", Family.TRAPDOOR, "oak_trapdoor"},
                {"spruce_planks", 0x725430, Family.SLAB, "spruce_slab", Family.STAIRS, "spruce_stairs", Family.FENCE, "spruce_fence"},
                {"nether_bricks", 0x2c161a, Family.SLAB, "nether_brick_slab", Family.STAIRS, "nether_brick_stairs", Family.FENCE, "nether_brick_fence"},
                {"red_sandstone", 0xb5621f, Family.SLAB, "red_sandstone_slab", Family.STAIRS, "red_sandstone_stairs"},
                {"white_wool", 0xe9ecec}, {"red_wool", 0xa12722}, {"black_concrete", 0x080a0f}, {"white_concrete", 0xcfd5d6},
                {"gray_concrete", 0x36393d}, {"yellow_concrete", 0xf1af15}, {"oak_leaves", 0x4c7a2a}, {"oak_log", 0x6d5532},
                {"quartz_block", 0xebe5de, Family.SLAB, "quartz_slab", Family.STAIRS, "quartz_stairs"},
        };
        for (Family f : Family.values()) allowed.put(f, new ArrayList<>());
        for (Object[] mat : mats) {
            String base = m((String) mat[0]);
            int rgb = (Integer) mat[1];
            allowed.get(Family.FULL).add(base);
            colours.put(base, rgb);
            material.put(base, base);
            Map<Family, String> v = new EnumMap<>(Family.class);
            v.put(Family.FULL, base);
            for (int k = 2; k < mat.length; k += 2) {
                Family f = (Family) mat[k];
                String id = m((String) mat[k + 1]);
                allowed.get(f).add(id);
                colours.put(id, rgb);
                material.put(id, base);
                v.put(f, id);
            }
            variants.put(base, v);
        }
        // Sand falls, so the Blocks tab never offers it, but a pattern may still name it.
        colours.put(m("sand"), 0xdbd3a0);
        allowed.get(Family.PANE).add(m("glass_pane"));
        colours.put(m("glass_pane"), 0xc0e0e8);
        Map<Family, String> choice = new EnumMap<>(Family.class);
        choice.put(Family.FULL, m("stone_bricks")); choice.put(Family.SLAB, m("stone_brick_slab")); choice.put(Family.STAIRS, m("stone_brick_stairs"));
        choice.put(Family.TRAPDOOR, m("oak_trapdoor")); choice.put(Family.FENCE, m("spruce_fence")); choice.put(Family.PANE, m("glass_pane"));
        choice.put(Family.WALL, m("stone_brick_wall"));
        choice.putAll(choices);
        Set<String> noConnect = new HashSet<>(Set.of(m("oak_leaves")));
        Set<String> wooden = Set.of(m("oak_fence"), m("spruce_fence"));
        return new Catalog(allowed, colours, material, variants, noConnect, wooden, choice, Catalog.lab(colours));
    }
}
