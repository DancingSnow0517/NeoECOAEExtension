"""Compose English PNG posters from unmodified Minecraft model and UI captures."""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import hashlib
import json
import math
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SIZE = (2560, 1440)
BG = (16, 20, 28)
FONTS = Path("C:/Windows/Fonts")
TIERS = ("l4", "l6", "l9")
COPY = {
    "crafting": {
        "title": "Crafting System", "label": "CRAFTING INFRASTRUCTURE", "accent": (125, 183, 255),
        "line": "Build more. Craft in parallel.",
        "description": "A modular worker array with pattern buses and cooling controls.",
        "metrics": [
            [("24", "parallel / core"), ("32", "overclocked parallel / core"), ("Expandable", "workers + pattern buses")],
            [("72", "parallel / core"), ("96", "overclocked parallel / core"), ("Expandable", "workers + pattern buses")],
            [("256", "parallel / core"), ("384", "overclocked parallel / core"), ("Expandable", "workers + pattern buses")],
        ],
    },
    "computation": {
        "title": "Computation System", "label": "CRAFTING COMPUTATION", "accent": (190, 162, 255),
        "line": "Give every crafting job room to run.",
        "description": "Dedicated crafting memory, threading cores and parallel acceleration.",
        "metrics": [
            [("64 MiB", "per flash matrix"), ("4", "threads / threading core"), ("64", "accelerator units / parallel core")],
            [("256 MiB", "per flash matrix"), ("16", "threads / threading core"), ("192", "accelerator units / parallel core")],
            [("1 GiB", "per flash matrix"), ("64", "threads / threading core"), ("576", "accelerator units / parallel core")],
        ],
    },
    "storage": {
        "title": "Storage System", "label": "RESOURCE STORAGE", "accent": (128, 230, 194),
        "line": "Make space for your next big build.",
        "description": "Expandable matrix bays, resource readouts and integrated energy storage.",
        "metrics": [
            [("16 MiB", "per ECO storage matrix"), ("10M AE", "per energy storage block"), ("Item + fluid", "dedicated storage matrices")],
            [("64 MiB", "per ECO storage matrix"), ("100M AE", "per energy storage block"), ("Item + fluid", "dedicated storage matrices")],
            [("256 MiB", "per ECO storage matrix"), ("1B AE", "per energy storage block"), ("Item + fluid", "dedicated storage matrices")],
        ],
    },
}


def font(size, weight="regular"):
    name = {"regular": "segoeui.ttf", "bold": "segoeuib.ttf", "light": "segoeuil.ttf", "mono": "consola.ttf"}[weight]
    return ImageFont.truetype(str(FONTS / name), size)


def text(draw, xy, value, size, fill=(234, 240, 249), weight="regular", anchor=None):
    face = font(size, weight)
    draw.text(xy, value, font=face, fill=fill, anchor=anchor, stroke_width=0)


def tracked(draw, xy, value, size=24, fill=(155, 170, 192), spacing=3):
    x, y = xy
    face = font(size, "bold")
    for char in value:
        draw.text((x, y), char, font=face, fill=fill)
        x += draw.textlength(char, font=face) + spacing


def backdrop(accent, size=SIZE):
    w, h = size
    yy, xx = np.mgrid[:h, :w]
    glow = np.exp(-(((xx - w * .25) / (w * .55)) ** 2 + ((yy - h * .50) / (h * .75)) ** 2) * 2.1)
    shade = yy / h
    array = np.zeros((h, w, 3), dtype=np.float32)
    for channel, base in enumerate((12, 16, 24)):
        array[:, :, channel] = base + 5 * shade + glow * accent[channel] * .055
    image = Image.fromarray(np.uint8(np.clip(array, 0, 255)), "RGB").convert("RGBA")
    grid = Image.new("RGBA", size)
    d = ImageDraw.Draw(grid)
    for x in range(0, w, 80):
        d.line((x, 330, x, h - 260), fill=(135, 163, 199, 8), width=1)
    for y in range(330, h - 260, 80):
        d.line((80, y, w - 80, y), fill=(135, 163, 199, 8), width=1)
    return Image.alpha_composite(image, grid)


