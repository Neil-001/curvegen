package dev.curvegen.client;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.chroma.Chroma;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.ShapeSettings;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class PresetStore {
    private PresetStore() {}

    public static final class Preset {
        int version = FORMAT_VERSION;
        public String name;
        public String gen;
        public boolean pinned;
        public long saved;
        public Map<String, String> data = new LinkedHashMap<>();
        public Map<String, String> blocks;
        public Map<String, String> paint;
        private transient Path file;

        public boolean applyBlocks(ShapeSettings s, Map<dev.curvegen.core.Pieces.Family, net.minecraft.world.level.block.Block> choice) {
            if (blocks == null) return false;
            boolean dropped = false;
            BlockChoices.apply(blocks, s, choice);
            if (paint != null) {
                dev.curvegen.core.paint.Paint saved = new dev.curvegen.core.paint.Paint();
                saved.apply(paint);
                s.paint.set(saved);
                if (saved.source == dev.curvegen.core.paint.Paint.Source.GRADIENT && !Chroma.available()) {
                    s.paint.source = dev.curvegen.core.paint.Paint.Source.OFF;
                    dropped = true;
                }
            } else {
                s.paint.source = dev.curvegen.core.paint.Paint.Source.OFF;
            }
            s.fullConnects = PaintBlocks.fullConnects(s, choice);
            return dropped;
        }

        public ShapeSettings.Gen gen() {
            try { return ShapeSettings.Gen.valueOf(gen); } catch (Exception _) { return null; }
        }
    }

    private static final int FORMAT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static List<Preset> presets;

    public static Path dir() { return CurveGenClient.platform.configDir().resolve("curvegen").resolve("presets"); }

    private static List<Preset> all() {
        if (presets != null) return presets;
        presets = new ArrayList<>();
        Path dir = dir();
        if (Files.isDirectory(dir)) {
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
            } catch (IOException e) {
                dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.presets_list"), e);
                return presets;
            }
            for (Path f : files) {
                try {
                    JsonElement root = JsonParser.parseString(Files.readString(f));
                    JsonElement v = root.isJsonObject() ? root.getAsJsonObject().get("version") : null;
                    int version = v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsInt() : FORMAT_VERSION;
                    if (version > FORMAT_VERSION) {
                        dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.preset_newer", f.getFileName().toString(), version));
                        continue;
                    }
                    Preset p = GSON.fromJson(root, Preset.class);
                    if (p != null && p.name != null && p.gen() != null && p.data != null) {
                        p.file = f;
                        presets.add(p);
                    } else {
                        dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.preset_missing", f.getFileName().toString()));
                    }
                } catch (Exception e) {
                    dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.preset_read", f.getFileName().toString()), e);
                }
            }
        } else {
            long t = System.currentTimeMillis();
            List<PresetData.Example> ex = PresetData.examples();
            for (int i = 0; i < ex.size(); i++) {
                Preset p = new Preset();
                p.name = tr(ex.get(i).name()); p.gen = ShapeSettings.Gen.EQUATION.name(); p.data = new LinkedHashMap<>(ex.get(i).data());
                p.saved = t - i;
                presets.add(p);
                write(p);
            }
        }
        return presets;
    }

    public static List<Preset> list(ShapeSettings.Gen gen) {
        List<Preset> out = new ArrayList<>();
        for (Preset p : all()) if (p.gen() == gen) out.add(p);
        out.sort(Comparator.comparing((Preset p) -> !p.pinned).thenComparing(p -> -p.saved));
        return out;
    }

    public static Preset find(ShapeSettings.Gen gen, String name) {
        for (Preset p : all()) if (p.gen() == gen && p.name.equalsIgnoreCase(name.trim())) return p;
        return null;
    }

    public static String unique(ShapeSettings.Gen gen, String name) {
        if (find(gen, name) == null) return name;
        for (int i = 2; ; i++) if (find(gen, name + " (" + i + ")") == null) return name + " (" + i + ")";
    }

    public static Preset save(ShapeSettings.Gen gen, String name, ShapeSettings s, boolean withBlocks) {
        Preset p = find(gen, name);
        if (p == null) { p = new Preset(); p.gen = gen.name(); all().add(p); }
        p.name = name.trim();
        p.data = new LinkedHashMap<>(PresetData.capture(s, gen));
        p.blocks = withBlocks ? BlockChoices.capture(s) : null;
        p.paint = withBlocks ? s.paint.capture() : null;
        p.saved = System.currentTimeMillis();
        write(p);
        return p;
    }

    public static void rename(Preset p, String name) { p.name = name.trim(); write(p); }
    public static void togglePin(Preset p) { p.pinned = !p.pinned; write(p); }

    public static void delete(Preset p) {
        all().remove(p);
        if (p.file == null) return;
        try {
            Files.deleteIfExists(p.file);
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.preset_delete", p.file.getFileName().toString()), e);
        }
    }

    public static boolean matches(Preset p, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return p.name.toLowerCase(Locale.ROOT).contains(q)
                || p.data.getOrDefault("equation", "").toLowerCase(Locale.ROOT).contains(q);
    }

    private static Path fileFor(Preset p) {
        String stem = PresetData.fileStem(p.gen(), p.name);
        for (int i = 1; ; i++) {
            Path f = dir().resolve(stem + (i == 1 ? "" : "-" + i) + ".json");
            if (f.equals(p.file) || !Files.exists(f)) return f;
        }
    }

    private static void write(Preset p) {
        try {
            Files.createDirectories(dir());
            Path f = fileFor(p);
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { GSON.toJson(p, w); }
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException _) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            if (p.file != null && !p.file.equals(f)) Files.deleteIfExists(p.file);
            p.file = f;
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.preset_save", p.name), e);
        }
    }
}
