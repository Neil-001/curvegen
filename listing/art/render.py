#!/usr/bin/env python3
"""Draws the listing artwork in listing/media, and the mod's icon, from the mod's own solver.

    python3 listing/art/render.py

Needs JDK 25 and Pillow. Compiles dev.curvegen.core with Dump.java, solves every shape through it, and paints the
results the way PreviewTexture does. The banner's title goes through the solver too, as a stencil of the Righteous
typeface. The stone brick texture comes from the Minecraft client jar in the Gradle cache (run ./gradlew build once
first), and the font is downloaded on first use.
"""
import glob
import io
import json
import math
import os
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ART = os.path.join(ROOT, "listing", "art")
OUT = os.path.join(ROOT, "listing", "media")
CACHE = os.path.join(tempfile.gettempdir(), "curvegen-art")
FONT_URL = "https://github.com/google/fonts/raw/main/ofl/righteous/Righteous-Regular.ttf"

# PreviewTexture's colours.
BG = (0x1B, 0x1F, 0x25)
GRID, GRID_MAJOR = 0x22 / 255, 0x55 / 255
AXIS, AXIS_ALPHA = (0x6E, 0xA0, 0xFF), 0xAA / 255
CURVE = (0xFF, 0x4D, 0x73)
TEXT = (0xDD, 0xE3, 0xEA)
DIM = (0x8B, 0x95, 0xA5)

CELL = 16


# ---------- solver ----------

