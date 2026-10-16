"""Coarse screen inspector for emulator screenshots.

Prints a downsampled colour map so a text-only agent can reason about layout, and
can sample the mean colour of a rectangle - used to verify button colours
objectively instead of by eye.

Usage:
  python peek.py map <png> [cols] [rows]
  python peek.py rect <png> <x1> <y1> <x2> <y2>
"""

import sys
from PIL import Image

NAMED = [
    ((0, 0, 0), "K"),  # black
    ((255, 255, 255), "W"),  # white
]


def load(path):
    return Image.open(path).convert("RGB")


def classify(r, g, b):
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    if lum < 40:
        return "K"  # near black
    if lum > 225:
        return "W"  # near white
    # Warm/red accent versus the app's red brand colour
    if r > 150 and g < 120 and b < 140:
        return "R"
    if b > 140 and b > r + 30:
        return "B"
    return "."


def show_map(img, cols, rows):
    w, h = img.size
    print(f"size {w}x{h}")
    small = img.resize((cols, rows), Image.BOX)
    px = small.load()
    for y in range(rows):
        line = "".join(classify(*px[x, y]) for x in range(cols))
        print(f"{y:>3} {line}")


def rect(img, x1, y1, x2, y2):
    crop = img.crop((x1, y1, x2, y2))
    px = list(crop.getdata())
    n = len(px)
    mean = tuple(round(sum(c[i] for c in px) / n) for i in range(3))
    print(f"rect {(x1, y1, x2, y2)} mean_rgb={mean} classify={classify(*mean)}")


if __name__ == "__main__":
    mode = sys.argv[1]
    image = load(sys.argv[2])
    if mode == "map":
        show_map(image, int(sys.argv[3]) if len(sys.argv) > 3 else 60, int(sys.argv[4]) if len(sys.argv) > 4 else 26)
    elif mode == "rect":
        rect(image, *[int(v) for v in sys.argv[3:7]])
