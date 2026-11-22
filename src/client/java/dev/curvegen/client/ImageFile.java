package dev.curvegen.client;

import dev.curvegen.core.paint.ImagePattern;
import dev.curvegen.core.paint.Pattern;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class ImageFile implements AutoCloseable {
    private static final long MAX_BYTES = 128L << 20, MAX_PIXELS = 64L << 20;

    public enum Problem { UNREADABLE, TOO_LARGE }

    public record Result(ImageFile image, Problem problem) {
        static Result of(Problem p) { return new Result(null, p); }
    }

    public final int width, height;
    private ByteBuffer rgba;

    private ImageFile(int width, int height, ByteBuffer rgba) { this.width = width; this.height = height; this.rgba = rgba; }

    public static Result read(Path file) {
        ByteBuffer bytes = null;
        if (!Files.isRegularFile(file)) return Result.of(Problem.UNREADABLE);
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = ch.size();
            if (size > MAX_BYTES) return Result.of(Problem.TOO_LARGE);
            bytes = MemoryUtil.memAlloc((int) size);
            while (bytes.hasRemaining()) if (ch.read(bytes) < 0) break;
            bytes.flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var w = stack.mallocInt(1);
                var h = stack.mallocInt(1);
                var comp = stack.mallocInt(1);
                if (!STBImage.stbi_info_from_memory(bytes, w, h, comp)) return Result.of(Problem.UNREADABLE);
                if ((long) w.get(0) * h.get(0) > MAX_PIXELS) return Result.of(Problem.TOO_LARGE);
                ByteBuffer px = STBImage.stbi_load_from_memory(bytes, w, h, comp, 4);
                if (px == null) return Result.of(Problem.UNREADABLE);
                return new Result(new ImageFile(w.get(0), h.get(0), px), null);
            }
        } catch (IOException | RuntimeException _) {
            return Result.of(Problem.UNREADABLE);
        } finally {
            if (bytes != null) MemoryUtil.memFree(bytes);
        }
    }

    public Pattern pattern(String name, int cells) {
        ByteBuffer px = rgba;
        return ImagePattern.of(name, (x, y) -> {
            int i = (y * width + x) * 4;
            return (px.get(i + 3) & 0xFF) << 24 | (px.get(i) & 0xFF) << 16 | (px.get(i + 1) & 0xFF) << 8 | px.get(i + 2) & 0xFF;
        }, width, height, cells);
    }

    @Override
    public void close() {
        if (rgba != null) STBImage.stbi_image_free(rgba);
        rgba = null;
    }
}
