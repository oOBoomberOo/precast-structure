"""Crisp handcrafted 16x16 vanilla-industrial textures (no AI soft blend)."""
from __future__ import annotations

import random
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    import subprocess
    import sys

    subprocess.check_call([sys.executable, "-m", "pip", "install", "pillow", "-q"])
    from PIL import Image

OUT = Path(r"D:\IdeaProjects\precast-structure\common\src\main\resources\assets\precast_structure\textures")
BLOCK = OUT / "block"
ITEM = OUT / "item"
BLOCK.mkdir(parents=True, exist_ok=True)
ITEM.mkdir(parents=True, exist_ok=True)


def clamp(v: int) -> int:
    return max(0, min(255, int(v)))


def n(rgb, amt, rng):
    return tuple(clamp(c + rng.randint(-amt, amt)) for c in rgb)


def put(px, x, y, rgb, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = (*rgb, a)


def rect(px, x0, y0, x1, y1, rgb, rng=None, amt=0):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(px, x, y, n(rgb, amt, rng) if rng and amt else rgb)


def save(img: Image.Image, path: Path):
    assert img.size == (16, 16), img.size
    img.save(path, format="PNG")
    print("wrote", path, img.size)


def platform_floor(rng):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    base, dark, mid, hi = (138, 138, 142), (86, 86, 90), (112, 112, 116), (160, 160, 164)
    riv, riv_h = (64, 64, 68), (150, 150, 154)
    rect(px, 0, 0, 16, 16, base, rng, 5)
    for i in range(16):
        put(px, i, 0, n(dark, 2, rng))
        put(px, i, 15, n(dark, 2, rng))
        put(px, 0, i, n(dark, 2, rng))
        put(px, 15, i, n(dark, 2, rng))
        put(px, i, 1, n(mid, 2, rng))
        put(px, i, 14, n(mid, 2, rng))
        put(px, 1, i, n(mid, 2, rng))
        put(px, 14, i, n(mid, 2, rng))
    for i in range(2, 14):
        put(px, 7, i, n(mid, 2, rng))
        put(px, 8, i, n(hi, 2, rng))
        put(px, i, 7, n(mid, 2, rng))
        put(px, i, 8, n(hi, 2, rng))
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        put(px, x, y, riv)
        put(px, x + 1, y, riv_h)
        put(px, x, y + 1, riv)
        put(px, x + 1, y + 1, riv)
    return img


def perimeter_fence(rng):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    y1, y2 = (184, 148, 42), (156, 122, 30)
    k1, k2 = (28, 28, 30), (48, 48, 50)
    for y in range(16):
        for x in range(16):
            stripe = ((x + y) // 4) % 2 == 0
            if stripe:
                put(px, x, y, n(y1 if (x + y) % 2 == 0 else y2, 3, rng))
            else:
                put(px, x, y, n(k1 if (x + y) % 2 == 0 else k2, 2, rng))
    return img


def metal_scaffold(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    beam, beam_d, beam_h, riv = (96, 96, 102), (68, 68, 74), (124, 124, 130), (44, 44, 48)
    # holes stay fully transparent (cutout render type)
    rect(px, 0, 0, 16, 2, beam, rng, 3)
    rect(px, 0, 14, 16, 16, beam, rng, 3)
    rect(px, 0, 0, 2, 16, beam, rng, 3)
    rect(px, 14, 0, 16, 16, beam, rng, 3)
    rect(px, 7, 2, 9, 14, beam, rng, 3)
    rect(px, 2, 7, 14, 9, beam, rng, 3)
    for i in range(2, 14):
        put(px, i, i, n(beam_h, 2, rng))
        put(px, i, 15 - i, n(beam_h, 2, rng))
        put(px, i, i - 1, n(beam_d, 1, rng))
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(px, x, y, riv)
    # top-left highlight on frame
    for i in range(16):
        put(px, i, 0, n(beam_h, 2, rng))
        put(px, 0, i, n(beam_h, 2, rng))
    return img


def structure_scanner_front(rng, valid=True):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    frame, fd, fh = (58, 62, 66), (36, 38, 42), (86, 90, 96)
    if valid:
        c_h, c, c_d = (148, 214, 218), (72, 168, 174), (36, 108, 118)
    else:
        c_h, c, c_d = (224, 120, 110), (176, 56, 52), (110, 28, 28)
    bolt = (22, 24, 26)
    rect(px, 0, 0, 16, 16, frame, rng, 3)
    for i in range(16):
        put(px, i, 0, fh)
        put(px, 0, i, fh)
        put(px, i, 15, fd)
        put(px, 15, i, fd)
    rect(px, 3, 3, 13, 13, fd)
    palette = (c_d, c, c_h, c, c_d, c)
    for y in range(4, 12):
        for x in range(4, 12):
            put(px, x, y, palette[((x * 5) + (y * 3)) % len(palette)])
    put(px, 7, 7, c_h)
    put(px, 8, 7, c_h)
    put(px, 7, 8, c)
    put(px, 8, 8, c_h)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, x, y, bolt)
    return img


def structure_scanner_side(rng):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    frame, fd, fh, vent = (58, 62, 66), (36, 38, 42), (86, 90, 96), (28, 30, 34)
    bolt = (22, 24, 26)
    rect(px, 0, 0, 16, 16, frame, rng, 3)
    for i in range(16):
        put(px, i, 0, fh)
        put(px, 0, i, fh)
        put(px, i, 15, fd)
        put(px, 15, i, fd)
    # side vents / plating
    for y in (4, 6, 8, 10):
        rect(px, 3, y, 13, y + 1, vent)
        put(px, 3, y, fd)
        put(px, 12, y, fh)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, x, y, bolt)
    return img


def structure_scanner_top(rng):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    frame, fd, fh = (64, 68, 72), (40, 42, 46), (92, 96, 102)
    lens, lens_h = (48, 140, 148), (120, 200, 208)
    bolt = (22, 24, 26)
    rect(px, 0, 0, 16, 16, frame, rng, 3)
    for i in range(16):
        put(px, i, 0, fh)
        put(px, 0, i, fh)
        put(px, i, 15, fd)
        put(px, 15, i, fd)
    # top sensor plate
    rect(px, 3, 3, 13, 13, fd, rng, 2)
    rect(px, 5, 5, 11, 11, lens)
    put(px, 7, 7, lens_h)
    put(px, 8, 7, lens_h)
    put(px, 7, 8, lens)
    put(px, 8, 8, lens_h)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, x, y, bolt)
    return img


