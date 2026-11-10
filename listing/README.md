# Listing

The Modrinth and CurseForge project pages, and the artwork they share.

- `description.md` is the description, which Modrinth takes as it is. `curseforge.html` is the same text as CurseForge's HTML, written by `art/curseforge.py`, because CurseForge's markdown drops `<center>` while its HTML keeps a centred paragraph. The script also points the Fabric API badge at CurseForge and uses PNG badges. Run `python3 listing/art/curseforge.py` after editing the description. The build checks that the two match. It needs the `markdown` package.
- `media/` holds the images, which both pages load from `main`. A new or changed image shows on the pages once it's merged there.
- `gallery/` holds the screenshots for the two sites' galleries. Its `README.md` has each one's title and description. The `Listing` workflow (`.github/workflows/listing.yml`) uploads them to Modrinth along with the description. CurseForge's page and gallery are done by hand: paste `curseforge.html` and upload the pictures.
- `art/` draws the images from the mod's solvers. Run `python3 listing/art/render.py`. It needs JDK 25 and Pillow, and `./gradlew build` run once before it.

After a change to the description or gallery has merged, run `gh workflow run listing.yml --ref main -f dry_run=true`, check what it says it would do, then run it without `dry_run`. It leaves a gallery picture alone when Modrinth already shows one with the same title, and `-f replace=true` uploads those again. It also sets the project's icon from `media/icon.png` when that file exists, which it doesn't here.
