from pathlib import Path
import random

try:
    from PIL import Image
except ImportError:
    import subprocess
    import sys

    subprocess.check_call([sys.executable, "-m", "pip", "install", "pillow", "-q"])
    from PIL import Image

BG = (198, 198, 198)
WHITE = (255, 255, 255)
SHADOW = (55, 55, 55)
MID = (139, 139, 139)
MID_DARK = (85, 85, 85)
BLACK = (0, 0, 0)


def rgba(rgb, a=255):
    return (rgb[0], rgb[1], rgb[2], a)


def clamp(v):
    return max(0, min(255, v))


def noisy(rgb, amount=7, rng=None):
    rng = rng or random
    return (
        clamp(rgb[0] + rng.randint(-amount, amount)),
        clamp(rgb[1] + rng.randint(-amount, amount)),
        clamp(rgb[2] + rng.randint(-amount, amount)),
        255,
    )


def set_px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c if len(c) == 4 else rgba(c))


def fill(img, x, y, w, h, c, grain=False, rng=None):
    for j in range(y, y + h):
        for i in range(x, x + w):
            set_px(img, i, j, noisy(c, 5, rng) if grain else (c if len(c) == 4 else rgba(c)))


def hline(img, x, y, w, c):
    for i in range(x, x + w):
        set_px(img, i, y, c)


def vline(img, x, y, h, c):
    for j in range(y, y + h):
        set_px(img, x, j, c)


def bevel_panel(img, x, y, w, h, rng):
    fill(img, x, y, w, h, BG, grain=True, rng=rng)
    # outer black
    hline(img, x, y, w, rgba(BLACK))
    hline(img, x, y + h - 1, w, rgba(BLACK))
    vline(img, x, y, h, rgba(BLACK))
    vline(img, x + w - 1, y, h, rgba(BLACK))
    # highlight
    hline(img, x + 1, y + 1, w - 2, rgba(WHITE))
    vline(img, x + 1, y + 1, h - 2, rgba(WHITE))
    # shadow
    hline(img, x + 1, y + h - 2, w - 2, rgba(SHADOW))
    vline(img, x + w - 2, y + 1, h - 2, rgba(SHADOW))
    # corner fix
    set_px(img, x + 1, y + h - 2, rgba(SHADOW))
    set_px(img, x + w - 2, y + 1, rgba(SHADOW))


def draw_slot(img, x, y, rng):
    # Vanilla-style recessed slot: dark TL, light BR, gray fill with light grain
    fill(img, x, y, 18, 18, MID, grain=True, rng=rng)
    hline(img, x, y, 17, rgba(SHADOW))
    vline(img, x, y, 17, rgba(SHADOW))
    hline(img, x + 1, y + 17, 17, rgba(WHITE))
    vline(img, x + 17, y + 1, 17, rgba(WHITE))
    set_px(img, x, y + 17, rgba(MID))
    set_px(img, x + 17, y, rgba(MID))
    # inner top-left shade for depth
    hline(img, x + 1, y + 1, 15, rgba(MID_DARK))
    vline(img, x + 1, y + 1, 15, rgba(MID_DARK))


def draw_arrow(img, x, y, filled=False, background=None):
    """Vanilla furnace progress arrow (24x16), pixel-matched to furnace.png UV."""
    empty_c = (139, 139, 139, 255)
    full_c = (255, 255, 255, 255)
    shade_c = (104, 104, 104, 255)  # vanilla #686868

    if background is None:
        fill(img, x, y, 24, 16, (0, 0, 0, 0))
    else:
        fill(img, x, y, 24, 16, background)

    # Exact filled-arrow mask from vanilla furnace.png @ (176, 14).
    # 'w' = white body, 's' = trailing shade. Empty uses the same mask in mid-gray.
    pixels = {}
    for col in range(15, 16):
        pixels[(0, col)] = "w"
    for col in range(15, 17):
        pixels[(1, col)] = "w"
    for col in range(15, 18):
        pixels[(2, col)] = "w"
    for col in range(15, 19):
        pixels[(3, col)] = "w"
    for col in range(15, 20):
        pixels[(4, col)] = "w"
    for col in range(15, 21):
        pixels[(5, col)] = "w"
    for col in range(1, 22):
        pixels[(6, col)] = "w"
    for col in range(1, 23):
        pixels[(7, col)] = "w"
    for col in range(1, 22):
        pixels[(8, col)] = "w"
    pixels[(8, 22)] = "s"
    for col in range(1, 15):
        pixels[(9, col)] = "s"
    for col in range(15, 21):
        pixels[(9, col)] = "w"
    pixels[(9, 21)] = "s"
    for col, kind in ((15, "w"), (16, "w"), (17, "w"), (18, "w"), (19, "w"), (20, "s")):
        pixels[(10, col)] = kind
    for col, kind in ((15, "w"), (16, "w"), (17, "w"), (18, "w"), (19, "s")):
        pixels[(11, col)] = kind
    for col, kind in ((15, "w"), (16, "w"), (17, "w"), (18, "s")):
        pixels[(12, col)] = kind
    for col, kind in ((15, "w"), (16, "w"), (17, "s")):
        pixels[(13, col)] = kind
    for col, kind in ((15, "w"), (16, "s")):
        pixels[(14, col)] = kind
    pixels[(15, 15)] = "s"

    for (row, col), kind in pixels.items():
        if filled:
            set_px(img, x + col, y + row, full_c if kind == "w" else shade_c)
        else:
            set_px(img, x + col, y + row, empty_c)


