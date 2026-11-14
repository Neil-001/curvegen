package dev.curvegen.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;

/**
 * Text wrapped to at most two lines of a given width, as the screens' status and hint lines show it. When more is left
 * the second line ends with "…" and {@code cut} is set, so the caller can give the whole text in a tooltip.
 */
record TwoLines(List<String> lines, boolean cut) {
    static TwoLines of(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        font.getSplitter().splitLines(text, width, Style.EMPTY).forEach(l -> lines.add(l.getString()));
        if (lines.size() <= 2) return new TwoLines(lines, false);
        // The rest after the first line, whose break the splitter dropped, cut to the width.
        String rest = text.substring(Math.min(text.length(), lines.getFirst().length())).stripLeading().replace('\n', ' ');
        return new TwoLines(List.of(lines.getFirst(), font.plainSubstrByWidth(rest, width - font.width("…")) + "…"), true);
    }

    /** The widest line's width in pixels. */
    int width(Font font) {
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        return w;
    }
}
