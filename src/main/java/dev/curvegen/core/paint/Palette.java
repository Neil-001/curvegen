package dev.curvegen.core.paint;

import dev.curvegen.core.paint.Pattern.Cell;

public interface Palette {
    int paletteSize();
    Cell cell(int index);
    int count(int index);
}
