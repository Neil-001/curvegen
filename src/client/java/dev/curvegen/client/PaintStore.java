package dev.curvegen.client;

import static dev.curvegen.client.Lang.tr;

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
 * The player's paint settings, with a copy of the pattern, kept in {@code .minecraft/config/curvegen/paint.json} so
 * they survive a restart, as {@link Paint#capture} writes them. {@link #tick} saves them soon after they change.
 */
public final class PaintStore {
    private PaintStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long SAVE_AFTER_MS = 1000;
    /** What was last read or written, and when the settings were first seen to differ from it. */
    private static Paint saved;
    private static long changedAt;

    private static Path file() { return CurveGenClient.platform.configDir().resolve("curvegen").resolve("paint.json"); }

    /** Reads the file into the live settings. A missing or unreadable file leaves them as they are. */
    public static void load() {
        Paint p = CurveGenClient.SETTINGS.paint;
        Path f = file();
        if (Files.isRegularFile(f)) {
            try {
                Map<String, String> d = read(Files.readString(f));
                if (d != null) p.apply(d);
                else dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.settings_invalid", f.getFileName().toString()));
            } catch (IOException e) {
                dev.curvegen.CurveGen.LOGGER.warn(tr("curvegen.log.settings_read", f.getFileName().toString()), e);
            }
        }
        saved = p.copy();
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

    static String write(Paint p) {
        JsonObject o = new JsonObject();
        for (var e : p.capture().entrySet()) o.addProperty(e.getKey(), e.getValue());
        return GSON.toJson(o);
    }

    /** Call every client tick. Saves a second after the settings last changed, so a scroll through values writes once. */
    public static void tick() {
        Paint now = CurveGenClient.SETTINGS.paint;
        if (saved == null || now.same(saved)) { changedAt = 0; return; }
        long t = System.currentTimeMillis();
        if (changedAt == 0) { changedAt = t; return; }
        if (t - changedAt < SAVE_AFTER_MS) return;
        changedAt = 0;
        saved = now.copy();
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, write(saved), StandardCharsets.UTF_8);
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
