"""Recolour one colour of a PNG (and its anti-aliased tints) into one variant per theme.

Pixels on the line between white and the base colour are moved to the same point on the line between white and the
theme colour, so soft edges and light tints keep their shape. Everything else (text, outlines, other colours) is
left alone. Writes <name>_<theme>.webp (lossless) next to the source, and a preview sheet when --preview is given.

Usage (from the project root):
    python ai/src/main/java/ai/scripts/recolor.py <source.png> <base #hex> <theme=#hex> [<theme=#hex> ...]
        [--from-row N] [--tolerance 35] [--preview build/recolor_preview.png]

Example:
    python ai/src/main/java/ai/scripts/recolor.py app/src/main/res/drawable-nodpi/img_card.png "#0E7ADD" \
        red=#ED3833 green=#4DA957 black=#0D0D0D --preview build/card_preview.png

Needs: Python 3, Pillow (pip install pillow).
Then point each theme at its file with a theme attribute (?attr/cardImage) instead of a code switch.
"""
import argparse
import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Needs Pillow: pip install pillow")


def hex_rgb(h):
    h = h.strip().lstrip('#')
    if len(h) != 6:
        raise argparse.ArgumentTypeError(f"not a #RRGGBB colour: {h}")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def recolor(im, base, target, from_row, tolerance):
    br, bg, bb = base
    tr, tg, tb = target
    px = im.load()
    w, h = im.size
    # the channel with the most room between white and the base colour gives the most precise mix amount
    span = [(255 - c) for c in base]
    ch = max(range(3), key=lambda i: span[i])
    if span[ch] == 0:
        sys.exit("The base colour is white: nothing to recolour.")
    for y in range(from_row, h):
        for x in range(w):
            r, g, b, a = px[x, y]
            t = max(0.0, min(1.0, (255 - (r, g, b)[ch]) / span[ch]))
            if t == 0:
                continue
            # only pixels on the white -> base line (tints and edges of the base colour)
            expect = (255 - t * (255 - br), 255 - t * (255 - bg), 255 - t * (255 - bb))
            if any(abs(v - e) > tolerance for v, e in zip((r, g, b), expect)):
                continue
            px[x, y] = (round(255 - t * (255 - tr)), round(255 - t * (255 - tg)), round(255 - t * (255 - tb)), a)
    return im


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("source")
    ap.add_argument("base", type=hex_rgb)
    ap.add_argument("themes", nargs="+", help="name=#RRGGBB")
    ap.add_argument("--from-row", type=int, default=0, help="leave rows above this alone (e.g. an icon at the top)")
    ap.add_argument("--tolerance", type=int, default=35)
    ap.add_argument("--preview")
    args = ap.parse_args()

    themes = []
    for t in args.themes:
        name, _, colour = t.partition("=")
        if not name or not colour:
            sys.exit(f"Themes are name=#RRGGBB (got {t})")
        themes.append((name, hex_rgb(colour)))

    src = Image.open(args.source).convert("RGBA")
    stem = os.path.splitext(args.source)[0]
    outputs = [("base", src)]
    for name, colour in themes:
        out = recolor(src.copy(), args.base, colour, args.from_row, args.tolerance)
        path = f"{stem}_{name}.webp"
        out.save(path, "WEBP", lossless=True, quality=100, method=6)
        outputs.append((name, out))
        print("wrote", path)

    if args.preview:
        w, h = src.size
        cw, ch = max(1, w // 2), max(1, h // 2)
        sheet = Image.new("RGBA", (cw * len(outputs), ch), "white")
        for i, (_, img) in enumerate(outputs):
            sheet.paste(img.resize((cw, ch)), (i * cw, 0))
        os.makedirs(os.path.dirname(args.preview) or ".", exist_ok=True)
        sheet.save(args.preview)
        print("preview", args.preview)


if __name__ == "__main__":
    main()
