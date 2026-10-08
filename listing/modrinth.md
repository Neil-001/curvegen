<center>

<img alt="Curve Generator, spelled out in blocks. Below it, an ellipse, a sine wave, a heart and a Bézier curve, each built from blocks, and then a torus turning." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/banner.gif">

<a href="https://fabricmc.net/"><img alt="Supported on Fabric" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg"></a>
<a href="https://neoforged.net/"><img alt="Supported on NeoForge" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/neoforge_vector.svg"></a>
<a href="https://modrinth.com/mod/fabric-api"><img alt="Requires Fabric API" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg"></a>
<a href="https://github.com/Neil-001/curvegen/wiki"><img alt="Read the wiki" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/generic_vector.svg"></a>
<a href="https://github.com/Neil-001/curvegen"><img alt="Source on GitHub" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg"></a>

</center>

Curve Generator turns shapes into blocks. In 2D it builds ellipses, equations and Bézier curves. In 3D it builds ellipsoids, toruses, equations in x, y and z, Bézier curves and Bézier surfaces. For every block along the shape it picks the full block, slab, stair, trapdoor, shelf, fence, glass pane or wall that matches most closely, so a circle looks rounder than one built from full blocks alone.

<center>

<img alt="Two stone brick circles with the true circle drawn over them. The one made of full blocks has stepped edges. The one with slabs, stairs and walls follows the circle more closely." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/compare.png">

</center>

The shape appears in your world as a hologram before anything is built. You drag its handles to resize it and its points to bend it, and the blocks update as you go. Then you place it, or export it as a Litematica schematic.

<center>

<img alt="Three previews: a thin ellipse, the area under a parabola, and a filled Bézier curve with four numbered handles." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/shapes.png">

*The 2D shapes in the menu's preview: ellipse, equation and Bézier curve. The pink line is the true curve.*

</center>

<center>

<img alt="Six pictures of shapes built from blocks: an ellipsoid, a hollow ellipsoid cut open to show its wall, a torus, a wavy surface from the equation z = sin(x) cos(y), a curved tube and a domed sheet." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/shapes3d.png">

*The 3D shapes in the menu's preview: ellipsoid, torus, 3D equation, 3D Bézier curve and Bézier surface. The pink lines are the true shape.*

</center>

<center>
<img alt="A wave built from blocks." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/divider.png">
</center>

## How to use it

1. Hold **V** to open the radial menu. Point at **2D shapes** or **3D shapes** and let go, then click a shape. Its hologram appears at the block you're looking at.
2. Look at a handle on the hologram's box so it lights up, hold the left mouse button and look where it should go. Hold sneak to move the opposite side too.
3. Press **Page Up** and **Page Down** to move the shape away from you and back. **Home** and **End** push out and pull in the side you're facing. **R** rotates the shape and **U** tips it over.
4. Open the radial menu again for **Shape options**, such as the fill, thickness or equation, and for **Blocks**, where you choose a block for each piece type. Scroll over a wedge to change its value without leaving the menu.
5. Press **Enter** to place the shape, or choose **Export** in the radial menu to save a `.litematic` file. **Backspace** cancels.

<center>

<img alt="A block ellipsoid inside a white box. The box has a white handle in the middle of each face, a blue one on each edge and an orange one at each corner." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/editor.png">

*The hologram's box. A face handle moves along one axis, an edge handle along two, and a corner handle in the plane facing you, with scrolling for the third axis.*

</center>

## Editing in the world

- Bézier curves and surfaces have a handle on every control point. Drag one the same way as a box handle. Right-click removes a point and middle-click copies it. **Insert** adds a point where you look at the curve, and **Delete** removes the one you look at. On a surface these add and remove a whole row, or a column while you sneak.
- Hold a move or resize key instead of tapping it and a number box opens after 3 seconds. Type 10 to move exactly 10 blocks, or a negative number to go the other way.
- **Z** undoes the last drag, move or option change, and **Y** redoes it. With no hologram out, **Z** undoes the last placement and **Y** puts it back.
- **K** unlocks the hologram so it follows where you look, and locks it again.
- **H** turns Replace on or off, which decides whether the shape replaces blocks already there. **J** turns Carve on or off, which clears the space the shape encloses.
- **Last shape** in the radial menu brings back the hologram you last placed or cancelled, where it was.

## The full menu

Press **G** for the full menu, with or without a hologram out. It has every setting for every shape, and a button that switches its tabs between 2D and 3D.

- Equations use Desmos-style syntax, like `y = 2sin(x)`, `x^2 + y^2 = 16` or `y < 9 - x^2/4`. A 3D equation can use x, y and z, like `z = sin(x) cos(y)` or `x^2 + y^2 + z^2 < 9`, where z is height.
- The 2D preview shows the pieces on graph paper. The 3D preview shows the blocks from any side: drag to turn it, scroll to zoom, and drag a Bézier point to move it.
- On the **Blocks** tab, choose a block for each piece type, or use **Match a colour…** to pick blocks whose textures are closest to a colour.
- 2D shapes are built **Upright** like a wall or **Flat** like a floor, and can be several blocks deep.
- The **Count** tab shows how many of each block you'll need.
- **Presets** save a shape with its block choices.
- **Place** puts the shape in the world as a hologram. **Export** saves it as a `.litematic` file.

<center>

<img alt="A full block, slab, stair, trapdoor, shelf, fence, glass pane and wall, seen from the side." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/pieces.png">

*The pieces it chooses from: full block, slab, stair, trapdoor, shelf, fence, glass pane and wall.*

</center>

## Settings

The cogwheel in the full menu opens the settings. On Fabric they are also in Mod Menu, if you have it, and on NeoForge behind the Config button in the mod list. You can change:

- how long to hold a key before the number box opens, and before its progress bar shows
- whether the radial menu stays open while you hold its key, or opens and closes with a press
- the order of the radial menu's wedges
- how big the handles are and how close you must look to grab one
- how solid the hologram looks, and how many blocks it shows before it falls back to an outline
- which way scrolling moves a handle you're dragging
- whether the key hints and the shape's size and block counts show on screen

Every key can be rebound under Options → Controls → Key Binds → Curve Generator. There are also six unbound keys that move the shape north, south, east, west, up and down.

## Good to know

- Placing needs operator permissions, the same as `/setblock`. In singleplayer, cheats must be on. Editing and exporting work anywhere.
- The mod runs on the client. A server doesn't need it. If the server has it too, large builds place faster.
- It follows Minecraft's connection rules, so fences, panes, walls and stair corners join in the hologram the same way they join in the world.
- A 3D shape's box can be up to 256 blocks along each side. A shell that thickens outwards adds its thickness beyond that. A hologram of more than 30,000 blocks shows only its outline unless you raise the limit in the settings.
- Chains and end rods are there for thin lines. They start switched off; turn them on in the **Blocks** tab.
- Resource packs and modded blocks work. Colour matching reads the textures you have loaded. For 3D shapes it matches the side of each block, or the top if you choose that.
- Version 2.0 is for Minecraft 26.3. Earlier versions of the mod, without the in-world editor and 3D shapes, are available for older Minecraft versions.

The [wiki](https://github.com/Neil-001/curvegen/wiki) covers Replace and Carve, presets, exporting, and how resource packs and modded blocks work.

<center>
<img alt="A wave built from blocks." width="100%" src="https://raw.githubusercontent.com/Neil-001/curvegen/main/listing/media/divider.png">
</center>
