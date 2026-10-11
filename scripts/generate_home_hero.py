"""Generates the home screen hero image: a tilted, edge-faded "marquee" of the supported
services' logos on rounded cards, with the app's play badge in the middle.

Inputs: the service logos in design/service-logos/ (SVG or PNG, as supplied by each service).
Logos made for dark backgrounds (white wordmarks) sit on dark cards and the rest on light
cards, so every logo stays legible on both the light and dark app themes.

Output: app/src/main/res/drawable-nodpi/home_services.png (transparent background).

Usage: python scripts/generate_home_hero.py   (needs Pillow and resvg-py)
"""

import io
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
LOGOS = ROOT / "design" / "service-logos"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "home_services.png"

SS = 2                       # supersampling factor; everything is drawn at 2x then downsampled
WIDTH, HEIGHT = 1400, 640    # final size in px
BLUE = (42, 127, 255, 255)   # #2A7FFF, the app's brand color

LIGHT_CARD = (255, 255, 255, 255)
LIGHT_BORDER = (226, 230, 238, 255)
DARK_CARD = (23, 25, 33, 255)
DARK_BORDER = (52, 56, 70, 255)

CARD_HEIGHT = 104
CARD_PAD_X = 34
CARD_GAP = 30
ROW_GAP = 34
TILT_DEGREES = 7

# name: (file, logo height in px on the card, dark card?)
SERVICES = {
    "veev": ("veev.png", 54, False),
    "mixdrop": ("mixdrop.png", 38, True),
    "streamtape": ("streamtape.png", 56, False),
    "voe": ("voe.svg", 50, False),
    "vidhide": ("vidhide.svg", 40, True),
    "streamwish": ("streamwish.svg", 50, False),
    "lulustream": ("lulustream.svg", 40, True),
}

# Three rows, laid out outwards from the center: (center card or None for the play badge's
# gap, cards going left, cards going right, row shift for the stagger). The clearly visible ring
# around the badge shows each service once; repeats only appear out in the faded edges.
ROWS = [
    ("streamtape", ["voe", "lulustream", "streamwish"], ["veev", "vidhide", "mixdrop"], 70),
    (None, ["vidhide", "streamwish", "voe"], ["lulustream", "mixdrop", "veev"], 0),
    ("streamwish", ["mixdrop", "veev", "lulustream"], ["voe", "streamtape", "vidhide"], -70),
]
BADGE_GAP = 230   # width of the hole left in the middle row for the play badge


def render_svg(path: Path, height_px: int) -> Image.Image:
    import resvg_py

    svg = path.read_text(encoding="utf-8")
    svg = re.sub(r"<\?xml[^>]*>|<!--.*?-->", "", svg, flags=re.S)
    tag = re.search(r"<svg\b[^>]*>", svg).group(0)
    view_box = re.search(r'viewBox="([^"]+)"', tag)
    if view_box:
        _, _, vw, vh = (float(v) for v in view_box.group(1).replace(",", " ").split())
    else:
        vw = float(re.search(r'\bwidth="([\d.]+)', tag).group(1))
        vh = float(re.search(r'\bheight="([\d.]+)', tag).group(1))
    scale = height_px * 4 / vh   # render big, then trim and resize precisely
    new_tag = re.sub(r'\s(width|height)="[^"]*"', "", tag)
    new_tag = new_tag.replace("<svg", f'<svg width="{vw * scale:.0f}" height="{vh * scale:.0f}"'
                              + ("" if view_box else f' viewBox="0 0 {vw} {vh}"'), 1)
    svg = svg.replace(tag, new_tag, 1)
    png = bytes(resvg_py.svg_to_bytes(svg_string=svg))
    return Image.open(io.BytesIO(png)).convert("RGBA")


def load_logo(file: str, height_px: int) -> Image.Image:
    path = LOGOS / file
    image = render_svg(path, height_px) if path.suffix == ".svg" else Image.open(path).convert("RGBA")
    image = image.crop(image.getbbox())
    width = round(image.width * height_px / image.height)
    return image.resize((width, height_px), Image.LANCZOS)