def inset_field(img, x, y, w, h, rng):
    fill(img, x, y, w, h, MID_DARK, grain=True, rng=rng)
    hline(img, x, y, w, rgba(SHADOW))
    vline(img, x, y, h, rgba(SHADOW))
    hline(img, x, y + h - 1, w, rgba(WHITE))
    vline(img, x + w - 1, y, h, rgba(WHITE))


out = Path(r"D:\IdeaProjects\precast-structure\common\src\main\resources\assets\precast_structure\textures\gui")
out.mkdir(parents=True, exist_ok=True)
rng = random.Random(42)

printer = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
# Compact baseline (166px). Screen stretches the middle when more material rows are needed.
bevel_panel(printer, 0, 0, 176, 166, rng)

# Fixed slots only: blueprint, output, player inventory. Material slots are drawn dynamically.
slots = [(26, 35), (152, 35)]
for row in range(3):
    for col in range(9):
        slots.append((8 + col * 18, 84 + row * 18))
for col in range(9):
    slots.append((8 + col * 18, 142))
# Vanilla: 18x18 slot art is drawn at (slotX - 1, slotY - 1); items render at (slotX, slotY).
for sx, sy in slots:
    draw_slot(printer, sx - 1, sy - 1, rng)

# Slot sprite for dynamic material slots
draw_slot(printer, 176, 32, rng)

# Scrollbar handle (6x15) for materials list
def draw_scrollbar_handle(img, x, y):
    fill(img, x, y, 6, 15, (0, 0, 0, 0))
    face = (198, 198, 198)
    fill(img, x, y, 6, 15, face)
    hline(img, x, y, 6, rgba(WHITE))
    vline(img, x, y, 15, rgba(WHITE))
    hline(img, x, y + 14, 6, rgba(SHADOW))
    vline(img, x + 5, y, 15, rgba(SHADOW))


draw_scrollbar_handle(printer, 176, 50)

# Progress arrow on panel + UV sprites
draw_arrow(printer, 176, 0, filled=False)
draw_arrow(printer, 176, 16, filled=True)
draw_arrow(printer, 127, 35, filled=False, background=BG)
printer.save(out / "structure_printer.png")

rng2 = random.Random(7)
scanner = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
bevel_panel(scanner, 0, 0, 176, 96, rng2)
# Match StructureScannerScreen widgets: field (12,28)-(164,48), button (12,58)-(164,78)
inset_field(scanner, 12, 28, 152, 20, rng2)
fill(scanner, 12, 58, 152, 20, BG, grain=True, rng=rng2)
hline(scanner, 12, 58, 152, rgba(WHITE))
vline(scanner, 12, 58, 20, rgba(WHITE))
hline(scanner, 12, 77, 152, rgba(SHADOW))
vline(scanner, 163, 58, 20, rgba(SHADOW))

# Green checkmark icon at UV 176,0 (16x16) for ready status
def draw_checkmark(img, x, y):
    fill(img, x, y, 16, 16, (0, 0, 0, 0))
    green = (60, 170, 60, 255)
    green_d = (30, 110, 30, 255)
    # stem of check
    check = [
        (3, 8), (4, 9), (5, 10), (6, 11), (7, 12),
        (8, 11), (9, 10), (10, 9), (11, 8), (12, 7), (13, 6), (14, 5),
    ]
    for cx, cy in check:
        set_px(img, x + cx, y + cy, green)
        set_px(img, x + cx, y + cy - 1, green_d)


def draw_close_button(img, x, y, hovered=False):
    """12x12 vanilla-style close button with red X."""
    fill(img, x, y, 12, 12, (0, 0, 0, 0))
    face = (198, 198, 198) if not hovered else (230, 230, 230)
    face_d = (139, 139, 139) if not hovered else (170, 170, 170)
    fill(img, x, y, 12, 12, face)
    hline(img, x, y, 12, rgba(BLACK))
    hline(img, x, y + 11, 12, rgba(BLACK))
    vline(img, x, y, 12, rgba(BLACK))
    vline(img, x + 11, y, 12, rgba(BLACK))
    hline(img, x + 1, y + 1, 10, rgba(WHITE))
    vline(img, x + 1, y + 1, 10, rgba(WHITE))
    hline(img, x + 1, y + 10, 10, rgba(SHADOW))
    vline(img, x + 10, y + 1, 10, rgba(SHADOW))
    # slight inner face darkening
    for yy in range(y + 2, y + 10):
        for xx in range(x + 2, x + 10):
            set_px(img, xx, yy, rgba(face_d if (xx + yy) % 5 == 0 else face))
    red = (200, 40, 40, 255) if not hovered else (255, 70, 70, 255)
    red_d = (120, 20, 20, 255) if not hovered else (160, 30, 30, 255)
    # X diagonals
    for i in range(2, 10):
        set_px(img, x + i, y + i, red)
        set_px(img, x + i, y + 11 - i, red)
        set_px(img, x + i - 1, y + i, red_d)
        set_px(img, x + i + 1, y + 11 - i, red_d)


draw_checkmark(scanner, 176, 0)
draw_close_button(scanner, 176, 16, hovered=False)
draw_close_button(scanner, 176, 28, hovered=True)
scanner.save(out / "structure_scanner.png")
print("regenerated gui textures")
