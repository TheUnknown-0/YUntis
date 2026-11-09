"""Generates the detailed YUntis logo: a calligraphic Y whose stem is a pen grip ending in a fountain pen nib.

Usage: python make_logo.py <repo root> [--preview <dir>]
"""
import sys
from pathlib import Path

BASE = "#F65D31"
DARK = "#C9441D"
DARKER = "#A3341A"
LIGHT = "#FF9670"
NIB = "#FF7A4D"
NIB_SHADE = "#E0562B"

# Whole silhouette of the Y: left arm (thin head, thick at the fork), right arm with a round head,
# the stem down to the grip end, and the nib.
SILHOUETTE = (
    "M104,104 C92,98 96,80 118,80 "            # left head
    "C184,100 230,156 256,222 "                # left arm, upper edge to the notch
    "C282,168 330,110 372,80 "                 # right arm, inner edge
    "C392,62 420,46 440,56 C456,64 452,88 434,94 "  # right head
    "C382,124 322,192 288,262 "                # right arm, outer edge
    "L282,268 L282,334 "                       # stem, right side
    "L300,344 C304,388 282,428 256,476 "       # nib, right
    "C230,428 208,388 212,344 "                # nib, left
    "L230,334 L230,268 "                       # stem, left side
    "C206,206 160,140 110,116 C106,113 104,108 104,104 Z"  # left arm, lower edge
)

# Breather hole and slit, cut out of the nib.
HOLE = "M265,398 A9,9 0 1,0 247,398 A9,9 0 1,0 265,398 Z"
SLIT = "M253,409 L259,409 L259,478 L253,478 Z"

LAYERS = [
    # shadow edges of the arms
    ("fill", DARK, "M434,94 C382,124 322,192 288,262 L274,256 C304,196 362,128 418,98 Z"),
    ("fill", DARK, "M230,268 C206,206 160,140 110,116 L116,106 C166,132 214,196 244,258 Z"),
    # pen grip: two light rings and a dark end ring above the nib
    ("fill", LIGHT, "M231,284 L281,284 L281,291 L231,291 Z"),
    ("fill", LIGHT, "M231,300 L281,300 L281,307 L231,307 Z"),
    ("fill", DARKER, "M230,322 L282,322 L282,336 L230,336 Z"),
    # nib, two-tone with the cutouts
    ("nib", NIB, "M212,344 L300,344 C304,388 282,428 256,476 C230,428 208,388 212,344 Z"),
    ("nib", NIB_SHADE, "M256,344 L300,344 C304,388 282,428 256,476 Z"),
]

STROKES = [
    # highlights along the upper edges of the arms
    (LIGHT, 6, "M126,88 C178,110 220,162 246,214"),
    (LIGHT, 6, "M268,212 C294,164 334,116 372,92"),
    # engraving and shoulder lines of the nib
    (DARKER, 4, "M232,360 C244,374 268,374 280,360"),
    (DARKER, 4, "M222,352 C226,392 238,420 250,446"),
    (DARKER, 4, "M290,352 C286,392 274,420 262,446"),
]

INK_DROP = "M256,482 C262,490 266,496 266,501 C266,507 261,511 256,511 C251,511 246,507 246,501 C246,496 250,490 256,482 Z"


def svg_detailed() -> str:
    parts = [f'  <path d="{SILHOUETTE}" fill="{BASE}"/>']
    for kind, color, d in LAYERS:
        if kind == "nib":
            parts.append(f'  <path d="{d} {HOLE} {SLIT}" fill="{color}" fill-rule="evenodd"/>')
        else:
            parts.append(f'  <path d="{d}" fill="{color}"/>')
    for color, width, d in STROKES:
        parts.append(f'  <path d="{d}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round"/>')
    parts.append(f'  <path d="{INK_DROP}" fill="{DARKER}"/>')
    # the cutouts must also cut the base silhouette below the nib layers
    return parts


