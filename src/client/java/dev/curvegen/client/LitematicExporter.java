package dev.curvegen.client;

import dev.curvegen.core.Layout;
import dev.curvegen.core.LitematicBits;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a single-region Litematica schematic into the game's schematics folder.
 * The drawing's x axis runs east, its y axis up, and depth runs south; rotate it in Litematica as needed.
 */
public final class LitematicExporter {
    private LitematicExporter() {}

    /** Litematica reads older schematic versions and upgrades them, so version 6 is the safe choice. */
    private static final int SCHEMATIC_VERSION = 6, SUB_VERSION = 1;

    public static Path export(Layout layout, int depth, String baseName, String author) throws IOException {
        int sx = layout.width(), sy = layout.height(), sz = Math.max(1, depth);
        int volume = sx * sy * sz;
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        BlockState air = Blocks.AIR.getDefaultState();
        palette.add(air);
        index.put(air, 0);
        int[] values = new int[volume];
        int total = 0;
        for (Layout.Cell c : layout.cells())
            for (int k = 0; k < sz; k++) {
                BlockState st = BlockChoices.stateFor(c.piece(), Direction.EAST, Direction.SOUTH, k, sz);
                int id = index.computeIfAbsent(st, s -> { palette.add(s); return palette.size() - 1; });
                values[(c.y() * sz + k) * sx + c.x()] = id;
                total++;
            }
        int bits = LitematicBits.bitsFor(palette.size());

        NbtList paletteNbt = new NbtList();
        for (BlockState st : palette) paletteNbt.add(NbtHelper.fromBlockState(st));

        NbtCompound region = new NbtCompound();
        region.put("Position", vec(0, 0, 0));
        region.put("Size", vec(sx, sy, sz));
        region.put("BlockStatePalette", paletteNbt);
        region.put("BlockStates", new NbtLongArray(LitematicBits.pack(values, bits)));
        region.put("TileEntities", new NbtList());
        region.put("Entities", new NbtList());
        region.put("PendingBlockTicks", new NbtList());
        region.put("PendingFluidTicks", new NbtList());

        long now = System.currentTimeMillis();
        NbtCompound meta = new NbtCompound();
        meta.putString("Name", baseName);
        meta.putString("Author", author);
        meta.putString("Description", "Made with Curve Generator");
        meta.putInt("RegionCount", 1);
        meta.putInt("TotalVolume", volume);
        meta.putInt("TotalBlocks", total);
        meta.putLong("TimeCreated", now);
        meta.putLong("TimeModified", now);
        meta.put("EnclosingSize", vec(sx, sy, sz));

        NbtCompound regions = new NbtCompound();
        regions.put(baseName, region);

        NbtCompound root = new NbtCompound();
        root.putInt("MinecraftDataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
        root.putInt("Version", SCHEMATIC_VERSION);
        root.putInt("SubVersion", SUB_VERSION);
        root.put("Metadata", meta);
        root.put("Regions", regions);

        Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
        Files.createDirectories(dir);
        Path file = dir.resolve(baseName + ".litematic");
        NbtIo.writeCompressed(root, file);
        return file;
    }

    public static String defaultName(String kind) {
        return "curvegen_" + kind + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private static NbtCompound vec(int x, int y, int z) {
        NbtCompound c = new NbtCompound();
        c.putInt("x", x); c.putInt("y", y); c.putInt("z", z);
        return c;
    }
}
