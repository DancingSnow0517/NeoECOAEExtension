"""Remove RecipeHolder only at known recipe boundaries; preserve runtime DTOs and registry holders."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
files = [
    "src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOLargeIntegratedWorkingStationBlockEntity.java",
    "src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOIntegratedWorkingStationBlockEntity.java",
    "src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingCoolingController.java",
    "src/main/java/cn/dancingsnow/neoecoae/integration/emi/NeoECOAEEmiPlugin.java",
    "src/main/java/cn/dancingsnow/neoecoae/integration/emi/recipe/CoolingEmiRecipe.java",
    "src/main/java/cn/dancingsnow/neoecoae/integration/emi/recipe/IntegrationWorkingStationEmiRecipe.java",
    "src/main/java/cn/dancingsnow/neoecoae/integration/jei/categories/CoolingCategory.java",
    "src/main/java/cn/dancingsnow/neoecoae/integration/jei/categories/IntegrationWorkingStationCategory.java",
]
for relative in files:
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    text = text.replace("import net.minecraft.world.item.crafting.RecipeHolder;\n", "")
    text = re.sub(r"RecipeHolder<(CoolingRecipe|IntegratedWorkingStationRecipe)>", r"\1", text)
    text = text.replace(".map(RecipeHolder::value)", "").replace(".map(net.minecraft.world.item.crafting.RecipeHolder::value)", "")
    for name in ("holder", "candidate", "best", "recipeHolder", "recipe"):
        text = text.replace(f"{name}.value()", name).replace(f"{name}.id()", f"{name}.getId()")
    path.write_text(text, encoding="utf-8")
for path in (ROOT / "src").rglob("*.java"):
    text = original = path.read_text(encoding="utf-8")
    for old, new in (("data/neoecoae/recipe/", "data/neoecoae/recipes/"),
                     ("data/neoecoae/advancement/", "data/neoecoae/advancements/"),
                     ("data/neoecoae/loot_table/", "data/neoecoae/loot_tables/")):
        text = text.replace(old, new)
    if text != original:
        path.write_text(text, encoding="utf-8")
print("Adapted recipe consumers and fixed source resource paths")
