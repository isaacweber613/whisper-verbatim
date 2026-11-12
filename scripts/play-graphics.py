"""Produce Play listing graphics from the app's microphone-and-cursor design.

Requires Pillow for asset authoring only, never packaged in the Android app.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/play-store/assets"
OUT.mkdir(parents=True, exist_ok=True)
PURPLE = "#4F46E5"


def icon(size):
    scale = size * 4 / 108
    image = Image.new("RGB", (size * 4, size * 4), PURPLE)
    draw = ImageDraw.Draw(image)
    def box(coords):
        return tuple(round(v * scale) for v in coords)
    def line(coords, width=5):
        draw.line(box(coords), fill="white", width=round(width * scale))
        for x, y in (coords[:2], coords[-2:]):
            draw.ellipse(box((x-width/2, y-width/2, x+width/2, y+width/2)), fill="white")
    draw.rounded_rectangle(box((33, 26, 51, 54)), radius=round(9 * scale), fill="white")
    draw.arc(box((25.5, 28.5, 58.5, 61.5)), 0, 180, fill="white", width=round(5 * scale))
    for x in (28, 56):
        draw.ellipse(box((x-2.5, 42.5, x+2.5, 47.5)), fill="white")
    line((42, 59, 42, 70))
    line((35, 72, 49, 72))
    line((70, 32, 70, 76))
    line((64, 32, 76, 32))
    line((64, 76, 76, 76))
    return image.resize((size, size), Image.Resampling.LANCZOS)


icon(512).save(OUT / "store-icon.png")
graphic = Image.new("RGB", (2048, 1000), "#F6F5FF")
graphic.paste(icon(400), (144, 300))
draw = ImageDraw.Draw(graphic)
fonts = Path("C:/Windows/Fonts")
draw.text((660, 300), "Word for Word", font=ImageFont.truetype(str(fonts / "segoeuib.ttf"), 108), fill="#24223A")
draw.text((665, 455), "Dictation for Android", font=ImageFont.truetype(str(fonts / "segoeui.ttf"), 62), fill="#4F46E5")
draw.text((665, 565), "Keep your keyboard. Speak your words.", font=ImageFont.truetype(str(fonts / "segoeui.ttf"), 44), fill="#555268")
graphic.resize((1024, 500), Image.Resampling.LANCZOS).save(OUT / "feature-graphic.png")