def start_solver():
    classes = os.path.join(CACHE, "classes")
    os.makedirs(classes, exist_ok=True)
    core = glob.glob(os.path.join(ROOT, "src/main/java/dev/curvegen/core/*.java"))
    tools = [os.path.join(ART, "Dump.java"), os.path.join(ART, "dev/curvegen/core/Stencil.java")]
    subprocess.run(["javac", "-nowarn", "-d", classes, *core, *tools], check=True,
                   stderr=subprocess.DEVNULL)
    proc = subprocess.Popen(["java", "-cp", classes, "Dump"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    return proc, json.loads(proc.stdout.readline())


SOLVER, PIECES = start_solver()


def solve(**settings):
    SOLVER.stdin.write(";".join(f"{k}={v}" for k, v in settings.items()) + "\n")
    SOLVER.stdin.flush()
    scene = json.loads(SOLVER.stdout.readline())
    if scene["error"]:
        sys.exit(f"{settings}: {scene['error']}")
    return scene


def status(scene):
    """The line CurveScreen shows above the preview."""
    total = sum(1 for p in scene["grid"] if p)
    pct = scene["err"] / scene["area"] * 100 if scene["area"] > 0 else 0
    return f"{total} pieces on {scene['nx']}×{scene['ny']}, mismatch {scene['err']:.2f} blocks² ({pct:.1f}%)"


# ---------- drawing ----------

def font(size):
    path = os.path.join(CACHE, "Righteous-Regular.ttf")
    if not os.path.exists(path):
        urllib.request.urlretrieve(FONT_URL, path)
    return ImageFont.truetype(path, size)


SUPERSCRIPT = {"²": "2", "³": "3"}


def write(d, xy, text, size, fill, anchor="l"):
    """Draws one line with xy at its left or right end and at its vertical middle, with ² and ³ as small raised digits."""
    f, small = font(size), font(round(size * .6))
    runs = [(SUPERSCRIPT.get(c, c), c in SUPERSCRIPT) for c in text]
    width = sum((small if up else f).getlength(c) for c, up in runs)
    x, y = xy[0] - (width if anchor == "r" else 0), xy[1]
    for c, up in runs:
        d.text((x, y - size * .25 if up else y), c, font=small if up else f, fill=fill, anchor="lm")
        x += (small if up else f).getlength(c)


def stone_bricks():
    jars = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/**/minecraft*client*.jar"), recursive=True))
    for jar in reversed(jars):
        with zipfile.ZipFile(jar) as z:
            name = "assets/minecraft/textures/block/stone_bricks.png"
            if name in z.namelist():
                return Image.open(io.BytesIO(z.read(name))).convert("RGB")
    sys.exit("No Minecraft client jar in ~/.gradle/caches. Run ./gradlew build first.")


def mask(piece):
    """Silhouette.of at 16 pixels, row 0 at the top."""
    m = [[False] * 16 for _ in range(16)]
    for x0, y0, x1, y1 in PIECES[piece]["rects"]:
        for y in range(16 - y1, 16 - y0):
            for x in range(x0, x1):
                m[y][x] = True
    return m


def outline(m, n):
    """Silhouette.outline: filled pixels on the border or touching an empty one, diagonals included."""
    def edge(x, y):
        if x in (0, n - 1) or y in (0, n - 1):
            return True
        return any(not m[y + dy][x + dx] for dy in (-1, 0, 1) for dx in (-1, 0, 1))
    return [[m[y][x] and edge(x, y) for x in range(n)] for y in range(n)]


def darken(rgb, f):
    return tuple(int(c * f) for c in rgb)


def pieces(img, scene, ox, oy, texture=None, color=None):
    """Paints the pieces at 16 pixels a cell, (ox, oy) being the top left of the grid. They take their piece type
    colours unless a texture or one colour for all is given."""
    nx, ny, px = scene["nx"], scene["ny"], img.load()
    for j in range(ny):
        for i in range(nx):
            p = scene["grid"][j * nx + i]
            if not p:
                continue
            m = mask(p)
            c = PIECES[p]["color"]
            rgb = color or (c >> 16 & 255, c >> 8 & 255, c & 255)
            border = outline(m, 16)
            x0, y0 = ox + i * CELL, oy + (ny - 1 - j) * CELL
            for y in range(16):
                for x in range(16):
                    if m[y][x]:
                        px[x0 + x, y0 + y] = texture.getpixel((x, y)) if texture else darken(rgb, .55) if border[y][x] else rgb
    if texture:
        shade_edges(img, scene, ox, oy)


def shade_edges(img, scene, ox, oy):
    """Darkens textured pixels next to empty space, so the outline of the whole build reads against the background."""
    nx, ny, px = scene["nx"], scene["ny"], img.load()
    w, h = nx * CELL, ny * CELL
    filled = [[False] * w for _ in range(h)]
    for j in range(ny):
        for i in range(nx):
            p = scene["grid"][j * nx + i]
            if p:
                m = mask(p)
                for y in range(16):
                    for x in range(16):
                        filled[(ny - 1 - j) * CELL + y][i * CELL + x] = m[y][x]
    for y in range(h):
        for x in range(w):
            if filled[y][x] and any(not (0 <= y + dy < h and 0 <= x + dx < w and filled[y + dy][x + dx])
                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[ox + x, oy + y] = darken(px[ox + x, oy + y], .55)


def blend(px, x, y, rgb, a):
    d = px[x, y]
    px[x, y] = tuple(int(s * a + c * (1 - a)) for s, c in zip(rgb, d[:3]))


def grid(img):
    """Graph paper over the whole image, with a heavier line every five cells."""
    px = img.load()
    for x in range(0, img.width, CELL):
        a = GRID_MAJOR if x // CELL % 5 == 0 else GRID
        for y in range(img.height):
            blend(px, x, y, (255, 255, 255), a)
    for y in range(0, img.height, CELL):
        a = GRID_MAJOR if y // CELL % 5 == 0 else GRID
        for x in range(img.width):
            blend(px, x, y, (255, 255, 255), a)


def axes(img, scene, ox, oy):
    px, nx, ny = img.load(), scene["nx"], scene["ny"]
    if scene["axisX"] is not None:
        x = ox + round(scene["axisX"] * CELL)
        for y in range(oy, oy + ny * CELL):
            if (y - oy) // 4 % 2 == 0:
                blend(px, x, y, AXIS, AXIS_ALPHA)
    if scene["axisY"] is not None:
        y = oy + round((ny - scene["axisY"]) * CELL)
        for x in range(ox, ox + nx * CELL):
            if (x - ox) // 4 % 2 == 0:
                blend(px, x, y, AXIS, AXIS_ALPHA)


def curve(img, scene, ox, oy, width=2):
    d, ny = ImageDraw.Draw(img), scene["ny"]
    for segs in scene["overlay"]:
        for k in range(0, len(segs) - 3, 4):
            d.line([ox + segs[k] * CELL, oy + (ny - segs[k + 1]) * CELL,
                    ox + segs[k + 2] * CELL, oy + (ny - segs[k + 3]) * CELL], fill=CURVE, width=width)


def handles(img, scene, ox, oy, pts):
    """Numbered Bézier handles, joined by a thin line."""
    d, ny = ImageDraw.Draw(img), scene["ny"]
    at = [(ox + x * CELL, oy + (ny - y) * CELL) for x, y in pts]
    d.line(at, fill=DIM, width=1)
    for n, (x, y) in enumerate(at, 1):
        d.ellipse([x - 9, y - 9, x + 9, y + 9], fill=TEXT, outline=BG, width=2)
        d.text((x, y), str(n), font=font(13), fill=BG, anchor="mm")


def paper(scene, cells_w, cells_h, texture=None, pts=None):
    """A solved shape centred on graph paper of the given size in cells, like the mod's preview."""
    img = Image.new("RGB", (cells_w * CELL + 1, cells_h * CELL + 1), BG)
    ox, oy = (cells_w - scene["nx"]) // 2 * CELL, (cells_h - scene["ny"]) // 2 * CELL
    pieces(img, scene, ox, oy, texture)
    grid(img)
    if not texture:
        axes(img, scene, ox, oy)
    curve(img, scene, ox, oy)
    if pts:
        handles(img, scene, ox, oy, pts)
    return img


def scaled(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def rounded(img, r=20):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, img.width - 1, img.height - 1], r, fill=255)
    out = img.convert("RGBA")
    out.putalpha(m)
    return out


def side_by_side(cards, gap):
    w = sum(c.width for c in cards) + gap * (len(cards) - 1)
    out = Image.new("RGBA", (w, max(c.height for c in cards)), (0, 0, 0, 0))
    x = 0
    for c in cards:
        out.paste(rounded(c), (x, 0))
        x += c.width + gap
    return out


def save(img, name):
    img.save(os.path.join(OUT, name), optimize=True)
    print(name, img.size, os.path.getsize(os.path.join(OUT, name)) // 1024, "KiB")


# ---------- banner ----------

COLS, ROWS = 84, 31
W, H = COLS * CELL, ROWS * CELL
STAGE_TOP, STAGE_ROWS = 12, 17      # the shapes go under the title


def wordmark():
    """The title as the solver builds it: Righteous, ten blocks tall, from full blocks, slabs, stairs and walls."""
    f = font(10 * CELL)
    left, top, right, bottom = f.getbbox("Curve Generator")
    nx, ny = -(-(right - left) // CELL), -(-(bottom - top) // CELL)
    img = Image.new("L", (nx * CELL, ny * CELL), 0)
    ImageDraw.Draw(img).text(((img.width - right - left) // 2, -top), "Curve Generator", font=f, fill=255)
    path = os.path.join(CACHE, "wordmark.txt")
    with open(path, "w") as out:
        for y in range(img.height):
            out.write("".join("1" if img.getpixel((x, y)) > 127 else "0" for x in range(img.width)) + "\n")
    return solve(stencil=path, trap="false", fence="false", pane="false")


def banner_frame(name, scene, label, pts=None):
    img = Image.new("RGB", (W, H), BG)
    pieces(img, name, (COLS - name["nx"]) // 2 * CELL, CELL, color=TEXT)
    ox, oy = (COLS - scene["nx"]) // 2 * CELL, (STAGE_TOP + (STAGE_ROWS - scene["ny"]) // 2) * CELL
    pieces(img, scene, ox, oy)
    grid(img)
    axes(img, scene, ox, oy)
    curve(img, scene, ox, oy)
    if pts:
        handles(img, scene, ox, oy, pts)
    d = ImageDraw.Draw(img)
    write(d, (2 * CELL, H - CELL), label, 22, CURVE)
    write(d, (W - 2 * CELL, H - CELL), status(scene), 18, DIM, "r")
    return img


def banner():
    name = wordmark()
    frames, times = [], []

    def add(scene, label, hold=False, pts=None):
        frames.append(banner_frame(name, scene, label, pts))
        times.append(1700 if hold else 80)

    sizes = [(13, 3), (19, 5), (25, 5), (31, 7), (37, 9), (43, 9), (49, 11), (55, 13), (61, 15)]
    for n, (w, h) in enumerate(sizes):
        add(solve(eW=w, eH=h), f"Ellipse, {w} × {h}", n == len(sizes) - 1)
    still = len(frames) - 1

    amps = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2]
    for n, a in enumerate(amps):
        src = f"y = {a:g}sin(x)"
        add(solve(gen="EQUATION", src=src, xmin="-4pi", xmax="4pi", ymin=-2.5, ymax=2.5, qW=80), src, n == len(amps) - 1)

    heart = dict(gen="EQUATION", src="(x^2 + y^2 - 1)^3 = x^2 * y^3", xmin=-1.5, xmax=1.5, ymin=-1.25, ymax=1.5)
    widths = [5, 7, 9, 11, 13, 15, 17, 19]
    for n, w in enumerate(widths):
        add(solve(**heart, qW=w), "(x² + y² − 1)³ = x²y³", n == len(widths) - 1)

    a = [(3, 2), (16, 16), (58, 16), (73, 3)]
    b = [(3, 14), (26, 1), (50, 16), (73, 5)]
    steps = 12
    for n in range(steps + 1):
        u = n / steps
        u = u * u * (3 - 2 * u)
        pts = [(x0 + (x1 - x0) * u, y0 + (y1 - y0) * u) for (x0, y0), (x1, y1) in zip(a, b)]
        scene = solve(gen="BEZIER", bW=76, bH=17, pts=" ".join(f"{x},{y}" for x, y in pts))
        add(scene, "Bézier", n in (0, steps), pts)

    # One palette for every frame, so colours don't shift as the shapes change.
    keys = [still, still + len(amps), still + len(amps) + len(widths), len(frames) - 1]
    sheet = Image.new("RGB", (W, H * len(keys)))
    for n, k in enumerate(keys):
        sheet.paste(frames[k], (0, H * n))
    pal = sheet.quantize(colors=128, method=Image.MEDIANCUT, dither=Image.Dither.NONE)
    out = [f.quantize(palette=pal, dither=Image.Dither.NONE) for f in frames]
    path = os.path.join(OUT, "banner.gif")
    out[0].save(path, save_all=True, append_images=out[1:], duration=times, loop=0, optimize=False)
    print("banner.gif", (W, H), len(frames), "frames", os.path.getsize(path) // 1024, "KiB")
    save(frames[still], "banner.png")


# ---------- stills ----------

def compare():
    """A filled 17-block circle in stone bricks: full blocks only, then with slabs, stairs and walls."""
    tex = stone_bricks()
    off = dict(slab="false", stair="false", trap="false", fence="false", pane="false", wall="false")
    cards = []
    for settings in (off, dict(trap="false", fence="false", pane="false")):
        scene = solve(eW=17, eH=17, eMode="FILLED", **settings)
        cards.append(scaled(paper(scene, 21, 21, tex), 2))
        print(f"  mismatch {scene['err']:.1f} blocks²")
    save(side_by_side(cards, 24), "compare.png")


def shapes():
    """One preview for each tab: Ellipse, Equation, Bézier."""
    pts = [(2, 3), (9, 21), (22, 21), (28, 5)]
    cards = [
        paper(solve(eW=27, eH=19), 32, 24),
        paper(solve(gen="EQUATION", src="y < 9 - x^2/4", xmin=-7, xmax=7, ymin=-1, ymax=10, qW=28), 32, 24),
        paper(solve(gen="BEZIER", bW=30, bH=22, bMode="FILLED", pts=" ".join(f"{x},{y}" for x, y in pts)), 32, 24, pts=pts),
    ]
    save(side_by_side(cards, 16), "shapes.png")


def families():
    """One piece from each family at six times preview size, in the preview's piece colours: full block, slab,
    stairs, trapdoor, shelf, fence, glass pane, wall."""
    wall = next(s for s, p in enumerate(PIECES) if p and p["name"] == "Wall, left low, right low, with post")
    shelf = next(s for s, p in enumerate(PIECES) if p and p["family"] == "SHELF")
    picks = [1, 2, 4, 8, shelf, 15, 17, wall]
    tile, gap, pad = 96, 76, 40
    img = Image.new("RGB", (pad * 2 + len(picks) * tile + (len(picks) - 1) * gap, pad * 2 + tile), BG)
    for n, p in enumerate(picks):
        one = Image.new("RGB", (16, 16), darken(BG, .6))
        pieces(one, dict(nx=1, ny=1, grid=[p]), 0, 0)
        img.paste(scaled(one, tile // 16), (pad + n * (tile + gap), pad))
    save(rounded(img), "pieces.png")


def divider():
    """A cosine wave of pieces on a transparent strip, to separate sections. The right half is the left half mirrored,
    because the solver doesn't promise a symmetric answer for equations."""
    scene = solve(gen="EQUATION", src="y = 0.72cos(x)", xmin="-5pi", xmax="5pi", ymin=-1, ymax=1, qW=80, qLock="false", qH=5,
                  trap="false", fence="false", pane="false", wall="false")
    img = Image.new("RGB", (80 * CELL, 5 * CELL), BG)
    pieces(img, scene, 0, 0)
    half = img.crop((0, 0, img.width // 2, img.height))
    img.paste(half.transpose(Image.FLIP_LEFT_RIGHT), (img.width // 2, 0))
    out = img.convert("RGBA")
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            if px[x, y][:3] == BG:
                px[x, y] = (0, 0, 0, 0)
    save(out, "divider.png")


def icon():
    """The mod's icon: a Bézier curve shaped like a bass clef."""
    pts = [(7, 10), (3, 9.5), (3.5, 18.5), (15.5, 17.5), (15.5, 6.5), (9.5, 3.5), (4, 2)]
    scene = solve(gen="BEZIER", bW=16, bH=16, bLW=1.4, pts=" ".join(f"{x},{y}" for x, y in pts))
    img = Image.new("RGB", (16 * CELL, 16 * CELL), BG)
    pieces(img, scene, 0, 0)
    grid(img)
    curve(img, scene, 0, 0, 3)
    rounded(img, 44).save(os.path.join(ROOT, "src/main/resources/assets/curvegen/icon.png"), optimize=True)
    print("icon.png", img.size)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    banner()
    compare()
    shapes()
    families()
    divider()
    icon()
    SOLVER.stdin.close()
