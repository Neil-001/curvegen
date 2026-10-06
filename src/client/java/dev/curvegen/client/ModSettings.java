package dev.curvegen.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The mod's own options, saved in {@code .minecraft/config/curvegen/settings.json}. They're plain static fields:
 * read them anywhere on the client, and call {@link #save()} after changing one.
 */
public final class ModSettings {
    private ModSettings() {}

    /** The limits the settings screen and the file reader both keep each number within. */
    public static final double HOLD_MIN = 0.5, HOLD_MAX = 10, BAR_DELAY_MAX = 5, HANDLE_MIN = 0.05, HANDLE_MAX = 1,
            PICK_MIN = 0.1, PICK_MAX = 2, OPACITY_MIN = 0.1, OPACITY_MAX = 1;
    public static final int BLOCK_LIMIT_MAX = 1_000_000;

    /** How long a nudge or bump key is held before the number box opens. */
    public static double holdSeconds;
    /** How long a key is held before the progress bar shows. */
    public static double barDelaySeconds;
    /** False: the radial menu is open while its key is held. True: one press opens it and another closes it. */
    public static boolean radialToggle;
    /** The radial menu's wedge ids in display order. Empty means the default order. */
    public static List<String> radialOrder;
    /** The size of the in-world drag handles, in blocks. */
    public static double handleSize;
    /** How close the crosshair must pass to a handle to pick it, in blocks. */
    public static double pickRadius;
    public static double hologramOpacity;
    /** Above this many blocks the hologram shows only its outline. */
    public static int hologramBlockLimit;
    /** Reverses which way scrolling moves a handle while it's dragged. */
    public static boolean invertDragScroll;

    static { reset(); }

    public static void reset() {
        holdSeconds = 3.0;
        barDelaySeconds = 0.3;
        radialToggle = false;
        radialOrder = new ArrayList<>();
        handleSize = 0.25;
        pickRadius = 0.4;
        hologramOpacity = 0.45;
        hologramBlockLimit = 30000;
        invertDragScroll = false;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Path file() { return CurveGenClient.platform.configDir().resolve("curvegen").resolve("settings.json"); }

    public static void load() { load(file()); }
    public static void save() { save(file()); }

    /** Reads the file. A missing or unreadable file leaves every setting at its default. */
    public static void load(Path file) {
        reset();
        if (!Files.isRegularFile(file)) return;
        try {
            if (!read(Files.readString(file)))
                dev.curvegen.CurveGen.LOGGER.warn("Couldn't understand {}, so the settings are back to their defaults", file.getFileName());
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn("Couldn't read {}, so the settings are back to their defaults", file.getFileName(), e);
        }
    }

    /** Writes the file atomically: to a temporary file first, then over the real one. */
    public static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, write(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            dev.curvegen.CurveGen.LOGGER.warn("Couldn't save the settings", e);
        }
    }

    /**
     * Takes every setting from the JSON text. A setting that's missing, of the wrong type or outside its limits gets
     * its default, and unknown names are ignored. Returns false if the text isn't a JSON object at all.
     */
    public static boolean read(String json) {
        reset();
        JsonObject o;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return false;
            o = root.getAsJsonObject();
        } catch (RuntimeException e) {
            return false;
        }
        holdSeconds = number(o, "holdSeconds", HOLD_MIN, HOLD_MAX, holdSeconds);
        barDelaySeconds = number(o, "barDelaySeconds", 0, BAR_DELAY_MAX, barDelaySeconds);
        radialToggle = flag(o, "radialToggle", radialToggle);
        if (o.get("radialOrder") instanceof JsonArray order)
            for (JsonElement e : order)
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && !radialOrder.contains(e.getAsString())) radialOrder.add(e.getAsString());
        handleSize = number(o, "handleSize", HANDLE_MIN, HANDLE_MAX, handleSize);
        pickRadius = number(o, "pickRadius", PICK_MIN, PICK_MAX, pickRadius);
        hologramOpacity = number(o, "hologramOpacity", OPACITY_MIN, OPACITY_MAX, hologramOpacity);
        double limit = number(o, "hologramBlockLimit", 0, BLOCK_LIMIT_MAX, hologramBlockLimit);
        if (limit == Math.rint(limit)) hologramBlockLimit = (int) limit;
        invertDragScroll = flag(o, "invertDragScroll", invertDragScroll);
        return true;
    }

    public static String write() {
        JsonObject o = new JsonObject();
        o.addProperty("holdSeconds", holdSeconds);
        o.addProperty("barDelaySeconds", barDelaySeconds);
        o.addProperty("radialToggle", radialToggle);
        JsonArray order = new JsonArray();
        for (String id : radialOrder) order.add(id);
        o.add("radialOrder", order);
        o.addProperty("handleSize", handleSize);
        o.addProperty("pickRadius", pickRadius);
        o.addProperty("hologramOpacity", hologramOpacity);
        o.addProperty("hologramBlockLimit", hologramBlockLimit);
        o.addProperty("invertDragScroll", invertDragScroll);
        return GSON.toJson(o);
    }

    private static double number(JsonObject o, String name, double min, double max, double fallback) {
        JsonElement e = o.get(name);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        double v;
        try { v = e.getAsDouble(); } catch (NumberFormatException ex) { return fallback; }
        return v >= min && v <= max ? v : fallback;   // NaN fails both comparisons
    }

    private static boolean flag(JsonObject o, String name, boolean fallback) {
        JsonElement e = o.get(name);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }
}
