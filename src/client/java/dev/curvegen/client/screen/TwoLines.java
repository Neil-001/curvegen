package dev.curvegen.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;

record TwoLines(List<String> lines, boolean cut) {
    static TwoLines of(Font font, String text, int width) {
        record Line(int start, int end) {}
        List<Line> lines = new ArrayList<>();
        font.getSplitter().splitLines(text, width, Style.EMPTY, false, (_, start, end) -> lines.add(new Line(start, end)));
        if (lines.size() <= 2) return new TwoLines(lines.stream().map(line -> text.substring(line.start, line.end)).toList(), false);
        Line first = lines.getFirst();
        String rest = text.substring(first.end).stripLeading().replace('\n', ' ');
        return new TwoLines(List.of(text.substring(first.start, first.end), font.plainSubstrByWidth(rest, width - font.width("…")) + "…"), true);
    }

    int width(Font font) {
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        return w;
    }
}
