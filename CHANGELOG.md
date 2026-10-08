# 2.0.0

Curve Generator 2.0.0 adds 3D shapes and an editor in the world. A shape now appears as a hologram with handles you drag to resize it and points you drag to bend it, and the blocks update as you go. The new shapes are ellipsoids, toruses, 3D equations, 3D Bézier curves and Bézier surfaces. A radial menu on V starts shapes and changes their options without leaving the world. This version is for Minecraft 26.3 on Fabric and NeoForge.

The hologram now starts locked at the block you're looking at, with its handles showing. Before, it followed your view until you pressed K. K still unlocks it to follow you.

## Features

- Edit 2D shapes in the world: drag the box's handles to resize, drag Bézier points, move with Page Up and Page Down, resize with Home and End, rotate with R, tip with U, and undo and redo edits with Z and Y. With no hologram out, Z undoes the last placement and Y puts it back (#46)
- A radial menu on V that starts a shape, changes its options and blocks, and places, exports or cancels it. You can put its wedges in your own order. Hold a move or resize key to type an exact number of blocks (#47)
- Ellipsoids and toruses, filled or hollow, built by a new 3D solver that uses every piece type and Minecraft's own rules for stair corners, fences, panes and walls (#45)
- Edit ellipsoids and toruses in the world, and turn or tip them a quarter turn at a time (#48)
- 3D equations in x, y and z, 3D Bézier curves as round tubes, and Bézier surfaces of 2 to 6 rows and columns of points (#49)
- Edit 3D equations, 3D Bézier curves and Bézier surfaces in the world, with a handle on every control point (#50)
- 3D tabs in the G menu, with a preview you can turn, zoom and drag points in, and block counts, presets and Litematica export for every 3D shape (#51)
- A settings screen for hold times, the radial menu, handle size, the hologram's opacity and block limit, and whether the key hints and shape information show on screen. Open it from the cogwheel in the G menu, from Mod Menu on Fabric or from the mod list on NeoForge (#44)
- Scroll over a number field in the G menu or the settings to change it (#56)

## Fixes

- The line above the G menu's picture no longer runs off its edge on a narrow screen (#53)
- On a server without the mod, placing or undoing a shape no longer fills chat with a line for every block (#54)

# 1.3.1

Curve Generator 1.3.1 shows mod messages above the hotbar instead of in chat.

## Features

- Show placement, undo and export messages above the hotbar (#41)

# 1.3.0

Curve Generator 1.3.0 adds shelves, chains and end rods as block choices for curves on Fabric and NeoForge.

## Features

- Use shelves to build curves (#32)
- Include chains and end rods in curves with optional block choices (#34)

# 1.2.0

Curve Generator 1.2.0 brings the mod to Minecraft 26.3 with Fabric and NeoForge.

## Features

- Support for Minecraft 26.3 (#29)

# 1.1.0

Curve Generator 1.1.0 brings the mod to Minecraft 26.2 with Fabric and NeoForge. It adds more ways to move the placement preview, fixes Carve and undo, and removes stray blocks inside thin ellipses.

## Features

- Support for Minecraft 26.2 (#18)
- Support for NeoForge alongside Fabric (#17)
- Move the placement preview along any axis or in the direction you are looking (#13)
- A new bass clef mod icon (#25)

## Fixes

- Thin ellipses no longer have stray blocks inside the curve beside walls (#23)
- Red Carve boxes show on solid blocks, holograms show through water surfaces, and undo restores nearby blocks that lost their support (#19)
- The Count tab draws its block icons more efficiently (#14)
- Wrapped hint text no longer has a shadow (#6)