def structure_printer_front(rng):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    frame, fd, fh = (66, 56, 76), (40, 32, 48), (96, 84, 110)
    cu, cud, cuh = (170, 100, 54), (122, 70, 38), (198, 130, 74)
    vent, bolt = (26, 22, 32), (20, 16, 26)
    rect(px, 0, 0, 16, 16, frame, rng, 3)
    for i in range(16):
        put(px, i, 0, fh)
        put(px, 0, i, fh)
        put(px, i, 15, fd)
        put(px, 15, i, fd)
    rect(px, 2, 3, 14, 5, cu, rng, 2)
    for x in range(2, 14):
        put(px, x, 3, cuh)
        put(px, x, 4, cud)
    for y in (7, 9, 11):
        rect(px, 4, y, 12, y + 1, vent)
        put(px, 3, y, cud)
        put(px, 12, y, cud)
    rect(px, 6, 13, 10, 15, (44, 36, 52))
    put(px, 7, 13, cuh)
    put(px, 8, 13, cu)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, x, y, bolt)
    return img


def structure_printer_side(rng):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    frame, fd, fh = (66, 56, 76), (40, 32, 48), (96, 84, 110)
    cu, cud = (150, 90, 48), (110, 64, 34)
    panel, bolt = (52, 44, 62), (20, 16, 26)
    rect(px, 0, 0, 16, 16, frame, rng, 3)
    for i in range(16):
        put(px, i, 0, fh)
        put(px, 0, i, fh)
        put(px, i, 15, fd)
        put(px, 15, i, fd)
    # side access panel
    rect(px, 3, 3, 13, 13, panel, rng, 2)
    rect(px, 4, 5, 12, 7, cu)
    for x in range(4, 12):
        put(px, x, 5, cu)
        put(px, x, 6, cud)
    for y in (9, 11):
        rect(px, 5, y, 11, y + 1, fd)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, x, y, bolt)
    return img


