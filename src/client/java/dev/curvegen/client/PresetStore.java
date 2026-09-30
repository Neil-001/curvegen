package dev.curvegen.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.ShapeSettings;
import net.fabricmc.loader.api.FabricLoader;

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

/**
 * Saved shape presets, kept in {@code .minecraft/config/curvegen/presets.json}.
 * The first time it's used, the file starts out with the old equation examples.
 */
public final class PresetStore {
    private PresetStore() {}

    public static final class Preset {
        public String name;
        public String gen;          // ELLIPSE, EQUATION or BEZIER
        public boolean pinned;
        public long saved;          // last saved or renamed, in milliseconds
        public Map<String, String> data = new LinkedHashMap<>();

        public ShapeSettings.Gen gen() {
            try { return ShapeSettings.Gen.valueOf(gen); } catch (Exception e) { return null; }
        }
    }

    /** Bumped when the file layout changes in a way older versions can't read. */
    private static final int FORMAT_VERSION = 1;

    private static final class FileFormat {
        int version = FORMAT_VERSION;
        List<Preset> presets = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static List<Preset> presets;

    public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("curvegen").resolve("presets.json"); }

    private static List<Preset> all() {
        if (presets != null) return presets;
        presets = new ArrayList<>();
        Path f = file();
        if (Files.exists(f)) {
            try {
                // Read the version before binding the rest, since a newer layout may not bind to these classes.
                JsonElement root = JsonParser.parseString(Files.readString(f));
                JsonElement v = root.isJsonObject() ? root.getAsJsonObject().get("version") : null;
                int version = v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsInt() : FORMAT_VERSION;
                if (version > FORMAT_VERSION) {
                    // Saved by a newer version of the mod. Keep it as it is instead of overwriting it.
                    Files.move(f, f.resolveSibling("presets.v" + version + ".json"), StandardCopyOption.REPLACE_EXISTING);
                    dev.curvegen.CurveGen.LOGGER.warn("presets.json is from a newer version of Curve Generator (format {}), so it was set aside", version);
                } else {
                    FileFormat ff = GSON.fromJson(root, FileFormat.class);
                    if (ff != null && ff.presets != null)
                        for (Preset p : ff.presets) if (p != null && p.name != null && p.gen() != null && p.data != null) presets.add(p);
                }
            } catch (Exception e) {
                // Keep a damaged file for the player instead of overwriting it, and start fresh.
                dev.curvegen.CurveGen.LOGGER.warn("Couldn't read presets.json, so it was set aside as presets.broken.json", e);
                try { Files.move(f, f.resolveSibling("presets.broken.json"), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) { }
            }
        } else {
            long t = System.currentTimeMillis();
            List<PresetData.Example> ex = PresetData.examples();
            for (int i = 0; i < ex.size(); i++) {
                Preset p = new Preset();
                p.name = ex.get(i).name(); p.gen = ShapeSettings.Gen.EQUATION.name(); p.data = new LinkedHashMap<>(ex.get(i).data());
                p.saved = t - i;   // keeps the examples in their original order
                presets.add(p);
            }
            write();
        }
        return presets;
    }

    /** Presets of one kind: pinned first, then the most recently saved. */
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

    /** name, or "name (2)", "name (3)"… whichever isn't taken yet. */
    public static String unique(ShapeSettings.Gen gen, String name) {
        if (find(gen, name) == null) return name;
        for (int i = 2; ; i++) if (find(gen, name + " (" + i + ")") == null) return name + " (" + i + ")";
    }

    /** Saves the settings under this name, replacing a preset of the same name (which keeps its pin). */
    public static Preset save(ShapeSettings.Gen gen, String name, ShapeSettings s) {
        Preset p = find(gen, name);
        if (p == null) { p = new Preset(); p.gen = gen.name(); all().add(p); }
        p.name = name.trim();
        p.data = new LinkedHashMap<>(PresetData.capture(s, gen));
        p.saved = System.currentTimeMillis();
        write();
        return p;
    }

    public static void rename(Preset p, String name) { p.name = name.trim(); write(); }
    public static void togglePin(Preset p) { p.pinned = !p.pinned; write(); }
    public static void delete(Preset p) { all().remove(p); write(); }

    public static boolean matches(Preset p, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return p.name.toLowerCase(Locale.ROOT).contains(q)
                || p.data.getOrDefault("equation", "").toLowerCase(Locale.ROOT).contains(q);
    }

    /** Writes atomically: to a temporary file first, then over the real one. */
    private static void write() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("presets.json.tmp");
            FileFormat ff = new FileFormat();
            ff.presets = presets;
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { GSON.toJson(ff, w); }
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn("Couldn't save presets", e);
        }
    }
}
