<center>

<img alt="Curve Generator, spelled out in blocks. Below it, an ellipse, a sine wave, a heart and a Bézier curve, each built from blocks." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/banner.gif">

<a href="https://fabricmc.net/"><img alt="Supported on Fabric" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg"></a>
<a href="https://neoforged.net/"><img alt="Supported on NeoForge" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/neoforge_vector.svg"></a>
<a href="https://modrinth.com/mod/fabric-api"><img alt="Requires Fabric API" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg"></a>
<a href="https://github.com/Neil-001/curvegen/wiki"><img alt="Read the wiki" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/generic_vector.svg"></a>
<a href="https://github.com/Neil-001/curvegen"><img alt="Source on GitHub" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg"></a>

</center>

Curve Generator turns ellipses, equations and Bézier curves into blocks. For every cell along the curve it picks the full block, slab, stair, trapdoor, fence, glass pane or wall that matches the curve most closely, so a circle looks rounder than one built from full blocks alone.

<center>

<img alt="Two stone brick circles with the true circle drawn over them. The one made of full blocks has stepped edges. The one with slabs, stairs and walls follows the circle more closely." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/compare.png">

</center>

You see the result in a preview before anything is built. Then you place it in your world, or export it as a Litematica schematic.

<center>

<img alt="Three previews: a thin ellipse, the area under a parabola, and a filled Bézier curve with four numbered handles." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/shapes.png">

*The preview on the Ellipse, Equation and Bézier tabs. The pink line is the true curve.*

</center>

<center>
<img alt="" width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/divider.png">
</center>

## How to use it

1. Press **G** to open the generator.
2. Pick a tab: **Ellipse**, **Equation** or **Bézier**. Equations use Desmos-style syntax, like `y = 2sin(x)`, `x^2 + y^2 = 16` or `y < 9 - x^2/4`. On the Bézier tab, drag the numbered handles in the preview.
3. On the **Blocks** tab, choose a block for each piece type, or use **Match a colour…** to pick blocks whose textures are closest to a colour.
4. Choose **Upright** to build it like a wall or **Flat** to build it like a floor.
5. Check the **Count** tab to see how many of each block you'll need.
6. Press **Place** to position a hologram in the world, then press Enter to build it. Or press **Export** to save a `.litematic` file.

<center>

<img alt="A full block, slab, stair, trapdoor, fence, glass pane and wall, seen from the side." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/pieces.png">

*The pieces it chooses from: full block, slab, stair, trapdoor, fence, glass pane and wall.*

</center>

## Good to know

- Placing needs operator permissions, the same as `/setblock`. In singleplayer, cheats must be on. Exporting works anywhere.
- The mod runs on the client. A server doesn't need it. If the server has it too, large builds place faster.
- It follows Minecraft's connection rules, so fences, panes and walls join in the preview the same way they join in the world.
- Presets save a shape with its block choices.
- Resource packs and modded blocks work. Colour matching reads the textures you have loaded.
- You can rebind every key under Options → Controls → Key Binds → Curve Generator.

The [wiki](https://github.com/Neil-001/curvegen/wiki) covers placement keys, Replace and Carve, presets and exporting.

<center>
<img alt="" width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/divider.png">
</center>
