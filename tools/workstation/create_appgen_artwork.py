"""Extend the existing workstation pixel art with the Applied Generators family.

Run from any directory with Python and Pillow. The preview is written under build/verification.
The supplied card PNG is preserved. Controller artwork extends the repository's neutral assets.
"""

import colorsys
import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/neoecoae"
TEXTURES = ASSETS / "textures"
FORMED = TEXTURES / "block/working_station/controller_formed"
PREVIEW = ROOT / "build/verification/appgen"
AMBER = (245, 162, 60)


def amber(pixel):
    r, g, b, a = pixel
    _, saturation, value = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    rgb = colorsys.hsv_to_rgb(0.088, max(0.72, saturation), value)
    return tuple(round(channel * 255) for channel in rgb) + (a,)


def save_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def atlas_art():
    path = TEXTURES / "guis/workstation/integrated_working_station_widget.png"
    atlas = Image.open(path).convert("RGBA")
    chip = atlas.crop((90, 17, 105, 32))
    reference = atlas.crop((75, 17, 90, 32))
    for y in range(15):
        for x in range(15):
            r, g, b, a = chip.getpixel((x, y))
            if a and chip.getpixel((x, y)) != reference.getpixel((x, y)):
                chip.putpixel((x, y), amber((r, g, b, a)))
    # Use the symbol from the supplied card inside the established UI chip housing.
    card = Image.open(TEXTURES / "item/integrated_working_station_modules/appgen.png").convert("RGBA")
    chip.paste(card.crop((4, 3, 12, 11)), (4, 4))
    atlas.paste(chip, (7 * 15, 17))
    circuit = atlas.crop((168, 34, 196, 62))
    for y in range(28):
        for x in range(28):
            a = circuit.getpixel((x, y))[3]
            if a:
                circuit.putpixel((x, y), AMBER + (a,))
    atlas.paste(circuit, (7 * 28, 34))
    atlas.save(path)
    return chip, circuit


def controller_art():
    for suffix in ("", "_on"):
        folder = FORMED / "controller_formed_light_compat"
        neutral = Image.open(folder / f"other{suffix}.png").convert("RGBA")
        reference = Image.open(folder / f"data_energistics{suffix}.png").convert("RGBA")
        for y in range(neutral.height):
            for x in range(neutral.width):
                pixel = neutral.getpixel((x, y))
                # Preserve shared blue/yellow port markers and the complete transparency mask.
                if pixel != reference.getpixel((x, y)) and pixel[3]:
                    neutral.putpixel((x, y), amber(pixel))
        path = folder / f"appgen{suffix}.png"
        neutral.save(path)
        if suffix:
            save_json(path.with_suffix(".png.mcmeta"), {"animation": {"interpolate": True, "frametime": 15}})
    path = FORMED / "controller_formed_screen_compat/appgen_on.png"
    screen = Image.open(path.with_name("other_on.png")).convert("RGBA")
    screen.putdata([amber(pixel) for pixel in screen.getdata()])
    screen.save(path)
    save_json(path.with_suffix(".png.mcmeta"), {"animation": {"frametime": 5}})


def preview(card, chip, circuit):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGB", (690, 365), "#252832")
    draw = ImageDraw.Draw(sheet)
    for title, art, origin, scale in (
        ("Supplied upgrade card", card, (30, 50), 8),
        ("Module icon", chip, (255, 60), 6),
        ("Circuit overlay", circuit, (455, 45), 6),
    ):
        draw.text((origin[0], 20), title, fill="#f5d2a0")
        enlarged = art.resize((art.width * scale, art.height * scale), Image.Resampling.NEAREST)
        sheet.paste(enlarged, origin, enlarged)
    screen = Image.open(FORMED / "controller_formed_screen_compat/appgen_on.png").convert("RGBA")
    draw.text((30, 220), "Controller screen / animation samples", fill="#f5d2a0")
    for index, frame in enumerate((0, 4, 8, 12, 16, 20)):
        art = screen.crop((0, frame * 16, 16, (frame + 1) * 16)).resize((80, 80), Image.Resampling.NEAREST)
        sheet.paste(art, (30 + index * 110, 250), art)
    path = PREVIEW / "appgen-artwork.png"
    sheet.save(path)
    print(path)


if __name__ == "__main__":
    card = Image.open(TEXTURES / "item/integrated_working_station_modules/appgen.png").convert("RGBA")
    chip, circuit = atlas_art()
    controller_art()
    preview(card, chip, circuit)
