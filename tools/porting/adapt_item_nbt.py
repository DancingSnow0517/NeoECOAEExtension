"""One-time translation of owned item state and settings; does not import an old storage backend."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/cn/dancingsnow/neoecoae"
files = ["impl/storage/infinite/ECOInfiniteStorageMember.java", "blocks/entity/storage/ECODriveBlockEntity.java",
         "blocks/entity/storage/ECOStorageSystemBlockEntity.java"]
for relative in files:
    path = JAVA / relative
    s = path.read_text(encoding="utf-8")
    if "net.minecraft.core.component.DataComponents" not in s:
        raise SystemExit(f"Already converted: {relative}")
    s = s.replace("import net.minecraft.core.component.DataComponents;", "import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;")
    s = s.replace("import net.minecraft.world.item.component.CustomData;\n", "")
    s = s.replace("import appeng.api.ids.AEComponents;", "import cn.dancingsnow.neoecoae.platform.nbt.NECellNbt;")
    for name in ("stack", "drop", "cellStack"):
        call = f"{name}.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)"
        s = s.replace(call + ".copyTag()", f"NEItemData.copy({name})")
        s = s.replace(call, f"NEItemData.read({name})")
        for tag in ("tag", "custom"):
            s = s.replace(f"{name}.set(DataComponents.CUSTOM_DATA, CustomData.of({tag}));", f"NEItemData.write({name}, {tag});")
        s = s.replace(f"{name}.remove(DataComponents.CUSTOM_DATA);", f"{name}.setTag(null);")
    s = s.replace("CustomData data", "CompoundTag data").replace("return data.getUnsafe();", "return data;")
    s = s.replace("replace the component", "replace the item tag")
    s = s.replace("stack.remove(AEComponents.STORAGE_CELL_INV);", "NECellNbt.clear(stack);")
    s = s.replace("Standard ECO cells use this component.", "Standard ECO cells use the owned NBT payload.")
    path.write_text(s, encoding="utf-8")

path = JAVA / "impl/storage/ECOStorageCell.java"
s = path.read_text(encoding="utf-8").replace("import appeng.api.ids.AEComponents;", "import cn.dancingsnow.neoecoae.platform.nbt.NECellNbt;")
s = s.replace("cellStack.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of())", "NECellNbt.readStacks(cellStack)")
s = s.replace("""        if (stacks.isEmpty()) {
            cellStack.remove(AEComponents.STORAGE_CELL_INV);
        } else {
            cellStack.set(AEComponents.STORAGE_CELL_INV, stacks);
        }""", "        NECellNbt.writeStacks(cellStack, stacks);")
path.write_text(s, encoding="utf-8")
path = JAVA / "items/ECOStorageCellItem.java"
s = path.read_text(encoding="utf-8").replace("import appeng.api.ids.AEComponents;", "import cn.dancingsnow.neoecoae.platform.nbt.NECellNbt;")
s = s.replace("is.getOrDefault(AEComponents.STORAGE_CELL_FUZZY_MODE, FuzzyMode.IGNORE_ALL)", "NECellNbt.fuzzyMode(is)")
s = s.replace("is.set(AEComponents.STORAGE_CELL_FUZZY_MODE, fzMode)", "NECellNbt.fuzzyMode(is, fzMode)")
path.write_text(s, encoding="utf-8")

path = JAVA / "blocks/entity/ECOIntegratedWorkingStationBlockEntity.java"
s = path.read_text(encoding="utf-8")
s = s.replace("import cn.dancingsnow.neoecoae.all.NEDataComponents;\n", "").replace("import net.minecraft.core.component.DataComponentMap;\n", "")
s = s.replace("DataComponentMap.Builder builder", "CompoundTag builder").replace("DataComponentMap input", "CompoundTag input")
s = s.replace("builder.set(NEDataComponents.AUTO_EXPORT_SIDES, new AutoExportSides(allowOutputs));", "new AutoExportSides(allowOutputs).write(builder);")
s = s.replace("input.get(NEDataComponents.AUTO_EXPORT_SIDES)", "AutoExportSides.read(input)")
path.write_text(s, encoding="utf-8")
path = JAVA / "NeoECOAE.java"
s = path.read_text(encoding="utf-8").replace("import cn.dancingsnow.neoecoae.all.NEDataComponents;\n", "").replace("        NEDataComponents.register(modBus);\n", "")
path.write_text(s, encoding="utf-8")
obsolete = JAVA / "all/NEDataComponents.java"
obsolete.unlink()
print("Converted owned cell contents, membership, receipts, controller state, fuzzy mode and auto-export settings")
