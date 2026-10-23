"""Join the 1.21.1 interface and its side wings using the source panel's continuous frame."""
from pathlib import Path
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "src/main/resources/assets/ae2/textures/guis/large_integrated_working_station_interface.png"
OUTPUT = SOURCE.with_name("large_integrated_working_station_interface_integrated.png")
CONTROLS = SOURCE.with_name("large_integrated_working_station_interface_controls.png")
WIDTH, HEIGHT, BODY_OFFSET = 228, 245, 20


def inset(mask, left, top, right, bottom):
    result = Image.new("L", mask.size, 255)
    for dx in range(-left, right + 1):
        for dy in range(-top, bottom + 1):
            shifted = Image.new("L", mask.size, 0)
            shifted.paste(mask, (-dx, -dy))
            result = ImageChops.darker(result, shifted)
    return result


def panel(source, buttons=0):
    silhouette = Image.new("L", source.size, 0)
    silhouette.paste(255, (BODY_OFFSET, 0, BODY_OFFSET + 188, HEIGHT))
    silhouette.paste(255, (BODY_OFFSET + 184, 0, WIDTH, 100))
    if buttons:
        silhouette.paste(255, (0, 0, BODY_OFFSET + 4, buttons * 22 + 6))

    # The source frame has a one-pixel dark edge and white inset, with a deeper bottom shadow.
    result = Image.new("RGBA", source.size, (0, 0, 0, 0))
    for mask, sample in [(silhouette, (0, 30)), (inset(silhouette, 1, 1, 1, 1), (30, 242)),
                         (inset(silhouette, 1, 1, 1, 3), (30, 241))]:
        result.paste(source.getpixel(sample), (0, 0), mask)
    interior = inset(silhouette, 2, 2, 2, 4)
    content = Image.new("RGBA", source.size, source.getpixel((10, 10)))
    content.paste(source.crop((2, 2, 186, 241)), (BODY_OFFSET + 2, 2))
    result.paste(content, (0, 0), interior)
    return result


def compose():
    source = Image.open(SOURCE).convert("RGBA")
    panel(source).save(OUTPUT)
    controls = Image.new("RGBA", source.size, (0, 0, 0, 0))
    for buttons in range(1, 11):
        wing = panel(source, buttons).crop((0, 0, 24, buttons * 22 + 10))
        controls.paste(wing, ((buttons - 1) * 24, 0))
    controls.save(CONTROLS)
    return OUTPUT


if __name__ == "__main__":
    print(compose().relative_to(ROOT))
