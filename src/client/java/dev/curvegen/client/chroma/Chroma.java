package dev.curvegen.client.chroma;

import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Lang;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.Gradient;
import java.util.List;
import java.util.Map;

public final class Chroma {
    private Chroma() {}

    public static final String ID = "chromastudio";

    interface Hooks {
        List<Gradient> gradients();
        boolean openGradientTab(String name);
        Catalog.Matcher matcher(boolean top, Map<Family, List<String>> allowed);
        int matcherVersion();
    }

    private static Hooks hooks;

    public static void init() {
        if (!CurveGenClient.platform.isModLoaded(ID)) return;
        try {
            if (ChromaBridge.supported()) hooks = ChromaBridge.install();
        } catch (LinkageError e) {
            dev.curvegen.CurveGen.LOGGER.warn(Lang.tr("curvegen.log.chroma_api"), e);
        }
    }

    public static boolean available() { return hooks != null; }

    public static List<Gradient> gradients() { return hooks == null ? List.of() : hooks.gradients(); }

    public static boolean openGradientTab(String name) { return hooks != null && hooks.openGradientTab(name); }

    public static Catalog.Matcher matcher(boolean top, Map<Family, List<String>> allowed, Catalog.Matcher fallback) {
        Catalog.Matcher m = hooks == null ? null : hooks.matcher(top, allowed);
        if (m == null) return fallback;
        return (rgb, list) -> {
            String id = m.closest(rgb, list);
            return id != null ? id : fallback.closest(rgb, list);
        };
    }

    public static int matcherVersion() { return hooks == null ? -1 : hooks.matcherVersion(); }
}