def cutout(path, family, kind):
    raw = Image.open(path).convert("RGB")
    w, h = raw.size
    if (w, h) != (2400, 1350):
        raise ValueError(f"Unexpected capture size: {path}: {raw.size}")
    region = (0, 0, w, h)
    if kind == "ui":
        height = (214 if family == "crafting" else 231) * 3
        # The 253px main panel is centered; preserve both external button rails.
        region = (int((w - 253 * 3) / 2 - 68), int((h - height) / 2 - 6),
                  int((w + 253 * 3) / 2 + 68), int((h + height) / 2 + 6))
    crop = raw.crop(region)
    pixels = np.asarray(crop)
    mask = np.any(pixels != BG, axis=2)
    background_fraction = 1 - float(mask.mean())
    if kind == "model" and background_fraction < .4:
        raise ValueError(f"Model capture has no clean backdrop: {path}")
    if not mask.any():
        raise ValueError(f"Empty capture: {path}")
    if kind == "ui" and background_fraction < .035:
        raise ValueError(f"UI background was not isolated: {path}")
    alpha = Image.fromarray((mask * 255).astype("uint8"))
    rgba = crop.convert("RGBA")
    rgba.putalpha(alpha)
    bounds = alpha.getbbox()
    if kind == "model" and (bounds[0] <= 2 or bounds[1] <= 2 or bounds[2] >= w - 2 or bounds[3] >= h - 2):
        raise ValueError(f"Clipped model: {path}")
    rgba = rgba.crop(bounds)
    padded = Image.new("RGBA", (rgba.width + 16, rgba.height + 16))
    padded.alpha_composite(rgba, (8, 8))
    return padded, {"source": path.name, "source_size": [w, h], "crop": list(region),
                    "content_bounds": list(bounds), "backdrop_fraction": round(background_fraction, 4)}


def fit(image, box):
    factor = min(box[0] / image.width, box[1] / image.height, 1.0)
    if factor < 1:
        image = image.resize((round(image.width * factor), round(image.height * factor)), Image.Resampling.LANCZOS)
    return image


def paste_shadow(canvas, obj, xy, radius=18, opacity=100):
    layer = Image.new("RGBA", canvas.size)
    shadow = Image.new("RGBA", obj.size, (0, 0, 0, opacity))
    shadow.putalpha(obj.getchannel("A").point(lambda a: a * opacity // 255))
    layer.alpha_composite(shadow, (xy[0], xy[1] + 16))
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius)))
    canvas.alpha_composite(obj, xy)


