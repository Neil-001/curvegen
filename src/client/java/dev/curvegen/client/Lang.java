package dev.curvegen.client;

import dev.curvegen.core.Message;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Resolves the core's messages using the game's current language. */
public final class Lang {
    private Lang() {}

    private static final Map<String, String> ENGLISH = english();

    private static Map<String, String> english() {
        Map<String, String> entries = new HashMap<>();
        try (var input = Objects.requireNonNull(Lang.class.getResourceAsStream("/assets/curvegen/lang/en_us.json"))) {
            Language.loadFromJson(input, entries::put);
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
        return Map.copyOf(entries);
    }

    public static MutableComponent component(String key, Object... args) {
        Object[] translated = new Object[args.length];
        for (int i = 0; i < args.length; i++)
            translated[i] = args[i] instanceof Message message ? component(message) : args[i];
        return Component.translatableWithFallback(key, ENGLISH.get(key), translated);
    }

    public static MutableComponent component(Message message) {
        return component(message.key(), message.args().toArray());
    }

    public static String tr(String key, Object... args) { return component(key, args).getString(); }

    public static String text(Message message) { return message == null ? null : component(message).getString(); }
}
