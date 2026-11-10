#!/usr/bin/env python3
"""Writes listing/curseforge.html from listing/description.md, so the two pages say the same thing.

    python3 listing/art/curseforge.py            # writes curseforge.html
    python3 listing/art/curseforge.py --check    # exits 1 if curseforge.html is out of date

Modrinth takes description.md as it is, <center> tags included. CurseForge's markdown drops <center>, but its
HTML keeps a paragraph's text-align style, which is what its own editor writes. So each paragraph inside a
<center> block becomes <p style="text-align: center;">, and the rest is plain HTML from the markdown. Two things
are then swapped for CurseForge's page, listed under FOR_CURSEFORGE. Needs the markdown package: pip install
markdown.
"""
import re
import sys
from pathlib import Path

try:
    import markdown
except ImportError:
    sys.exit("needs the markdown package: pip install markdown")

LISTING = Path(__file__).resolve().parent.parent
SOURCE = LISTING / "description.md"
TARGET = LISTING / "curseforge.html"
CENTRE = '<p style="text-align: center;">'
# What CurseForge's page says differently, as (pattern, replacement) pairs applied to the HTML.
FOR_CURSEFORGE = (
    # The Fabric API badge links to Fabric API on the site the reader is on.
    (r"https://modrinth\.com/mod/fabric-api", "https://www.curseforge.com/minecraft/mc-mods/fabric-api"),
    # devins-badges as PNG instead of SVG, which not every place CurseForge shows a description is sure to draw.
    (r"(/devins-badges@[^\"]*)_vector\.svg", r"\1_64h.png"),
)


def md(text):
    # Modrinth nests a list item indented by two spaces; Python-Markdown wants to be told so.
    return markdown.markdown(text, extensions=["tables"], output_format="html", tab_length=2).strip("\n")


def render(text):
    """The CurseForge HTML for the description's markdown."""
    # Python-Markdown treats everything from <center> to </center> as one raw HTML block and leaves the markdown
    # inside alone, so each block's inside is rendered on its own, with its paragraphs centred.
    parts, end = [], 0
    for block in re.finditer(r"^<center>[ \t]*\n(.*?)^</center>[ \t]*\n", text, flags=re.S | re.M):
        parts.append(md(text[end:block.start()]))
        parts.append(md(block.group(1)).replace("<p>", CENTRE))
        end = block.end()
    parts.append(md(text[end:]))
    html = "\n".join(part for part in parts if part)
    if "<center>" in html or "</center>" in html:
        sys.exit("a <center> tag isn't on a line of its own, or its block isn't closed")
    for pattern, replacement in FOR_CURSEFORGE:
        html = re.sub(pattern, replacement, html)
    return html + "\n"


def main(argv):
    html = render(SOURCE.read_text(encoding="utf-8"))
    if "--check" in argv:
        current = TARGET.read_text(encoding="utf-8") if TARGET.exists() else ""
        if current != html:
            sys.exit(f"{TARGET.relative_to(LISTING.parent)} is out of date: run python3 listing/art/curseforge.py")
        print(f"{TARGET.relative_to(LISTING.parent)} is up to date")
        return
    TARGET.write_text(html, encoding="utf-8")
    print(f"wrote {TARGET.relative_to(LISTING.parent)}: {html.count(CENTRE)} centred paragraphs")


if __name__ == "__main__":
    main(sys.argv[1:])
