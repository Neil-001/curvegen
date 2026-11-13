package dev.curvegen.client.chroma;

import dev.curvegen.client.CurveGenClient;
import dev.curvegen.client.Lang;
import dev.curvegen.core.Pieces.Family;
import dev.curvegen.core.paint.Catalog;
import dev.curvegen.core.paint.Gradient;
import java.util.List;
import java.util.Map;

/**
 * Chroma Studio, when a new enough one is installed: its saved gradients, its palettes and its colour matching. It is
 * optional, so this class never refers to it. {@link #init} checks the loader has it and that its API is version 2
 * (Chroma Studio 1.2) or later, and only then loads {@link ChromaBridge}, the one class that does, which sets the hooks
 * here. An older Chroma Studio counts as not installed: the hooks keep their defaults, and everything works as it does
 * without one, with the features that need it greyed out.
 */
public final class Chroma {
    private Chroma() {}

    /** The mod's id. */
    public static final String ID = "chromastudio";

    /** What Chroma Studio does for this mod. {@link ChromaBridge} has the real one. */
    interface Hooks {
        List<Gradient> gradients();
        boolean openGradientTab(String name);
        /** A matcher that answers null where it can't, or null itself before Chroma Studio's index is ready. */
        Catalog.Matcher matcher(boolean top, Map<Family, List<String>> allowed);
        int matcherVersion();
    }

    private static Hooks hooks;

    /** Looks for Chroma Studio. Call once, from the client entrypoint, before anything asks about it. */
    public static void init() {
        if (!CurveGenClient.platform.isModLoaded(ID)) return;
        try {
            if (ChromaBridge.supported()) hooks = ChromaBridge.install();
        } catch (LinkageError e) {
            dev.curvegen.CurveGen.LOGGER.warn(Lang.tr("curvegen.log.chroma_api"), e);
        }
    }

    /** Whether Chroma Studio 1.2 or newer is installed. */
    public static boolean available() { return hooks != null; }

    /** The gradients the player saved in Chroma Studio, each copied whole, or an empty list without it. */
    public static List<Gradient> gradients() { return hooks == null ? List.of() : hooks.gradients(); }

    /**
     * Opens Chroma Studio on its Gradient tab with the named gradient loaded, or as the player left it for null.
     * Closing it returns to the game. Returns whether it opened.
     */
    public static boolean openGradientTab(String name) { return hooks != null && hooks.openGradientTab(name); }

    /**
     * The matcher painting and "Match a colour…" use, made now on the client thread and safe on any thread after:
     * Chroma Studio's on the top face or a side, for the lists of blocks in {@code allowed}, or {@code fallback} without
     * it or before its block index is ready. {@code fallback} also answers for a list none of whose blocks Chroma Studio
     * knows.
     */
    public static Catalog.Matcher matcher(boolean top, Map<Family, List<String>> allowed, Catalog.Matcher fallback) {
        Catalog.Matcher m = hooks == null ? null : hooks.matcher(top, allowed);
        if (m == null) return fallback;
        return (rgb, list) -> {
            String id = m.closest(rgb, list);
            return id != null ? id : fallback.closest(rgb, list);
        };
    }

    /**
     * A number that moves on whenever Chroma Studio's matching would answer differently, as when its block index is
     * built again after a resource pack change. -1 without it or before its index is ready, when painting matches as it
     * does without it. A shape painted under another number is painted again.
     */
    public static int matcherVersion() { return hooks == null ? -1 : hooks.matcherVersion(); }
}