def structure_printer_top(rng):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    frame, fd, fh = (72, 60, 84), (44, 34, 54), (104, 90, 118)
    cu, cuh = (170, 100, 54), (198, 130, 74)
    hatch, bolt = (34, 26, 42), (20, 16, 26)
    rect(px, 0, 0, 16, 16, frame, rng, 3)
    for i in range(16):
        put(px, i, 0, fh)
        put(px, 0, i, fh)
        put(px, i, 15, fd)
        put(px, 15, i, fd)
    # top hatch / hopper intake
    rect(px, 3, 3, 13, 13, hatch, rng, 2)
    rect(px, 5, 5, 11, 11, fd)
    rect(px, 6, 6, 10, 10, hatch)
    put(px, 7, 7, cuh)
    put(px, 8, 7, cu)
    put(px, 7, 8, cu)
    put(px, 8, 8, cuh)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(px, x, y, bolt)
    return img


def blueprint(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    paper, pd, ink, inkl = (150, 184, 194), (112, 146, 158), (40, 74, 104), (68, 112, 140)
    rect(px, 1, 1, 14, 15, paper, rng, 2)
    # folded corner
    put(px, 13, 1, (0, 0, 0), 0)
    put(px, 14, 1, (0, 0, 0), 0)
    put(px, 14, 2, (0, 0, 0), 0)
    put(px, 12, 1, pd)
    put(px, 13, 2, pd)
    put(px, 13, 3, pd)
    # grid
    for i in (4, 7, 10):
        for j in range(2, 14):
            if px[i, j][3]:
                put(px, i, j, inkl)
            if px[j, i][3]:
                put(px, j, i, inkl)
    # building doodle
    for x in range(4, 11):
        put(px, x, 12, ink)
    for y in range(8, 12):
        put(px, 4, y, ink)
        put(px, 10, y, ink)
    for x, y in ((5, 7), (6, 6), (7, 5), (8, 6), (9, 7)):
        put(px, x, y, ink)
    put(px, 6, 10, inkl)
    put(px, 7, 10, inkl)
    # dark outline
    for i in range(1, 14):
        if px[i, 1][3]:
            put(px, i, 1, pd)
        if px[1, i][3]:
            put(px, 1, i, pd)
        if px[i, 14][3]:
            put(px, i, 14, pd)
        if px[13, i][3] and i > 2:
            put(px, 13, i, pd)
    return img


def empty_blueprint(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    paper, pd, inkl = (168, 196, 204), (130, 162, 172), (110, 148, 168)
    rect(px, 1, 1, 14, 15, paper, rng, 2)
    put(px, 13, 1, (0, 0, 0), 0)
    put(px, 14, 1, (0, 0, 0), 0)
    put(px, 14, 2, (0, 0, 0), 0)
    put(px, 12, 1, pd)
    put(px, 13, 2, pd)
    put(px, 13, 3, pd)
    for i in (4, 7, 10):
        for j in range(2, 14):
            if px[i, j][3]:
                put(px, i, j, inkl)
            if px[j, i][3]:
                put(px, j, i, inkl)
    for i in range(1, 14):
        if px[i, 1][3]:
            put(px, i, 1, pd)
        if px[1, i][3]:
            put(px, 1, i, pd)
        if px[i, 14][3]:
            put(px, i, 14, pd)
        if px[13, i][3] and i > 2:
            put(px, 13, i, pd)
    return img


def main():
    rng = random.Random(42)
    save(platform_floor(rng), BLOCK / "platform_floor.png")
    save(perimeter_fence(rng), BLOCK / "perimeter_fence.png")
    save(metal_scaffold(rng), BLOCK / "metal_scaffold.png")
    save(structure_scanner_front(rng, valid=True), BLOCK / "structure_scanner_front.png")
    save(structure_scanner_front(rng, valid=False), BLOCK / "structure_scanner_front_invalid.png")
    save(structure_scanner_side(rng), BLOCK / "structure_scanner_side.png")
    save(structure_scanner_top(rng), BLOCK / "structure_scanner_top.png")
    save(structure_printer_front(rng), BLOCK / "structure_printer_front.png")
    save(structure_printer_side(rng), BLOCK / "structure_printer_side.png")
    save(structure_printer_top(rng), BLOCK / "structure_printer_top.png")
    save(blueprint(rng), ITEM / "blueprint.png")
    save(empty_blueprint(rng), ITEM / "empty_blueprint.png")


if __name__ == "__main__":
    main()
