"""Generates the Stream Anything logo, launcher icons, and Android TV banner.

The glyph combines two Material Symbols Outlined icons (FILL 0, wght 400, GRAD 0, opsz 24):
"all_inclusive" (the infinity loop) with "play_circle"'s play triangle, minus its circle, sitting
inside the loop's right half — "play, endlessly".

Outputs follow the sibling apps' conventions: #2A7FFF glyph on white, a monochrome variant for
themed icons, assets/logo.svg, and design/ PNGs for the Play Store. On top of that, a 320x180dp
banner for the Android TV / Google TV launcher row.

Usage: python scripts/generate_icons.py   (PNG output needs resvg-py: pip install resvg-py; the
Android vector drawables are written either way)
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
BLUE = "#2A7FFF"

# all_inclusive (the infinity loop), viewBox 0 -960 960 960.
INFINITY = (
    "M220,-260q-92,0 -156,-64T0,-480q0,-92 64,-156t156,-64q37,0 71,13t61,37l68,62 -60,54 -62,-56"
    "q-16,-14 -36,-22t-42,-8q-58,0 -99,41t-41,99q0,58 41,99t99,41q22,0 42,-8t36,-22l310,-280"
    "q27,-24 61,-37t71,-13q92,0 156,64t64,156q0,92 -64,156t-156,64q-37,0 -71,-13t-61,-37l-68,-62"
    " 60,-54 62,56q16,14 36,22t42,8q58,0 99,-41t41,-99q0,-58 -41,-99t-99,-41q-22,0 -42,8t-36,22"
    "L352,-310q-27,24 -61,37t-71,13Z"
)

# play_circle's triangle without the circle, scaled to 0.55 and centered in the infinity's
# right loop (center 740,-480, inner radius 140) — nudged 2 units left for optical balance.
PLAY = "M686.7,-381.0L840.7,-480.0L686.7,-579.0Z"

GLYPH = INFINITY + PLAY


def vector(width_dp, viewport, body, height_dp=None, viewport_height=None):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{width_dp}dp"\n'
        f'    android:height="{height_dp or width_dp}dp"\n'
        f'    android:viewportWidth="{viewport}"\n'
        f'    android:viewportHeight="{viewport_height or viewport}">\n'
        f"{body}"
        "</vector>\n"
    )


def path(color, indent="    "):
    return (
        f"{indent}<path\n"
        f'{indent}    android:fillColor="{color}"\n'
        f'{indent}    android:pathData="{GLYPH}" />\n'
    )


def group(scale, tx, ty, color):
    return (
        "    <group\n"
        f'        android:scaleX="{scale}"\n'
        f'        android:scaleY="{scale}"\n'
        f'        android:translateX="{tx}"\n'
        f'        android:translateY="{ty}">\n'
        f"{path(color, '        ')}"
        "    </group>\n"
    )


def rect(color, w, h):
    return f'    <path\n        android:fillColor="{color}"\n        android:pathData="M0,0h{w}v{h}h-{w}z" />\n'


def write(rel, text):
    target = RES / rel if not rel.startswith("/") else ROOT / rel[1:]
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8", newline="\n")


def main():
    # Adaptive icon: 108dp canvas with the glyph inside the 66dp safe zone (same placement as
    # the sibling apps).
    write("drawable/ic_launcher_foreground.xml", vector(108, 1200, group(0.5625, 330, 870, BLUE)))
    write("drawable/ic_launcher_monochrome.xml", vector(108, 1200, group(0.5625, 330, 870, "#FFFFFFFF")))
    write("drawable-nodpi/splash_icon.xml", vector(108, 1200, group(0.5625, 330, 870, BLUE)))
    write("drawable/ic_launcher_background.xml", vector(108, 108, rect("#FFFFFFFF", 108, 108)))
    write("drawable-nodpi/ic_app_icon.xml", vector(48, 1200, rect("#FFFFFFFF", 1200, 1200) + group(0.8125, 210, 990, BLUE)))
    write("drawable-nodpi/ic_logo_mark.xml", vector(48, 960, '    <group android:translateY="960">\n' + path(BLUE, "        ") + "    </group>\n"))

    # TV banner: 320x180dp, white like the launcher icon. The glyph is wide and short, so it's
    # sized by width (192dp) and centered on the glyph's vertical middle (y = -480).
    banner_scale = 192 / 960
    banner_tx = (320 - 192) / 2
    banner_ty = 90 + 480 * banner_scale
    write(
        "drawable-nodpi/tv_banner.xml",
        vector(
            320, 320,
            rect("#FFFFFFFF", 320, 180) + group(round(banner_scale, 5), banner_tx, banner_ty, BLUE),
            height_dp=180, viewport_height=180,
        ),
    )

    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    )
    write("mipmap-anydpi-v26/ic_launcher.xml", adaptive)
    write("mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)

    logo = f'<svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px" fill="{BLUE}"><path d="{GLYPH}"/></svg>\n'
    write("/assets/logo.svg", logo)

    try:
        import resvg_py
    except ImportError as e:
        print(f"Skipping design/ PNGs ({e})")
        return

    def png(svg, out):
        (ROOT / "design").mkdir(exist_ok=True)
        (ROOT / "design" / out).write_bytes(bytes(resvg_py.svg_to_bytes(svg_string=svg)))

    # Play Store icon: full-bleed white square, glyph at the same proportion as the launcher icon.
    png(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 1200 1200">'
        f'<rect width="1200" height="1200" fill="#fff"/>'
        f'<g transform="translate(210 990) scale(0.8125)"><path fill="{BLUE}" d="{GLYPH}"/></g></svg>',
        "logo-playstore-512.png",
    )
    png(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="960" height="960" viewBox="0 -960 960 960">'
        f'<path fill="{BLUE}" d="{GLYPH}"/></svg>',
        "logo-source.png",
    )
    # Play Console TV banner asset (1280x720).
    png(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 320 180">'
        f'<rect width="320" height="180" fill="#fff"/>'
        f'<g transform="translate({banner_tx} {banner_ty}) scale({banner_scale})"><path fill="{BLUE}" d="{GLYPH}"/></g></svg>',
        "tv-banner-1280x720.png",
    )


if __name__ == "__main__":
    main()
