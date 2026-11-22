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

public final class PaintStore {
    private PaintStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long SAVE_AFTER_MS = 1000;
    private static Paint saved;
    private static Map<String, String> savedBlocks;
    private static long changedAt;

    private static Path file(String name) { return CurveGenClient.platform.configDir().resolve("curvegen").resolve(name); }

    public static void load() {
        Paint p = CurveGenClient.SETTINGS.paint;
        Map<String, String> d = readFile(file("paint.json"));
        if (d != null) p.apply(d);
        if (p.source == Paint.Source.GRADIENT && !Chroma.available()) {
            p.source = Paint.Source.OFF;
            noGradient = true;
        }
        saved = p.copy();
    }

    private static void loadBlocks() {
        Map<String, String> d = readFile(file("blocks.json"));
        if (d != null) BlockChoices.applyChoices(d, CurveGenClient.SETTINGS, BlockChoices.CHOICE);
        CurveGenClient.SETTINGS.fullConnects = BlockChoices.fullConnects(CurveGenClient.SETTINGS);
        savedBlocks = BlockChoices.captureChoices(CurveGenClient.SETTINGS, BlockChoices.CHOICE);
    }

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

    private static boolean noGradient;

    public static void tick() {
        if (noGradient && net.minecraft.client.Minecraft.getInstance().player != null) {
            noGradient = false;
            Placement.say(net.minecraft.network.chat.Component.literal(tr("curvegen.paint.preset_no_gradient")));
        }
        if (saved == null) return;
        if (savedBlocks == null) loadBlocks();
        Paint now = CurveGenClient.SETTINGS.paint;
        Map<String, String> blocks = BlockChoices.captureChoices(CurveGenClient.SETTINGS, BlockChoices.CHOICE);
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
