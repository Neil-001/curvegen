package dev.curvegen.client;

import static dev.curvegen.client.Lang.tr;

import dev.curvegen.client.chroma.Chroma;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.curvegen.core.paint.Paint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The player's paint settings, with a copy of the pattern, and the Blocks tab's block choices, which paint falls back
 * to. They're kept in {@code .minecraft/config/curvegen/paint.json} and {@code blocks.json} so they survive a restart,
 * as {@link Paint#capture} and {@link BlockChoices#captureChoices} write them. {@link #tick} saves them soon after they
 * change.
 */
public final class PaintStore {
    private PaintStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long SAVE_AFTER_MS = 1000;
    /** What was last read or written, and when the settings were first seen to differ from it. */
    private static Paint saved;
    private static Map<String, String> savedBlocks;
    private static long changedAt;

    private static Path file(String name) { return CurveGenClient.platform.configDir().resolve("curvegen").resolve(name); }

    /** Reads the paint file into the live settings. A missing or unreadable file leaves them as they are. */
    public static void load() {
        Paint p = CurveGenClient.SETTINGS.paint;
        Map<String, String> d = readFile(file("paint.json"));
        if (d != null) p.apply(d);
        // A gradient needs Chroma Studio. Without it the gradient is kept, for when it's back, but nothing is painted.
        if (p.source == Paint.Source.GRADIENT && !Chroma.available()) p.source = Paint.Source.OFF;
        saved = p.copy();
    }

    /**
     * Reads the block choices, the same way. NeoForge registers other mods' blocks after the client entrypoint runs,
     * so this waits for the first tick. Fences, panes and walls then attach as the restored blocks and paint say.
     */
    private static void loadBlocks() {
        Map<String, String> d = readFile(file("blocks.json"));
        if (d != null) BlockChoices.applyChoices(d, BlockChoices.CHOICE);
        CurveGenClient.SETTINGS.fullConnects = BlockChoices.fullConnects(CurveGenClient.SETTINGS);
        savedBlocks = BlockChoices.captureChoices(BlockChoices.CHOICE);
    }

    /** The file's settings, or null when it's missing or unreadable, which the log says. */
    private static Map<String, String> readFile(Path f) {
        if (!Files.isRegularFile(f)) return null;
        try {
            Map<String, String> d = read(Files.readString(f));
            if (d == null) dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.settings_invalid", f.getFileName().toString()));
            return d;
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.settings_read", f.getFileName().toString()), e);
            return null;
        }
    }

    /** The text's settings, or null when it isn't a JSON object. Values that aren't text are skipped. */
    static Map<String, String> read(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            Map<String, String> d = new LinkedHashMap<>();
            for (var e : root.getAsJsonObject().entrySet())
                if (e.getValue().isJsonPrimitive()) d.put(e.getKey(), e.getValue().getAsString());
            return d;
        } catch (RuntimeException _) {
            return null;
        }
    }

    static String write(Map<String, String> d) {
        JsonObject o = new JsonObject();
        for (var e : d.entrySet()) o.addProperty(e.getKey(), e.getValue());
        return GSON.toJson(o);
    }

    /** Call every client tick. Saves a second after the settings last changed, so a scroll through values writes once. */
    public static void tick() {
        if (saved == null) return;
        if (savedBlocks == null) loadBlocks();
        Paint now = CurveGenClient.SETTINGS.paint;
        Map<String, String> blocks = BlockChoices.captureChoices(BlockChoices.CHOICE);
        boolean paintChanged = !now.same(saved), blocksChanged = !blocks.equals(savedBlocks);
        if (!paintChanged && !blocksChanged) { changedAt = 0; return; }
        long t = System.currentTimeMillis();
        if (changedAt == 0) { changedAt = t; return; }
        if (t - changedAt < SAVE_AFTER_MS) return;
        changedAt = 0;
        if (paintChanged) {
            saved = now.copy();
            save(file("paint.json"), saved.capture());
        }
        if (blocksChanged) {
            savedBlocks = blocks;
            save(file("blocks.json"), blocks);
        }
    }

    private static void save(Path f, Map<String, String> d) {
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, write(d), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException _) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.settings_save"), e);
        }
    }
}
