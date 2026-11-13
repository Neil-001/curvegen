import dev.curvegen.client.BlockChoices;
import dev.curvegen.client.PaintBlocks;
import dev.curvegen.core.Pieces.Family;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Prints what painting needs to know about the game's blocks, for render.py: the blocks each piece type may use, as
 * the Blocks tab's picker offers them, each material's pieces, which full blocks fences, panes and walls don't attach
 * to, which fences join each other, and the Blocks tab's starting choices. It runs the mod's own BlockChoices and
 * PaintBlocks against the game's registries, so it needs Minecraft on the class path but no world or client.
 * Colours aren't here: render.py reads them from the textures, as ColorIndex does in the game.
 *
 * <p>One line each: "allowed FAMILY id…", "piece base FAMILY id", "noconnect id", "wooden id", "choice FAMILY id", and
 * "default id name=value…" with each allowed block's default state, which picks its model.
 */
public class Blocks {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        StringBuilder out = new StringBuilder();
        for (Family f : BlockChoices.FAMILIES) {
            out.append("allowed ").append(f);
            for (Block b : BlockChoices.candidates(f)) out.append(' ').append(PaintBlocks.id(b));
            out.append('\n');
        }
        for (Block b : BlockChoices.candidates(Family.FULL)) {
            String base = PaintBlocks.id(b);
            if (!BlockChoices.connects(b)) out.append("noconnect ").append(base).append('\n');
            if (!base.equals(PaintBlocks.materialOf(base))) continue;
            for (var e : PaintBlocks.pieces(base).entrySet()) out.append("piece ").append(base).append(' ').append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        }
        // The game's wooden fence tag isn't loaded without a world: those are the fences made from planks.
        for (Block b : BlockChoices.candidates(Family.FENCE)) {
            String id = PaintBlocks.id(b), base = PaintBlocks.materialOf(id);
            if (base != null && base.endsWith("_planks")) out.append("wooden ").append(id).append('\n');
        }
        for (Family f : BlockChoices.FAMILIES)
            for (Block b : BlockChoices.candidates(f)) {
                out.append("default ").append(PaintBlocks.id(b));
                for (Property<?> p : b.defaultBlockState().getProperties()) out.append(' ').append(p.getName()).append('=').append(value(b, p));
                out.append('\n');
            }
        for (var e : BlockChoices.CHOICE.entrySet()) out.append("choice ").append(e.getKey()).append(' ').append(PaintBlocks.id(e.getValue())).append('\n');
        System.out.print(out);
    }

    private static <T extends Comparable<T>> String value(Block b, Property<T> p) { return p.getName(b.defaultBlockState().getValue(p)); }
}
