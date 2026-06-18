package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public final class NECreativeTabOrder {
    private NECreativeTabOrder() {}

    public static void acceptAll(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output output) {
        acceptMachines(output);
        acceptMultiblockParts(output);
        acceptStorageCells(output);
        acceptComputationCells(output);
        acceptTerminalsAndStations(output);
        acceptMaterialsAndComponents(output);
        acceptTools(output);
        acceptCompat(params, output);
    }

    private static void acceptMachines(CreativeModeTab.Output output) {
        accept(output, NEBlocks.STORAGE_SYSTEM_L4);
        accept(output, NEBlocks.STORAGE_SYSTEM_L6);
        accept(output, NEBlocks.STORAGE_SYSTEM_L9);

        accept(output, NEBlocks.CRAFTING_SYSTEM_L4);
        accept(output, NEBlocks.CRAFTING_SYSTEM_L6);
        accept(output, NEBlocks.CRAFTING_SYSTEM_L9);

        accept(output, NEBlocks.COMPUTATION_SYSTEM_L4);
        accept(output, NEBlocks.COMPUTATION_SYSTEM_L6);
        accept(output, NEBlocks.COMPUTATION_SYSTEM_L9);
    }

    private static void acceptMultiblockParts(CreativeModeTab.Output output) {
        accept(output, NEBlocks.STORAGE_CASING);
        accept(output, NEBlocks.CRAFTING_CASING);
        accept(output, NEBlocks.COMPUTATION_CASING);

        accept(output, NEBlocks.STORAGE_INTERFACE);
        accept(output, NEBlocks.CRAFTING_INTERFACE);
        accept(output, NEBlocks.COMPUTATION_INTERFACE);

        accept(output, NEBlocks.ECO_DRIVE);
        accept(output, NEBlocks.COMPUTATION_DRIVE);

        accept(output, NEBlocks.STORAGE_VENT);
        accept(output, NEBlocks.CRAFTING_VENT);

        accept(output, NEBlocks.CRAFTING_WORKER);
        accept(output, NEBlocks.CRAFTING_PATTERN_BUS);

        accept(output, NEBlocks.CRAFTING_PARALLEL_CORE_L4);
        accept(output, NEBlocks.CRAFTING_PARALLEL_CORE_L6);
        accept(output, NEBlocks.CRAFTING_PARALLEL_CORE_L9);
        accept(output, NEBlocks.COMPUTATION_PARALLEL_CORE_L4);
        accept(output, NEBlocks.COMPUTATION_PARALLEL_CORE_L6);
        accept(output, NEBlocks.COMPUTATION_PARALLEL_CORE_L9);

        accept(output, NEBlocks.COMPUTATION_THREADING_CORE_L4);
        accept(output, NEBlocks.COMPUTATION_THREADING_CORE_L6);
        accept(output, NEBlocks.COMPUTATION_THREADING_CORE_L9);

        accept(output, NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4);
        accept(output, NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6);
        accept(output, NEBlocks.COMPUTATION_COOLING_CONTROLLER_L9);
        accept(output, NEBlocks.COMPUTATION_TRANSMITTER);

        accept(output, NEBlocks.INPUT_HATCH);
        accept(output, NEBlocks.OUTPUT_HATCH);

        accept(output, NEBlocks.ENERGY_CELL_L4);
        accept(output, NEBlocks.ENERGY_CELL_L6);
        accept(output, NEBlocks.ENERGY_CELL_L9);
    }

    private static void acceptStorageCells(CreativeModeTab.Output output) {
        accept(output, NEItems.ECO_ITEM_CELL_HOUSING);
        accept(output, NEItems.ECO_FLUID_CELL_HOUSING);

        accept(output, NEItems.ECO_ITEM_CELL_16M);
        accept(output, NEItems.ECO_ITEM_CELL_64M);
        accept(output, NEItems.ECO_ITEM_CELL_256M);

        accept(output, NEItems.ECO_FLUID_CELL_16M);
        accept(output, NEItems.ECO_FLUID_CELL_64M);
        accept(output, NEItems.ECO_FLUID_CELL_256M);

        acceptChemicalCells(output);
        acceptFeCells(output);
        acceptManaCells(output);
        acceptSourceCells(output);
    }

    private static void acceptComputationCells(CreativeModeTab.Output output) {
        accept(output, NEItems.ECO_COMPUTATION_CELL_L4);
        accept(output, NEItems.ECO_COMPUTATION_CELL_L6);
        accept(output, NEItems.ECO_COMPUTATION_CELL_L9);
    }

    private static void acceptTerminalsAndStations(CreativeModeTab.Output output) {
        accept(output, NEItems.STRUCTURE_TERMINAL);
        accept(output, NEBlocks.INTEGRATED_WORKING_STATION);
    }

    private static void acceptMaterialsAndComponents(CreativeModeTab.Output output) {
        accept(output, NEBlocks.ALUMINUM_ORE);
        accept(output, NEBlocks.RAW_ALUMINUM_BLOCK);
        accept(output, NEBlocks.TUNGSTEN_ORE);
        accept(output, NEBlocks.RAW_TUNGSTEN_BLOCK);

        accept(output, NEBlocks.ALUMINUM_BLOCK);
        accept(output, NEBlocks.TUNGSTEN_BLOCK);
        accept(output, NEBlocks.ALUMINUM_ALLOY_BLOCK);
        accept(output, NEBlocks.ALUMINUM_ALLOY_CASING);
        accept(output, NEBlocks.BLACK_TUNGSTEN_ALLOY_BLOCK);
        accept(output, NEBlocks.BLACK_TUNGSTEN_ALLOY_CASING);

        accept(output, NEBlocks.ENERGIZED_CRYSTAL_BLOCK);
        accept(output, NEBlocks.ENERGIZED_SUPERCONDUCTIVE_BLOCK);
        accept(output, NEBlocks.ENERGIZED_FLUIX_CRYSTAL_BLOCK);
        accept(output, NEBlocks.FLAWLESS_BUDDING_ENERGIZED_CRYSTAL);
        accept(output, NEBlocks.FLAWED_BUDDING_ENERGIZED_CRYSTAL);
        accept(output, NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL);
        accept(output, NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL);
        accept(output, NEBlocks.SMALL_ENERGIZED_CRYSTAL_BUD);
        accept(output, NEBlocks.MEDIUM_ENERGIZED_CRYSTAL_BUD);
        accept(output, NEBlocks.LARGE_ENERGIZED_CRYSTAL_BUD);
        accept(output, NEBlocks.ENERGIZED_CRYSTAL_CLUSTER);

        accept(output, NEItems.IRON_DUST);
        accept(output, NEItems.RAW_ALUMINUM_ORE);
        accept(output, NEItems.ALUMINUM_INGOT);
        accept(output, NEItems.ALUMINUM_DUST);
        accept(output, NEItems.RAW_TUNGSTEN_ORE);
        accept(output, NEItems.TUNGSTEN_INGOT);
        accept(output, NEItems.TUNGSTEN_DUST);
        accept(output, NEItems.ALUMINUM_ALLOY_INGOT);
        accept(output, NEItems.ALUMINUM_ALLOY_DUST);
        accept(output, NEItems.BLACK_TUNGSTEN_ALLOY_INGOT);
        accept(output, NEItems.BLACK_TUNGSTEN_ALLOY_DUST);

        accept(output, NEItems.ENERGIZED_CRYSTAL);
        accept(output, NEItems.ENERGIZED_CRYSTAL_DUST);
        accept(output, NEItems.ENERGIZED_FLUIX_CRYSTAL);
        accept(output, NEItems.ENERGIZED_FLUIX_CRYSTAL_DUST);
        accept(output, NEItems.CRYSTAL_INGOT);
        accept(output, NEItems.CRYSTAL_MATRIX);
        accept(output, NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT);
        accept(output, NEItems.CRYOTHEUM);
        accept(output, NEItems.CRYOTHEUM_CRYSTAL);

        accept(output, NEItems.SUPERCONDUCTING_PROCESSOR_PRESS);
        accept(output, NEItems.SUPERCONDUCTING_PROCESSOR_PRINT);
        accept(output, NEItems.SUPERCONDUCTING_PROCESSOR);

        accept(output, NEItems.ECO_CELL_COMPONENT_16M);
        accept(output, NEItems.ECO_CELL_COMPONENT_64M);
        accept(output, NEItems.ECO_CELL_COMPONENT_256M);

        accept(output, NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE);
        accept(output, NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE);
    }

    private static void acceptTools(CreativeModeTab.Output output) {
        accept(output, NEItems.ALUMINUM_PICKAXE);
        accept(output, NEItems.ALUMINUM_AXE);
        accept(output, NEItems.ALUMINUM_SHOVEL);
        accept(output, NEItems.ALUMINUM_HOE);
        accept(output, NEItems.ALUMINUM_SWORD);

        accept(output, NEItems.TUNGSTEN_PICKAXE);
        accept(output, NEItems.TUNGSTEN_AXE);
        accept(output, NEItems.TUNGSTEN_SHOVEL);
        accept(output, NEItems.TUNGSTEN_HOE);
        accept(output, NEItems.TUNGSTEN_SWORD);

        accept(output, NEItems.ALUMINUM_ALLOY_PICKAXE);
        accept(output, NEItems.ALUMINUM_ALLOY_AXE);
        accept(output, NEItems.ALUMINUM_ALLOY_SHOVEL);
        accept(output, NEItems.ALUMINUM_ALLOY_HOE);
        accept(output, NEItems.ALUMINUM_ALLOY_SWORD);

        accept(output, NEItems.BLACK_TUNGSTEN_ALLOY_PICKAXE);
        accept(output, NEItems.BLACK_TUNGSTEN_ALLOY_AXE);
        accept(output, NEItems.BLACK_TUNGSTEN_ALLOY_SHOVEL);
        accept(output, NEItems.BLACK_TUNGSTEN_ALLOY_HOE);
        accept(output, NEItems.BLACK_TUNGSTEN_ALLOY_SWORD);
    }

    private static void acceptCompat(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output output) {
        // Chemical cells are inserted in acceptStorageCells(), not appended here.
    }

    private static void acceptChemicalCells(CreativeModeTab.Output output) {
        if (!ModList.get().isLoaded("appmek")) {
            return;
        }
        acceptById(output, "eco_chemical_cell_housing");
        acceptById(output, "eco_chemical_storage_cell_16m");
        acceptById(output, "eco_chemical_storage_cell_64m");
        acceptById(output, "eco_chemical_storage_cell_256m");
    }

    private static void acceptFeCells(CreativeModeTab.Output output) {
        if (!ModList.get().isLoaded("appflux")) {
            return;
        }
        acceptById(output, "eco_fe_cell_housing");
        acceptById(output, "eco_fe_storage_cell_16m");
        acceptById(output, "eco_fe_storage_cell_64m");
        acceptById(output, "eco_fe_storage_cell_256m");
    }

    private static void acceptManaCells(CreativeModeTab.Output output) {
        if (!ModList.get().isLoaded("appbot")) {
            return;
        }
        acceptById(output, "eco_mana_cell_housing");
        acceptById(output, "eco_mana_storage_cell_16m");
        acceptById(output, "eco_mana_storage_cell_64m");
        acceptById(output, "eco_mana_storage_cell_256m");
    }

    private static void acceptSourceCells(CreativeModeTab.Output output) {
        if (!ModList.get().isLoaded("arseng")) {
            return;
        }
        acceptById(output, "eco_source_cell_housing");
        acceptById(output, "eco_source_storage_cell_16m");
        acceptById(output, "eco_source_storage_cell_64m");
        acceptById(output, "eco_source_storage_cell_256m");
    }

    private static void accept(CreativeModeTab.Output output, Supplier<? extends ItemLike> supplier) {
        output.accept(supplier.get());
    }

    private static void acceptById(CreativeModeTab.Output output, String path) {
        ResourceLocation id = NeoECOAE.id(path);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item != null) {
            output.accept(item);
        }
    }
}
