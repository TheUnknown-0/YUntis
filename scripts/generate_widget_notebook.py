"""Generates the paper tiles of the Schulheft (notebook) widget style.

Ruled like the app (a line every 24 dp) or squared (16 dp), in the app's ruling colour,
at xxhdpi (3 px per dp). Run again after changing the look:
    python scripts/generate_widget_notebook.py

Note: widgets only get system fonts, so the style cannot use the app's Kalam handwriting;
it uses the system handwriting ("casual") through a TypefaceSpan instead.
"""
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
TILES = ROOT / "app/src/main/res/drawable-xxhdpi"
LINE = (0x6A, 0x95, 0xB0, 66)

TILES.mkdir(exist_ok=True)

lined = Image.new("RGBA", (24, 72), (0, 0, 0, 0))
ImageDraw.Draw(lined).rectangle([0, 70, 23, 71], fill=LINE)
lined.save(TILES / "widget_paper_lined_tile.png")

squared = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
d = ImageDraw.Draw(squared)
d.rectangle([0, 46, 47, 47], fill=LINE)
d.rectangle([46, 0, 47, 47], fill=LINE)
squared.save(TILES / "widget_paper_squared_tile.png")
print("tiles written")
