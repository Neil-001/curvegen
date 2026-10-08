# Listing

The Modrinth and CurseForge project pages, and the artwork they share.

- `modrinth.md` and `curseforge.html` are the two descriptions. They say the same thing.
- `media/` holds the images, which both pages load from `main`. A new or changed image shows on the pages once it's merged there.
- `art/` draws the images from the mod's solvers. Run `python3 listing/art/render.py`. It needs JDK 25 and Pillow, and `./gradlew build` run once before it.
