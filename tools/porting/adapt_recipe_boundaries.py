"""Apply explicit package/signature changes for the Forge 1.20 recipe boundary."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
replacements = {
    "net.minecraftforge.common.crafting.SizedIngredient": "cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient",
    "net.minecraftforge.fluids.crafting.FluidIngredient": "cn.dancingsnow.neoecoae.platform.recipe.FluidIngredient",
    "net.minecraftforge.fluids.crafting.SizedFluidIngredient": "cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient",
}
changed = []
for path in (ROOT / "src").rglob("*.java"):
    text = original = path.read_text(encoding="utf-8")
    for old, new in replacements.items():
        text = text.replace(old, new)
    if text != original:
        path.write_text(text, encoding="utf-8")
        changed.append(path.relative_to(ROOT).as_posix())
print(f"Updated explicit recipe imports in {len(changed)} files")