def poster(family, tier, model, ui, dest):
    info = COPY[family]
    tier_index = TIERS.index(tier)
    accent = info["accent"]
    image = backdrop(accent)
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((96, 62, 110, 103), radius=5, fill=accent)
    text(d, (132, 43), "NeoECOAE", 49, weight="bold")
    tracked(d, (459, 68), "MACHINE COLLECTION", 20, spacing=3)
    text(d, (2464, 68), "MINECRAFT 1.21.1  /  NEOFORGE", 24, fill=(142, 160, 183), anchor="ra")
    tracked(d, (100, 130), info["label"], 23, accent, 3)
    text(d, (93, 164), info["title"], 93, weight="bold")
    text(d, (100, 278), info["line"], 35, fill=(177, 192, 212))
    d.rounded_rectangle((2185, 132, 2463, 303), radius=24, fill=tuple(max(0, c // 9) + 15 for c in accent),
                        outline=tuple(c // 3 for c in accent), width=2)
    text(d, (2324, 148), tier.upper(), 110, accent, "bold", "ma")
    d.line((100, 332, 2464, 332), fill=(47, 60, 77), width=2)

    left = (96, 364, 1242, 1190)
    right = (1278, 364, 2464, 1190)
    for rect in (left, right):
        d.rounded_rectangle(rect, radius=24, fill=(19, 25, 35, 255), outline=(43, 54, 72, 255), width=2)
    tracked(d, (132, 392), "ASSEMBLED MACHINE", 23, accent, 2)
    tracked(d, (1314, 392), "CONTROLLER UI", 23, accent, 2)
    text(d, (2423, 399), "ENGLISH", 20, fill=(126, 145, 169), weight="mono", anchor="ra")
    # Soft stage shadow beneath the actual in-game geometry.
    stage = Image.new("RGBA", SIZE)
    sd = ImageDraw.Draw(stage)
    sd.ellipse((230, 984, 1130, 1094), fill=(0, 0, 0, 100))
    image.alpha_composite(stage.filter(ImageFilter.GaussianBlur(32)))
    model_fitted = fit(model, (1060, 605))
    mx = (left[0] + left[2] - model_fitted.width) // 2
    my = 470 + (605 - model_fitted.height) // 2
    paste_shadow(image, model_fitted, (mx, my), radius=22, opacity=80)
    ui_fitted = fit(ui, (1110, 711))
    ux = (right[0] + right[2] - ui_fitted.width) // 2
    uy = 448 + (711 - ui_fitted.height) // 2
    paste_shadow(image, ui_fitted, (ux, uy), radius=15, opacity=120)
    d = ImageDraw.Draw(image)
    text(d, (132, 1103), "FOUR EXPANSION SECTIONS", 21, fill=(141, 162, 188), weight="mono")
    text(d, (132, 1136), info["description"], 23, fill=(169, 186, 207))

    for n, (number, label) in enumerate(info["metrics"][tier_index]):
        x = 104 + n * 800
        if n:
            d.line((x - 33, 1241, x - 33, 1353), fill=(50, 64, 84), width=2)
        text(d, (x, 1214), number, 68 if len(number) < 10 else 61, accent, "bold")
        text(d, (x + 2, 1301), label, 27, fill=(177, 193, 213))
    d.line((100, 1371, 2464, 1371), fill=(40, 52, 68), width=1)
    text(d, (103, 1390), "Default per-component specifications. UI shows the assembled example build.", 22, fill=(119, 139, 166))
    text(d, (2460, 1390), f"{family.upper()}  /  {tier.upper()}", 22, fill=(119, 139, 166), weight="mono", anchor="ra")
    image.convert("RGB").save(dest, optimize=True)


def overview(posters, dest):
    width, height = 2560, 1800
    image = backdrop((152, 189, 242), (width, height))
    d = ImageDraw.Draw(image)
    text(d, (94, 43), "NeoECOAE", 56, weight="bold")
    text(d, (94, 124), "Nine machines. One expandable ecosystem.", 53, weight="bold")
    text(d, (96, 206), "Crafting, computation and storage  /  L4, L6 and L9", 28, fill=(156, 179, 207))
    for col, tier in enumerate(TIERS):
        tracked(d, (164 + col * 816, 277), tier.upper(), 29, (218, 229, 245), 3)
    for row, family in enumerate(COPY):
        y = 329 + row * 478
        for col, tier in enumerate(TIERS):
            thumb = Image.open(posters / f"{family}-{tier}.png").convert("RGBA")
            thumb = thumb.resize((784, 441), Image.Resampling.LANCZOS)
            image.alpha_composite(thumb, (96 + col * 816, y))
    image.convert("RGB").save(dest, optimize=True)


def family_sheet(family, posters, dest):
    image = backdrop(COPY[family]["accent"], (2560, 1680))
    d = ImageDraw.Draw(image)
    text(d, (98, 44), "NeoECOAE  /  " + COPY[family]["title"], 57, weight="bold")
    text(d, (100, 130), COPY[family]["line"] + "  L4 / L6 / L9", 31, fill=(168, 187, 212))
    for i, tier in enumerate(TIERS):
        thumb = Image.open(posters / f"{family}-{tier}.png").convert("RGBA")
        thumb = thumb.resize((2400, 1350), Image.Resampling.LANCZOS)
        # Three complete posters in a tall, lossless overview for this family.
        thumb = thumb.resize((824, 464), Image.Resampling.LANCZOS)
        image.alpha_composite(thumb, (100 + i * 824, 234))
    # A large tier comparison and readable component values below the mini-posters.
    d = ImageDraw.Draw(image)
    tracked(d, (104, 780), "DEFAULT COMPONENT SPECIFICATIONS", 27, COPY[family]["accent"], 3)
    for i, tier in enumerate(TIERS):
        x = 112 + i * 824
        text(d, (x, 868), tier.upper(), 80, COPY[family]["accent"], "bold")
        for j, (number, label) in enumerate(COPY[family]["metrics"][i]):
            text(d, (x, 1000 + j * 165), number, 58, weight="bold")
            text(d, (x, 1074 + j * 165), label, 26, fill=(154, 179, 209))
    text(d, (104, 1578), "Every tier shown with its own assembled model and actual English controller UI.", 28, fill=(140, 163, 194))
    image.convert("RGB").save(dest, optimize=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("captures", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    for folder in ("posters", "assets", "raw", "overview"):
        (output / folder).mkdir(parents=True, exist_ok=True)
    manifest = {"generated_utc": datetime.now(timezone.utc).isoformat(), "language": "en_us",
                "poster_size": list(SIZE), "sections": 4, "capture_method": "Minecraft geometry and normal synchronized controller menus",
                "specification_source": "src/main/java/cn/dancingsnow/neoecoae/api/ECOTier.java", "machines": []}
    for family in COPY:
        for tier in TIERS:
            key = family + "-" + tier
            assets = {}
            entries = {}
            for kind in ("model", "ui"):
                source = args.captures / f"promo-{key}-{kind}.png"
                image, record = cutout(source, family, kind)
                assets[kind] = image
                (output / "raw" / source.name).write_bytes(source.read_bytes())
                image.save(output / "assets" / f"{key}-{kind}.png", optimize=True)
                record["sha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
                entries[kind] = record
            poster(family, tier, assets["model"], assets["ui"], output / "posters" / f"{key}.png")
            manifest["machines"].append({"id": key, "captures": entries, "metrics": COPY[family]["metrics"][TIERS.index(tier)]})
            print("Rendered", key)
    overview(output / "posters", output / "overview" / "all-machines.png")
    for family in COPY:
        family_sheet(family, output / "posters", output / "overview" / f"{family}-tiers.png")
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    archive_path = output / "NeoECOAE-Machine-Promo-English.zip"
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
        for folder in ("posters", "overview"):
            for path in sorted((output / folder).glob("*.png")):
                archive.write(path, path.relative_to(output))
        archive.write(output / "manifest.json", "manifest.json")
    print("Created", archive_path)


if __name__ == "__main__":
    main()
