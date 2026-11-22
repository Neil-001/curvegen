package dev.curvegen.client.chroma;

import dev.chromastudio.api.BlockMatcher;
import dev.chromastudio.api.ChromaStudioApi;
import dev.chromastudio.api.SavedGradient;
import dev.chromastudio.core.palette.Palette;
import dev.chromastudio.core.palette.PaletteEntry;
import dev.curvegen.client.MixSources;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.Gradient;
import dev.curvegen.core.paint.Mix;
import dev.curvegen.core.paint.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class ChromaBridge implements Chroma.Hooks {
    private ChromaBridge() {}

    static boolean supported() {
        try {
            return ChromaStudioApi.apiVersion() >= 2;
        } catch (NoSuchMethodError _) {
            return false;
        }
    }

    static Chroma.Hooks install() {
        MixSources.palettes = ChromaBridge::palettes;
        return new ChromaBridge();
    }


    @Override
    public List<Gradient> gradients() {
        List<Gradient> out = new ArrayList<>();
        for (SavedGradient g : ChromaStudioApi.savedGradients()) {
            List<Gradient.Stop> stops = new ArrayList<>();
            for (SavedGradient.Stop s : g.stops()) stops.add(new Gradient.Stop(s.rgb(), s.blockId()));
            List<Gradient.Step> steps = new ArrayList<>();
            for (SavedGradient.Step s : g.blocks()) steps.add(new Gradient.Step(s.idealRgb(), s.blockId(), s.chosenByHand()));
            try {
                out.add(new Gradient(g.name(), stops, steps));
            } catch (IllegalArgumentException _) {
            }
        }
        return out;
    }

    private static List<Mix> palettes() {
        List<Mix> out = new ArrayList<>();
        for (Palette p : ChromaStudioApi.palettes()) {
            Map<Pattern.Cell, Integer> weights = new LinkedHashMap<>();
            for (PaletteEntry e : p.entries) {
                Pattern.Cell cell = Pattern.Cell.parse(e.idWithProperties());
                if (cell == null || cell.state() == null || ChromaStudioApi.blockState(e) == null) continue;
                weights.merge(cell, e.weight(), Integer::sum);
            }
            if (weights.isEmpty()) continue;
            int[] w = weights.values().stream().mapToInt(Integer::intValue).toArray();
            out.add(new Mix(p.name == null ? "" : p.name, new ArrayList<>(weights.keySet()), w));
        }
        return out;
    }

    @Override
    public boolean openGradientTab(String name) { return ChromaStudioApi.openGradientTab(name); }


    private static final Map<Boolean, Map<List<String>, BlockMatcher>> MADE = new HashMap<>();
    private static int madeFor = Integer.MIN_VALUE;
    private static Catalog.Matcher last;
    private static Object lastFor;

    @Override
    public int matcherVersion() { return ChromaStudioApi.indexReady() ? ChromaStudioApi.indexVersion() : -1; }

    @Override
    public Catalog.Matcher matcher(boolean top, Map<Family, List<String>> allowed) {
        if (!ChromaStudioApi.indexReady()) return null;
        int version = ChromaStudioApi.indexVersion();
        if (version != madeFor || MADE.values().stream().mapToInt(Map::size).sum() > 64) { MADE.clear(); madeFor = version; last = null; }
        Object key = List.of(top, allowed);
        if (last != null && key.equals(lastFor)) return last;
        Map<List<String>, BlockMatcher> made = MADE.computeIfAbsent(top, _ -> new HashMap<>());
        Direction face = top ? Direction.UP : Direction.NORTH;
        Map<List<String>, BlockMatcher> whole = new IdentityHashMap<>();
        Map<String, BlockState> states = new HashMap<>();
        Map<String, BlockMatcher> holder = new HashMap<>();
        for (List<String> ids : allowed.values()) {
            BlockMatcher m = made.get(ids);
            List<BlockState> list = new ArrayList<>();
            for (String id : ids) {
                Block b = PaintBlocks.block(id);
                if (b == null) continue;
                states.put(id, b.defaultBlockState());
                list.add(b.defaultBlockState());
            }
            if (m == null) made.put(List.copyOf(ids), m = ChromaStudioApi.matcher(list, face, null));
            whole.put(ids, m);
            for (String id : ids) if (states.containsKey(id) && !holder.containsKey(id) && m.blocks().contains(states.get(id))) holder.put(id, m);
        }
        Map<List<String>, BlockMatcher> byValue = new HashMap<>(whole);
        Map<BlockState, String> ids = new HashMap<>();
        for (var e : states.entrySet()) ids.putIfAbsent(e.getValue(), e.getKey());
        lastFor = key;
        return last = (rgb, list) -> {
            BlockMatcher m = whole.get(list);
            if (m == null) m = byValue.get(list);
            if (m != null) {
                BlockState best = m.closest(rgb);
                return best == null ? null : ids.get(best);
            }
            String best = null;
            double bd = Double.MAX_VALUE;
            for (String id : list) {
                BlockMatcher h = holder.get(id);
                if (h == null) continue;
                double d = h.distance(rgb, states.get(id));
                if (d < bd) { bd = d; best = id; }
            }
            return best;
        };
    }
}