def svg(detailed: bool = True, background: str | None = None) -> str:
    body = []
    if background:
        body.append(f'  <rect width="512" height="512" fill="{background}"/>')
    if detailed:
        body.append(f'  <path d="{SILHOUETTE} {HOLE} {SLIT}" fill="{BASE}" fill-rule="evenodd"/>')
        body += svg_detailed()[1:]
    else:
        body.append(f'  <path d="{SILHOUETTE} {HOLE} {SLIT}" fill="{BASE}" fill-rule="evenodd"/>')
        body.append(f'  <path d="{INK_DROP}" fill="{BASE}"/>')
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">\n'
        + "\n".join(body)
        + "\n</svg>\n"
    )


def android_body(indent: str, detailed: bool, mono: str | None = None) -> str:
    def fill(color, d, even_odd=False):
        extra = f'\n{indent}    android:fillType="evenOdd"' if even_odd else ""
        return f'{indent}<path\n{indent}    android:fillColor="{color}"{extra}\n{indent}    android:pathData="{d}" />\n'

    def stroke(color, width, d):
        return (
            f'{indent}<path\n{indent}    android:strokeColor="{color}"\n{indent}    android:strokeWidth="{width}"\n'
            f'{indent}    android:strokeLineCap="round"\n{indent}    android:pathData="{d}" />\n'
        )

    if not detailed:
        color = mono or BASE
        return fill(color, f"{SILHOUETTE} {HOLE} {SLIT}", True) + fill(color, INK_DROP)

    out = fill(BASE, f"{SILHOUETTE} {HOLE} {SLIT}", True)
    for kind, color, d in LAYERS:
        out += fill(color, f"{d} {HOLE} {SLIT}", True) if kind == "nib" else fill(color, d)
    for color, width, d in STROKES:
        out += stroke(color, width, d)
    return out + fill(DARKER, INK_DROP)


def vector(size_dp: int, detailed: bool, scale: float | None = None, mono: str | None = None) -> str:
    head = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- YUntis logo (generated): a calligraphic Y, its stem a pen grip ending in a fountain pen nib. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
        '    android:viewportWidth="512"\n    android:viewportHeight="512">\n'
    )
    if scale is None:
        return head + android_body("    ", detailed, mono) + "</vector>\n"
    return (
        head
        + "    <!-- Scaled down so the logo stays inside the adaptive icon safe zone. -->\n"
        + f'    <group\n        android:scaleX="{scale}"\n        android:scaleY="{scale}"\n'
        + '        android:pivotX="256"\n        android:pivotY="256">\n'
        + android_body("        ", detailed, mono)
        + "    </group>\n</vector>\n"
    )


ADAPTIVE = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@drawable/ic_launcher_background" />\n'
    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
    '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
    "</adaptive-icon>\n"
)

if __name__ == "__main__":
    root = Path(sys.argv[1])

    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        out.mkdir(parents=True, exist_ok=True)
        (out / "light.svg").write_text(svg(True, "#FFFFFF"), encoding="utf-8")
        (out / "dark.svg").write_text(svg(True, "#1C1B1F"), encoding="utf-8")
        (out / "mono.svg").write_text(svg(False, "#FFFFFF"), encoding="utf-8")
        print("preview written")
        sys.exit(0)

    files = {
        "logo.svg": svg(True),
        "app/src/main/res/drawable/app_logo.xml": vector(48, True),
        "app/src/main/res/drawable/ic_launcher_foreground.xml": vector(108, True, 0.62),
        "app/src/main/res/drawable/ic_launcher_monochrome.xml": vector(108, False, 0.62, "#000000"),
        "app/src/main/res/drawable/ic_notification.xml": vector(24, False, None, "#FFFFFFFF"),
        "wear/src/main/res/drawable/ic_launcher_foreground.xml": vector(108, True, 0.62),
        "wear/src/main/res/drawable/ic_launcher_monochrome.xml": vector(108, False, 0.62, "#000000"),
    }
    for module in ("app", "wear"):
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            files[f"{module}/src/main/res/mipmap-anydpi/{name}"] = ADAPTIVE

    for rel, content in files.items():
        (root / rel).write_text(content, encoding="utf-8", newline="\n")
        print("wrote", rel)
