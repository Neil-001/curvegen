package dev.curvegen.net;

import dev.curvegen.CurveGen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * One batch of blocks to set, relative to an origin. States travel as raw block-state ids,
 * which Fabric keeps in sync between client and server. Batches stay well under the
 * 32 KiB limit for serverbound custom payloads.
 */
public record PlaceBlocksPayload(BlockPos origin, int[] offsets, int[] states, boolean last, int total, boolean undo)
        implements CustomPayload {

    public static final int MAX_PER_BATCH = 2500;
    public static final CustomPayload.Id<PlaceBlocksPayload> ID = new CustomPayload.Id<>(Identifier.of(CurveGen.MOD_ID, "place_blocks"));
    public static final PacketCodec<PacketByteBuf, PlaceBlocksPayload> CODEC = PacketCodec.of(PlaceBlocksPayload::write, PlaceBlocksPayload::read);

    private static int zig(int v) { return (v << 1) ^ (v >> 31); }
    private static int unzig(int v) { return (v >>> 1) ^ -(v & 1); }

    private void write(PacketByteBuf buf) {
        buf.writeBlockPos(origin);
        buf.writeVarInt(states.length);
        for (int i = 0; i < states.length; i++) {
            buf.writeVarInt(zig(offsets[3 * i]));
            buf.writeVarInt(zig(offsets[3 * i + 1]));
            buf.writeVarInt(zig(offsets[3 * i + 2]));
            buf.writeVarInt(states[i]);
        }
        buf.writeBoolean(last);
        buf.writeVarInt(total);
        buf.writeBoolean(undo);
    }

    private static PlaceBlocksPayload read(PacketByteBuf buf) {
        BlockPos origin = buf.readBlockPos();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_PER_BATCH) throw new IllegalArgumentException("Too many blocks in one batch: " + n);
        int[] off = new int[n * 3], st = new int[n];
        for (int i = 0; i < n; i++) {
            off[3 * i] = unzig(buf.readVarInt());
            off[3 * i + 1] = unzig(buf.readVarInt());
            off[3 * i + 2] = unzig(buf.readVarInt());
            st[i] = buf.readVarInt();
        }
        return new PlaceBlocksPayload(origin, off, st, buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
