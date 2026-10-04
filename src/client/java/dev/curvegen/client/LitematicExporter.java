package dev.curvegen.client;

import dev.curvegen.core.Layout;
import dev.curvegen.core.LitematicBits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Writes a single-region Litematica schematic into the game's schematics folder.
 * Upright shapes: the drawing's x axis runs east, its y axis up, and depth runs south.
 * Flat shapes: x runs east, the drawing's y runs north, and layers stack up. Rotate in Litematica as needed.
 */
public final class LitematicExporter {
    private LitematicExporter() {}

    /** Litematica reads older schematic versions and upgrades them, so version 6 is the safe choice. */
    private static final int SCHEMATIC_VERSION = 6, SUB_VERSION = 1;

    public static Path export(Layout layout, int depth, boolean floor, String baseName, String author) throws IOException {
        int d = Math.max(1, depth);
        // Upright: drawing x → east, y → up, depth → south. Flat: drawing x → east, y → north, layers → up.
        int sx = layout.width(), sy = floor ? d : layout.height(), sz = floor ? layout.height() : d;
        int volume = sx * sy * sz;
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        palette.add(air);
        index.put(air, 0);
        int[] values = new int[volume];
        int total = 0;
        for (Layout.Cell c : layout.cells())
            for (int k = 0; k < d; k++) {
                BlockState st = floor ? BlockChoices.stateFor(c.piece(), c.above(), Direction.EAST, Direction.NORTH, k, d, true)
                                      : BlockChoices.stateFor(c.piece(), c.above(), Direction.EAST, Direction.SOUTH, k, d, false);
                int wy = floor ? k : c.y(), wz = floor ? layout.height() - 1 - c.y() : k;
                int id = index.computeIfAbsent(st, s -> { palette.add(s); return palette.size() - 1; });
                values[(wy * sz + wz) * sx + c.x()] = id;
                total++;
            }
        int bits = LitematicBits.bitsFor(palette.size());

        ListTag paletteNbt = new ListTag();
        for (BlockState st : palette) paletteNbt.add(NbtUtils.writeBlockState(st));

        CompoundTag region = new CompoundTag();
        region.put("Position", vec(0, 0, 0));
        region.put("Size", vec(sx, sy, sz));
        region.put("BlockStatePalette", paletteNbt);
        region.put("BlockStates", new LongArrayTag(LitematicBits.pack(values, bits)));
        region.put("TileEntities", new ListTag());
        region.put("Entities", new ListTag());
        region.put("PendingBlockTicks", new ListTag());
        region.put("PendingFluidTicks", new ListTag());

        long now = System.currentTimeMillis();
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", baseName);
        meta.putString("Author", author);
        meta.putString("Description", "Made with Curve Generator");
        meta.putInt("RegionCount", 1);
        meta.putInt("TotalVolume", volume);
        meta.putInt("TotalBlocks", total);
        meta.putLong("TimeCreated", now);
        meta.putLong("TimeModified", now);
        meta.put("EnclosingSize", vec(sx, sy, sz));

        CompoundTag regions = new CompoundTag();
        regions.put(baseName, region);

        CompoundTag root = new CompoundTag();
        root.putInt("MinecraftDataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.putInt("Version", SCHEMATIC_VERSION);
        root.putInt("SubVersion", SUB_VERSION);
        root.put("Metadata", meta);
        root.put("Regions", regions);

        Path dir = CurveGenClient.platform.gameDir().resolve("schematics");
        Files.createDirectories(dir);
        Path file = dir.resolve(baseName + ".litematic");
        NbtIo.writeCompressed(root, file);
        return file;
    }

    public static String defaultName(String kind) {
        return "curvegen_" + kind + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private static CompoundTag vec(int x, int y, int z) {
        CompoundTag c = new CompoundTag();
        c.putInt("x", x); c.putInt("y", y); c.putInt("z", z);
        return c;
    }
}