def card(logo: Image.Image, dark: bool) -> Image.Image:
    """A pill-shaped card with a soft shadow; returned with shadow margin included."""
    w = logo.width + 2 * CARD_PAD_X * SS
    h = CARD_HEIGHT * SS
    margin = 24 * SS
    tile = Image.new("RGBA", (w + 2 * margin, h + 2 * margin), (0, 0, 0, 0))

    shadow = Image.new("L", tile.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle(
        (margin, margin + 8 * SS, margin + w, margin + h + 8 * SS), radius=h // 2, fill=70 if dark else 46,
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(12 * SS))
    tile.paste(Image.new("RGBA", tile.size, (8, 12, 28, 255)), mask=shadow)

    draw = ImageDraw.Draw(tile)
    draw.rounded_rectangle(
        (margin, margin, margin + w, margin + h), radius=h // 2,
        fill=DARK_CARD if dark else LIGHT_CARD, outline=DARK_BORDER if dark else LIGHT_BORDER, width=2 * SS,
    )
    tile.alpha_composite(logo, (margin + CARD_PAD_X * SS, margin + (h - logo.height) // 2))
    return tile


def play_badge(size: int) -> Image.Image:
    """The app's play mark: a blue disc with a white ring, a white triangle, and a soft glow."""
    margin = size // 2
    badge = Image.new("RGBA", (size + 2 * margin, size + 2 * margin), (0, 0, 0, 0))
    glow = Image.new("L", badge.size, 0)
    ImageDraw.Draw(glow).ellipse((margin, margin + size // 14, margin + size, margin + size + size // 14), fill=150)
    glow = glow.filter(ImageFilter.GaussianBlur(size // 7))
    badge.paste(Image.new("RGBA", badge.size, (20, 70, 200, 255)), mask=glow)

    draw = ImageDraw.Draw(badge)
    ring = size // 16
    draw.ellipse((margin, margin, margin + size, margin + size), fill=(255, 255, 255, 255))
    draw.ellipse((margin + ring, margin + ring, margin + size - ring, margin + size - ring), fill=BLUE)
    c = margin + size / 2
    r = size * 0.24
    draw.polygon([(c - r * 0.62, c - r), (c - r * 0.62, c + r), (c + r * 1.0, c)], fill=(255, 255, 255, 255))
    return badge


def main():
    logos = {name: (load_logo(file, height * SS), dark) for name, (file, height, dark) in SERVICES.items()}
    cards = {name: card(logo, dark) for name, (logo, dark) in logos.items()}

    # Lay the rows out on an oversized canvas, then tilt it, so rotation never shows edges.
    big_w, big_h = WIDTH * SS * 2, HEIGHT * SS * 2
    sheet = Image.new("RGBA", (big_w, big_h), (0, 0, 0, 0))
    row_pitch = (CARD_HEIGHT + ROW_GAP) * SS
    center_x, center_y = big_w // 2, big_h // 2

    def place(name, x, y):
        tile = cards[name]
        margin = (tile.height - CARD_HEIGHT * SS) // 2
        sheet.alpha_composite(tile, (x - margin, y - margin))
        return tile.width - 2 * margin   # the card's own width, without its shadow margin

    for row, (center, lefts, rights, shift) in enumerate(ROWS):
        y = center_y + (row - 1) * row_pitch - CARD_HEIGHT * SS // 2
        if center is None:
            half = BADGE_GAP * SS // 2
        else:
            width = cards[center].width - (cards[center].height - CARD_HEIGHT * SS)
            half = width // 2
            place(center, center_x + shift * SS - half, y)
        right_edge = center_x + shift * SS + half
        left_edge = center_x + shift * SS - half
        for name in rights:
            right_edge += CARD_GAP * SS
            right_edge += place(name, right_edge, y)
        for name in lefts:
            tile = cards[name]
            width = tile.width - (tile.height - CARD_HEIGHT * SS)
            left_edge -= CARD_GAP * SS + width
            place(name, left_edge, y)
    sheet = sheet.rotate(TILT_DEGREES, resample=Image.BICUBIC, center=(big_w // 2, big_h // 2))
    left = (big_w - WIDTH * SS) // 2
    upper = (big_h - HEIGHT * SS) // 2
    art = sheet.crop((left, upper, left + WIDTH * SS, upper + HEIGHT * SS))

    # Fade the left/right edges (marquee feel) and, gently, the top/bottom.
    w, h = art.size
    fade = Image.new("L", (w, h), 255)
    px = fade.load()
    edge_x, edge_y = int(w * 0.17), int(h * 0.12)
    for x in range(w):
        fx = min(1.0, min(x, w - 1 - x) / edge_x)
        for y in range(h):
            fy = min(1.0, min(y, h - 1 - y) / edge_y)
            px[x, y] = int(255 * (fx ** 2.2) * (fy ** 1.2))
    art.putalpha(ImageChops.multiply(art.getchannel("A"), fade))

    badge = play_badge(150 * SS)
    art.alpha_composite(badge, ((w - badge.width) // 2, (h - badge.height) // 2))

    final = art.resize((WIDTH, HEIGHT), Image.LANCZOS)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    final.save(OUT, optimize=True)
    print(f"{OUT.relative_to(ROOT)}  {final.size[0]}x{final.size[1]}  {OUT.stat().st_size / 1024:.0f} KB")


if __name__ == "__main__":
    main()
