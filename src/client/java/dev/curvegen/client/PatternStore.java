package dev.curvegen.client;

import static dev.curvegen.client.Lang.tr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.curvegen.core.paint.Pattern;
import dev.curvegen.core.paint.PatternNames;
import dev.curvegen.core.paint.Patterns;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The pattern library: saved patterns, one JSON file each in {@code .minecraft/config/curvegen/patterns/}, holding
 * {@link Pattern#encode}'s text with a version, a pin and when it was saved. The first time it's used, the folder
 * starts out with the bundled patterns, whose names stay translation keys until the player renames them.
 */
public final class PatternStore {
    private PatternStore() {}

    /** Bumped when a pattern file's layout changes in a way older versions can't read. */
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class Saved {
        public Pattern pattern;
        public boolean pinned;
        /** Last saved or renamed, in milliseconds. */
        public long saved;
        private Path file;

        /** The name as the player sees it: a bundled pattern's is translated. */
        public String name() { return shownName(pattern); }
    }

    private static List<Saved> all;

    /** A pattern's name as the player sees it: a bundled pattern's is translated. */
    public static String shownName(Pattern p) { return shownName(p.name); }

    /** A pattern's or a mix's stored name as the player sees it: one from a bundled pattern is translated. */
    public static String shownName(String name) { return name.startsWith(Pattern.BUNDLED) ? tr(name) : name; }

    private static Path dir() { return CurveGenClient.platform.configDir().resolve("curvegen").resolve("patterns"); }

    private static List<Saved> all() {
        if (all != null) return all;
        all = new ArrayList<>();
        Path dir = dir();
        if (!Files.isDirectory(dir)) {
            long t = System.currentTimeMillis();
            for (int i = 0; i < Patterns.BUNDLED.size(); i++) {
                Saved s = new Saved();
                s.pattern = Patterns.BUNDLED.get(i);
                s.saved = t - i;   // keeps them in their own order
                all.add(s);
                write(s);
            }
            return all;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.patterns_list"), e);
            return all;
        }
        // Files that can't be read are skipped and left alone: new patterns never take their names.
        for (Path f : files) {
            try {
                Saved s = read(Files.readString(f));
                if (s == null) {
                    dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.pattern_invalid", f.getFileName().toString()));
                    continue;
                }
                s.file = f;
                all.add(s);
            } catch (IOException e) {
                dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.pattern_read", f.getFileName().toString()), e);
            }
        }
        return all;
    }

    /** A pattern file's contents, or null when it isn't a whole, valid pattern this version can read. */
    static Saved read(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonObject o = root.getAsJsonObject();
            Map<String, String> d = new LinkedHashMap<>();
            for (var e : o.entrySet()) if (e.getValue().isJsonPrimitive()) d.put(e.getKey(), e.getValue().getAsString());
            if (d.containsKey("version") && Integer.parseInt(d.get("version").trim()) > FORMAT_VERSION) return null;
            Pattern p = Pattern.decode(d);
            if (p == null) return null;
            Saved s = new Saved();
            s.pattern = p;
            s.pinned = Boolean.parseBoolean(d.get("pinned"));
            try { s.saved = Long.parseLong(d.getOrDefault("saved", "0").trim()); } catch (NumberFormatException ignored) { s.saved = 0; }
            return s;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static String write(Pattern p, boolean pinned, long saved) {
        JsonObject o = new JsonObject();
        o.addProperty("version", FORMAT_VERSION);
        o.addProperty("pinned", pinned);
        o.addProperty("saved", saved);
        for (var e : p.encode().entrySet()) o.addProperty(e.getKey(), e.getValue());
        return GSON.toJson(o);
    }

    /** Every saved pattern: pinned first, then the most recently saved. */
    public static List<Saved> list() {
        List<Saved> out = new ArrayList<>(all());
        out.sort(Comparator.comparing((Saved s) -> !s.pinned).thenComparing(s -> -s.saved));
        return out;
    }

    /** The saved pattern shown under this name, ignoring case, or null. */
    public static Saved find(String name) {
        for (Saved s : all()) if (s.name().equalsIgnoreCase(name.trim())) return s;
        return null;
    }

    /** A name for a new pattern that no saved one has yet. */
    public static String unique(String name) { return PatternNames.unique(name, n -> find(n) != null); }

    /** Saves a new pattern, or replaces the one of the same name, which keeps its pin. */
    public static Saved save(Pattern p) {
        Saved s = find(p.name);
        if (s == null) { s = new Saved(); all().add(s); }
        s.pattern = p;
        s.saved = System.currentTimeMillis();
        write(s);
        return s;
    }

    public static void rename(Saved s, String name) {
        s.pattern = s.pattern.renamed(name.trim());
        s.saved = System.currentTimeMillis();
        write(s);
    }

    public static void togglePin(Saved s) { s.pinned = !s.pinned; write(s); }

    public static void delete(Saved s) {
        all().remove(s);
        if (s.file == null) return;
        try {
            Files.deleteIfExists(s.file);
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.pattern_delete", s.file.getFileName().toString()), e);
        }
    }

    /** The file named after the pattern, or "-2", "-3"… after it if another file has that name. */
    private static Path fileFor(Saved s) {
        String stem = PatternNames.fileStem(s.pattern.name);
        for (int i = 1; ; i++) {
            Path f = dir().resolve(stem + (i == 1 ? "" : "-" + i) + ".json");
            if (f.equals(s.file) || !Files.exists(f)) return f;
        }
    }

    /** Writes one pattern atomically, moving it to a file named after its new name after a rename. */
    private static void write(Saved s) {
        try {
            Files.createDirectories(dir());
            Path f = fileFor(s);
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, write(s.pattern, s.pinned, s.saved), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            if (s.file != null && !s.file.equals(f)) Files.deleteIfExists(s.file);
            s.file = f;
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.pattern_save", s.name()), e);
        }
    }
}
