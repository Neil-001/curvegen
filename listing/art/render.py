#!/usr/bin/env python3
"""Draws the listing artwork in listing/media, and the mod's icon, from the mod's own solvers.

    python3 listing/art/render.py

Needs JDK 25 and Pillow. Compiles dev.curvegen.core with Dump.java, solves every shape through it, and paints the
results the way PreviewTexture does. 3D shapes go through Solver3, and Dump draws them with Mesh3 and Raster3 as
the menu's 3D preview does. The banner's title goes through the solver too, as a stencil of the Righteous
typeface. The stone brick texture comes from the Minecraft client jar in the Gradle cache (run ./gradlew build once
first), and the font is downloaded on first use.

Painted shapes are painted by the mod's own painters, in Dump.java. What they need to know about the game's blocks
comes from Blocks.java, run against Minecraft with the class path Fabric's tests use, and each block's colour from its
textures in the client jar, averaged as ColorIndex averages them. Tints aren't applied, so the pictures stay clear of
grass and leaves. Colours are matched as "Match a colour…" matches them without Chroma Studio.
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

from PIL import Image, ImageChops, ImageDraw, ImageFont

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
STONE = "A9AAA6"
# Editor's handle colours: face, edge, corner.
FACE, EDGE, CORNER = (0xF2, 0xF2, 0xF2), (0x7F, 0xD4, 0xFF), (0xFF, 0xA6, 0x46)

CELL = 16


# ---------- solver ----------

def start_solver():
    classes = os.path.join(CACHE, "classes")
    os.makedirs(classes, exist_ok=True)
    core = glob.glob(os.path.join(ROOT, "src/main/java/dev/curvegen/core/**/*.java"), recursive=True)
    tools = [os.path.join(ART, "Dump.java"), *glob.glob(os.path.join(ART, "dev/**/*.java"), recursive=True)]
    subprocess.run(["javac", "-nowarn", "-d", classes, *core, *tools], check=True,
                   stderr=subprocess.DEVNULL)
    proc = subprocess.Popen(["java", "-cp", classes, "Dump"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    return proc, json.loads(proc.stdout.readline())


SOLVER, PIECES = start_solver()


def solve(**settings):
    SOLVER.stdin.write(";".join(f"{k}={v}" for k, v in settings.items()) + "\n")
    SOLVER.stdin.flush()
    scene = json.loads(SOLVER.stdout.readline())
    if scene.get("error"):
        sys.exit(f"{settings}: {scene['error']}")
    return scene


def solve3(w, h, **settings):
    """Solves a 3D shape and draws it w × h pixels, as the menu's 3D preview does. Returns the picture and the solve."""
    path = os.path.join(CACHE, "view.argb")
    scene = solve(viewOut=path, viewW=w, viewH=h, viewBg="%02X%02X%02X" % BG, **settings)
    with open(path, "rb") as f:
        img = Image.frombuffer("RGBA", (w, h), f.read(), "raw", "ARGB", 0, 1).convert("RGB")
    return img, scene


def status3(scene):
    """The line CurveScreen shows above the 3D preview."""
    pct = scene["err"] / scene["volume"] * 100 if scene["volume"] > 0 else 0
    return f"{scene['blocks']} blocks in {scene['nx']}×{scene['ny']}×{scene['nz']}, mismatch {scene['err']:.1f} blocks³ ({pct:.1f}%)"


def status(scene):
    """The line CurveScreen shows above the preview."""
    total = sum(1 for p in scene["grid"] if p)
    pct = scene["err"] / scene["area"] * 100 if scene["area"] > 0 else 0
    return f"{total} pieces on {scene['nx']}×{scene['ny']}, mismatch {scene['err']:.2f} blocks² ({pct:.1f}%)"


# ---------- painting ----------

