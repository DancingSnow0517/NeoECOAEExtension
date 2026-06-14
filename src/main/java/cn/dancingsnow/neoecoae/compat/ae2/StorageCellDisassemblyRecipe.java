package cn.dancingsnow.neoecoae.compat.ae2;

import cn.dancingsnow.neoecoae.NeoECOAE;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

public final class StorageCellDisassemblyRecipe {
    private StorageCellDisassemblyRecipe() {}

    public static List<ItemStack> getDisassemblyResult(Level level, Item item) {
        ResourceLocation cellId = ForgeRegistries.ITEMS.getKey(item);
        if (cellId == null || !NeoECOAE.MOD_ID.equals(cellId.getNamespace())) {
            return List.of();
        }

        DisassemblyParts parts = DISASSEMBLY_PARTS.get(cellId.getPath());
        if (parts == null) {
            return List.of();
        }

        Item housing = registryItem(parts.housing());
        Item component = registryItem(parts.component());
        if (housing == Items.AIR || component == Items.AIR) {
            return List.of();
        }
        return List.of(new ItemStack(housing), new ItemStack(component));
    }

    private static Item registryItem(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(NeoECOAE.id(path));
        return item == null ? Items.AIR : item;
    }

    private static final Map<String, DisassemblyParts> DISASSEMBLY_PARTS = Map.ofEntries(
            Map.entry(
                    "eco_item_storage_cell_16m",
                    new DisassemblyParts("eco_item_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_item_storage_cell_64m",
                    new DisassemblyParts("eco_item_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_item_storage_cell_256m",
                    new DisassemblyParts("eco_item_cell_housing", "eco_cell_component_256m")),
            Map.entry(
                    "eco_fluid_storage_cell_16m",
                    new DisassemblyParts("eco_fluid_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_fluid_storage_cell_64m",
                    new DisassemblyParts("eco_fluid_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_fluid_storage_cell_256m",
                    new DisassemblyParts("eco_fluid_cell_housing", "eco_cell_component_256m")),
            Map.entry(
                    "eco_chemical_storage_cell_16m",
                    new DisassemblyParts("eco_chemical_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_chemical_storage_cell_64m",
                    new DisassemblyParts("eco_chemical_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_chemical_storage_cell_256m",
                    new DisassemblyParts("eco_chemical_cell_housing", "eco_cell_component_256m")),
            Map.entry("eco_fe_storage_cell_16m", new DisassemblyParts("eco_fe_cell_housing", "eco_cell_component_16m")),
            Map.entry("eco_fe_storage_cell_64m", new DisassemblyParts("eco_fe_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_fe_storage_cell_256m", new DisassemblyParts("eco_fe_cell_housing", "eco_cell_component_256m")),
            Map.entry(
                    "eco_mana_storage_cell_16m",
                    new DisassemblyParts("eco_mana_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_mana_storage_cell_64m",
                    new DisassemblyParts("eco_mana_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_mana_storage_cell_256m",
                    new DisassemblyParts("eco_mana_cell_housing", "eco_cell_component_256m")),
            Map.entry(
                    "eco_source_storage_cell_16m",
                    new DisassemblyParts("eco_source_cell_housing", "eco_cell_component_16m")),
            Map.entry(
                    "eco_source_storage_cell_64m",
                    new DisassemblyParts("eco_source_cell_housing", "eco_cell_component_64m")),
            Map.entry(
                    "eco_source_storage_cell_256m",
                    new DisassemblyParts("eco_source_cell_housing", "eco_cell_component_256m")));

    private record DisassemblyParts(String housing, String component) {}
}
