package dev.curvegen.core.paint;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public final class PatternNames {
    private PatternNames() {}

    public static String fileStem(String name) {
        if (name.startsWith(Pattern.BUNDLED)) name = name.substring(Pattern.BUNDLED.length());
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String slug = plain.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-$", "");
        return "pattern" + (slug.isEmpty() ? "" : "-" + slug);
    }

    public static String unique(String name, Predicate<String> taken) {
        name = name.trim();
        if (!taken.test(name)) return name;
        for (int i = 2; ; i++) if (!taken.test(name + " (" + i + ")")) return name + " (" + i + ")";
    }

    public static boolean matches(String shown, String query) {
        String n = shown.toLowerCase(Locale.ROOT);
        for (String w : query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
            if (!w.isEmpty() && !n.contains(w)) return false;
        return true;
    }
}