def catalog():
    """The file Dump.java reads painting's blocks from: Blocks.java's lines, then each block's side and top colour."""
    jar = client_jar()
    path = os.path.join(CACHE, "catalog-" + os.path.basename(jar)[:-4] + ".txt")
    if os.path.exists(path):
        return path
    init = os.path.join(ART, "classpath.gradle")
    out = subprocess.run([os.path.join(ROOT, "gradlew"), "-q", "-I", init, ":fabric:artClasspath"], cwd=ROOT, check=True,
                         capture_output=True, text=True).stdout
    cp = next(l[3:] for l in out.splitlines() if l.startswith("CP="))
    classes = os.path.join(CACHE, "blocks")
    os.makedirs(classes, exist_ok=True)
    subprocess.run(["javac", "-nowarn", "-cp", cp, "-d", classes, os.path.join(ART, "Blocks.java")], check=True)
    lines = subprocess.run(["java", "-cp", classes + os.pathsep + cp, "Blocks"], cwd=classes, check=True,  # the game writes logs/ here
                           capture_output=True, text=True).stdout.splitlines()
    ids = {i for l in lines if l.startswith("allowed ") for i in l.split()[2:]}
    states = {l.split()[1]: dict(kv.split("=", 1) for kv in l.split()[2:]) for l in lines if l.startswith("default ")}
    with zipfile.ZipFile(jar) as z:
        textures = Textures(z)
        colours = [f"{face} {i} {textures.colour(i, face, states.get(i, {})):06x}" for i in sorted(ids) for face in ("side", "top")]
    with open(path, "w") as f:
        f.write("\n".join(lines + colours) + "\n")
    return path


class Textures:
    """Block colours from the client jar's models and textures, as ColorIndex works them out in the game: the default
    state's quads on the north face (side) or the top, with its models turned as the block state turns them, each
    texture's opaque pixels averaged and weighed by how much of it is opaque, or the particle texture when the face has
    none."""

    def __init__(self, z):
        self.z, self.models, self.averages = z, {}, {}

    def json(self, kind, ref):
        ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
        name = f"assets/{ns}/{kind}/{path}.json"
        return json.loads(self.z.read(name)) if name in self.z.namelist() else None

    def model(self, ref):
        """A model with its parents' textures merged in and the nearest elements."""
        if ref not in self.models:
            m = self.json("models", ref) or {}
            parent = self.model(m["parent"]) if "parent" in m else {"textures": {}, "elements": []}
            self.models[ref] = {"textures": {**parent["textures"], **m.get("textures", {})},
                                "elements": m.get("elements", parent["elements"])}
        return self.models[ref]

    def parts(self, block, state):
        """The models the block's default state is drawn with, each with its x and y rotation in degrees."""
        bs = self.json("blockstates", block) or {}

        def holds(conditions):
            return all(state.get(k) in str(v).split("|") for k, v in conditions.items())

        def matches(when):
            if "OR" in when:
                return any(matches(w) for w in when["OR"])
            if "AND" in when:
                return all(matches(w) for w in when["AND"])
            return holds(when)

        picks = []
        if "variants" in bs:
            for key, pick in bs["variants"].items():
                if holds(dict(kv.split("=") for kv in key.split(",") if kv)):
                    picks.append(pick)
                    break
        else:
            picks = [p["apply"] for p in bs.get("multipart", []) if matches(p.get("when", {}))]
        return [(p.get("model"), p.get("x", 0), p.get("y", 0)) for p in (q[0] if isinstance(q, list) else q for q in picks)]

    def average(self, ref, textures):
        while isinstance(ref, dict) or ref and ref.startswith("#"):
            ref = ref.get("sprite") if isinstance(ref, dict) else textures.get(ref[1:])   # newer models give {"sprite": ...}
        if not ref:
            return None
        if ref not in self.averages:
            ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
            name = f"assets/{ns}/textures/{path}.png"
            avg = None
            if name in self.z.namelist():
                img = Image.open(io.BytesIO(self.z.read(name))).convert("RGBA")
                r = g = b = w = 0
                for pr, pg, pb, a in img.getdata():
                    if a < 16:
                        continue
                    r, g, b, w = r + pr * a, g + pg * a, b + pb * a, w + a
                avg = (r // w, g // w, b // w, max(1, w // 255)) if w else None
            self.averages[ref] = avg
        return self.averages[ref]

    # A model's x rotation turns up to north, and its y rotation north to east, a quarter turn at a time.
    TURN_X = {"up": "north", "north": "down", "down": "south", "south": "up"}
    TURN_Y = {"north": "east", "east": "south", "south": "west", "west": "north"}

    def turned(self, d, x, y):
        for _ in range(x // 90 % 4):
            d = self.TURN_X.get(d, d)
        for _ in range(y // 90 % 4):
            d = self.TURN_Y.get(d, d)
        return d

    def colour(self, block, face, state):
        want = "up" if face == "top" else "north"
        faces, culled, particle = [], [], None
        for ref, x, y in self.parts(block, state):
            if not ref:
                continue
            m = self.model(ref)
            particle = particle or m["textures"]
            for e in m["elements"]:
                for d, f in e.get("faces", {}).items():
                    if self.turned(d, x, y) != want:
                        continue
                    faces.append((f, m["textures"]))
                    if f.get("cullface") and self.turned(f["cullface"], x, y) == want:
                        culled.append((f, m["textures"]))
        r = g = b = w = 0
        for f, textures in culled or faces:
            avg = self.average(f.get("texture"), textures)
            if avg:
                r, g, b, w = r + avg[0] * avg[3], g + avg[1] * avg[3], b + avg[2] * avg[3], w + avg[3]
        if w:
            return int(r / w) << 16 | int(g / w) << 8 | int(b / w)
        avg = self.average("#particle", particle or {})
        return avg[0] << 16 | avg[1] << 8 | avg[2] if avg else 0x808080


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


def client_jar():
    jars = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/**/minecraft*client*.jar"), recursive=True))
    for jar in reversed(jars):
        with zipfile.ZipFile(jar) as z:
            if "assets/minecraft/textures/block/stone_bricks.png" in z.namelist():
                return jar
    sys.exit("No Minecraft client jar in ~/.gradle/caches. Run ./gradlew build first.")


def stone_bricks():
    with zipfile.ZipFile(client_jar()) as z:
        return Image.open(io.BytesIO(z.read("assets/minecraft/textures/block/stone_bricks.png"))).convert("RGB")


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
    return solve(stencil=path, trap="false", shelf="false", fence="false", pane="false")


def banner_frame3(name, label, **settings):
    """A banner frame with a 3D shape on the stage, drawn as the menu's 3D preview draws it."""
    img = Image.new("RGB", (W, H), BG)
    pieces(img, name, (COLS - name["nx"]) // 2 * CELL, CELL, color=TEXT)
    grid(img)
    view, scene = solve3(W, STAGE_ROWS * CELL, **settings)
    img.paste(view, (0, STAGE_TOP * CELL), shape_mask(view))
    d = ImageDraw.Draw(img)
    write(d, (2 * CELL, H - CELL), label, 22, CURVE)
    write(d, (W - 2 * CELL, H - CELL), status3(scene), 18, DIM, "r")
    return img


def shape_mask(view):
    """Everything in a 3D picture but its background, so it can go over graph paper."""
    return Image.eval(ImageChops.difference(view, Image.new("RGB", view.size, BG)).convert("L"), lambda v: 255 if v else 0)


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
    flat = len(frames) - 1

    # A torus, turned a quarter of the way round. It looks the same again after that.
    turns = 18
    for n in range(turns + 1):
        frames.append(banner_frame3(name, "Torus, 25 × 9 × 25", gen="TORUS", viewYaw=-35 + 90 * n / turns, viewMargin=10))
        times.append(1700 if n == turns else 80)

    # One palette for every frame, so colours don't shift as the shapes change.
    keys = [still, still + len(amps), still + len(amps) + len(widths), flat, len(frames) - 1]
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
    off = dict(slab="false", stair="false", trap="false", shelf="false", fence="false", pane="false", wall="false")
    cards = []
    for settings in (off, dict(trap="false", shelf="false", fence="false", pane="false")):
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


def stacked(rows, gap):
    out = Image.new("RGBA", (max(r.width for r in rows), sum(r.height for r in rows) + gap * (len(rows) - 1)), (0, 0, 0, 0))
    y = 0
    for r in rows:
        out.paste(r, ((out.width - r.width) // 2, y))
        y += r.height + gap
    return out


def card3(label, w=512, h=384, **settings):
    """A 3D shape in one colour, as the 3D preview's Stone colouring shows it, with its name underneath."""
    img, _ = solve3(w, h, viewColour=STONE, **settings)
    write(ImageDraw.Draw(img), (16, h - 20), label, 20, TEXT)
    return img


def shapes3d():
    """One picture for each 3D tab, and a hollow ellipsoid cut open to show its wall."""
    wave = dict(gen="EQUATION3", src3="z = sin(x) cos(y)", viewMargin=24)
    tube = dict(gen="BEZIER3", b3T=3, viewMargin=24)
    rows = [
        [card3("Ellipsoid", gen="ELLIPSOID", e3W=25, e3H=19, e3D=25, viewMargin=28),
         card3("Hollow, cut open", gen="ELLIPSOID", e3W=25, e3H=19, e3D=25, e3Mode="INWARDS", e3T=2, viewCut="true",
               viewWires="false", viewMargin=28),
         card3("Torus", gen="TORUS", viewMargin=28)],
        [card3("3D equation: z = sin(x) cos(y)", **wave), card3("3D Bézier curve", **tube), card3("Bézier surface", gen="SURFACE", viewMargin=24)],
    ]
    save(stacked([side_by_side(r, 16) for r in rows], 16), "shapes3d.png")


def lab(rgb):
    """CIELAB, as the mod's Lab class works it out."""
    def lin(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin((rgb >> s & 255) / 255) for s in (16, 8, 0))
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
    def f(t):
        return t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116
    return 116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))


def gradient(name, stops, steps):
    """Paint settings for a gradient like one saved in Chroma Studio, which the pictures can't run: the colours run
    evenly between the stops, and each step takes the unused concrete, wool or terracotta closest in colour."""
    colours = {}
    with open(catalog()) as f:
        for line in f:
            w = line.split()
            if w[0] == "side" and any(w[1].endswith(e) for e in ("_concrete", "_wool", "_terracotta")) and "glazed" not in w[1]:
                colours[w[1]] = lab(int(w[2], 16))

    def at(t):
        k = min(int(t * (len(stops) - 1)), len(stops) - 2)
        u = t * (len(stops) - 1) - k
        return sum(round((stops[k] >> s & 255) * (1 - u) + (stops[k + 1] >> s & 255) * u) << s for s in (16, 8, 0))

    used, out = set(), []
    for n in range(steps):
        ideal = at(n / (steps - 1))
        want = lab(ideal)
        block = min((b for b in sorted(colours) if b not in used), key=lambda b: math.dist(colours[b], want))
        used.add(block)
        out.append(f"#{ideal:06x}@{block}")
    return {"paint.source": "GRADIENT", "paint.gradient.name": name,
            "paint.gradient.stops": " ".join(f"#{c:06x}" for c in stops), "paint.gradient.steps": " ".join(out)}


def painted(**paint):
    """Paint settings as a preset's text has them."""
    return {"paint." + k: str(v) for k, v in paint.items()}


# The painted shapes, each with its label on the listing's picture. Thin pieces come from the shape's own materials
# where they can, so trapdoors and shelves are off where they'd take the closest colour from another material.
DOME = dict(gen="ELLIPSOID", e3W=33, e3H=33, e3D=33, e3Mode="INWARDS", viewTop="true", viewPitch=22)
COPPER = {"paint.source": "MIX", "paint.mix.name": "Copper", "paint.mix.blocks": "minecraft:oxidized_cut_copper*5 "
          "minecraft:weathered_cut_copper*4 minecraft:exposed_cut_copper*2 minecraft:cut_copper*1"}
NO_THIN = dict(trap="false", shelf="false")
PAINTED = [
    ("dome", "Stripes on a dome",
     dict(**DOME, **NO_THIN, **painted(source="PATTERN", size=3), paintPattern="STRIPES")),
    ("road", "Two-lane road on a curve",
     dict(gen="BEZIER", bW=72, bH=44, bLW=9, floor="true", pts="5,7 22,46 50,-2 67,34", **painted(source="PATTERN"),
          paintPattern="TWO_LANE_ROAD", viewPitch=50, viewYaw=-20)),
    ("torus", "Gradient, bottom to top",
     dict(gen="TORUS", tRing=33, tTube=11, tW=33, tH=11, tD=33, **NO_THIN, viewPitch=12,
          **gradient("Sunset", [0xFFD34E, 0xF2762E, 0xC0392B, 0x7B2FA0, 0x3C44AA], 10))),
    ("lattice", "Holes: a lattice dome",
     dict(**DOME, **painted(source="PATTERN", size=2, empty="OPEN"), paintPattern="LATTICE")),
    ("spiral", "A stripe turned into a spiral",
     dict(gen="BEZIER3", b3T=9, b3Hollow="true", **NO_THIN, **painted(source="PATTERN", size=2, turn=35), paintPattern="STRIPES")),
    ("mix", "A mix of copper in patches",
     dict(gen="SURFACE", **NO_THIN, **painted(patch=5), **COPPER)),
]


def paint():
    """The painted shapes, as the menu's "Block colours" picture shows them."""
    cards = []
    for _, label, settings in PAINTED:
        img, scene = solve3(512, 384, catalog=catalog(), viewWires="false", viewMargin=24, **settings)
        write(ImageDraw.Draw(img), (16, 364), label, 20, TEXT)
        print(f"  {label}: {status3(scene)}, {scene['painted']['open']} open")
        cards.append(img)
    save(stacked([side_by_side(cards[:3], 16), side_by_side(cards[3:], 16)], 16), "paint.png")


def gallery():
    """The same painted shapes, one to a picture, for the galleries."""
    folder = os.path.join(ROOT, "listing", "gallery")
    for n, (name, _, settings) in enumerate(PAINTED, 8):
        img, _ = solve3(1920, 1080, catalog=catalog(), viewWires="false", viewMargin=90, **settings)
        path = os.path.join(folder, f"{n:02d}-{name}.png")
        img.save(path, optimize=True)
        print(os.path.basename(path), img.size, os.path.getsize(path) // 1024, "KiB")


def editor():
    """The in-world editor's box round an ellipsoid, with its 26 handles in the editor's colours."""
    w, h = 1000, 520
    img, _ = solve3(w, h, gen="ELLIPSOID", e3W=17, e3H=11, e3D=13, viewColour=STONE, viewWires="false", viewHandles="true",
                    viewHandleSize=13, viewMargin=56)
    out = Image.new("RGB", (w + 440, h), BG)
    out.paste(img, (0, 0))
    d = ImageDraw.Draw(out)
    rows = [(FACE, "Face", "moves along one axis"), (EDGE, "Edge", "moves along two axes"),
            (CORNER, "Corner", "moves along two, scroll for the third")]
    for n, (colour, name, does) in enumerate(rows):
        y = h // 2 + (n - 1) * 84
        d.rectangle([w - 20, y - 22, w - 2, y - 4], fill=colour, outline=darken(BG, .6), width=2)
        write(d, (w + 14, y - 13), name, 26, TEXT)
        write(d, (w - 20, y + 20), does, 20, DIM)
    save(rounded(out), "editor.png")


def families():
    """One piece from each family at six times preview size, in the preview's piece colours: full block, slab,
    stairs, trapdoor, shelf, fence, glass pane, wall."""
    wall = next(s for s, p in enumerate(PIECES) if p and p["name"] == "curvegen.piece.wall.low_low.with_post")
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
                  trap="false", shelf="false", fence="false", pane="false", wall="false")
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
    scene = solve(gen="BEZIER", bW=16, bH=16, bLW=1.4, shelf="false", pts=" ".join(f"{x},{y}" for x, y in pts))
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
    shapes3d()
    paint()
    gallery()
    editor()
    families()
    divider()
    icon()
    SOLVER.stdin.close()
