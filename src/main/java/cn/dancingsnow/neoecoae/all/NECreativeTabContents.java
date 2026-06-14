package cn.dancingsnow.neoecoae.all;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Controls the order of items in the ECO creative tab.
 * <p>
 * Without this, Registrate's {@code defaultCreativeTab} adds items in
 * registration order, which can differ between dev and production
 * environments when items come from multiple registry files.
 * </p>
 */
public final class NECreativeTabContents {

    private static final List<Supplier<ItemStack>> ENTRIES = new ArrayList<>();

    static {
        // ═════════════════════════════════════════════════════════════
        // Ores & Raw Blocks
        // ═════════════════════════════════════════════════════════════
        add(NEBlocks.ALUMINUM_ORE);
        add(NEBlocks.RAW_ALUMINUM_BLOCK);
        add(NEBlocks.TUNGSTEN_ORE);
        add(NEBlocks.RAW_TUNGSTEN_BLOCK);

        // ═════════════════════════════════════════════════════════════
        // Metal Blocks & Casings
        // ═════════════════════════════════════════════════════════════
        add(NEBlocks.ALUMINUM_BLOCK);
        add(NEBlocks.TUNGSTEN_BLOCK);
        add(NEBlocks.ALUMINUM_ALLOY_BLOCK);
        add(NEBlocks.ALUMINUM_ALLOY_CASING);
        add(NEBlocks.BLACK_TUNGSTEN_ALLOY_BLOCK);
        add(NEBlocks.BLACK_TUNGSTEN_ALLOY_CASING);

        // ═════════════════════════════════════════════════════════════
        // Crystal Blocks
        // ═════════════════════════════════════════════════════════════
        add(NEBlocks.ENERGIZED_CRYSTAL_BLOCK);
        add(NEBlocks.ENERGIZED_SUPERCONDUCTIVE_BLOCK);
        add(NEBlocks.ENERGIZED_FLUIX_CRYSTAL_BLOCK);

        // ═════════════════════════════════════════════════════════════
        // Budding Crystals & Clusters
        // ═════════════════════════════════════════════════════════════
        add(NEBlocks.FLAWLESS_BUDDING_ENERGIZED_CRYSTAL);
        add(NEBlocks.FLAWED_BUDDING_ENERGIZED_CRYSTAL);
        add(NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL);
        add(NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL);
        add(NEBlocks.SMALL_ENERGIZED_CRYSTAL_BUD);
        add(NEBlocks.MEDIUM_ENERGIZED_CRYSTAL_BUD);
        add(NEBlocks.LARGE_ENERGIZED_CRYSTAL_BUD);
        add(NEBlocks.ENERGIZED_CRYSTAL_CLUSTER);

        // ═════════════════════════════════════════════════════════════
        // Machines
        // ═════════════════════════════════════════════════════════════
        add(NEBlocks.INTEGRATED_WORKING_STATION);

        // --- Storage System ---
        add(NEBlocks.STORAGE_SYSTEM_L4);
        add(NEBlocks.STORAGE_SYSTEM_L6);
        add(NEBlocks.STORAGE_SYSTEM_L9);
        add(NEBlocks.STORAGE_INTERFACE);
        add(NEBlocks.ENERGY_CELL_L4);
        add(NEBlocks.ENERGY_CELL_L6);
        add(NEBlocks.ENERGY_CELL_L9);
        add(NEBlocks.ECO_DRIVE);
        add(NEBlocks.STORAGE_VENT);
        add(NEBlocks.STORAGE_CASING);

        // --- Computation System ---
        add(NEBlocks.COMPUTATION_SYSTEM_L4);
        add(NEBlocks.COMPUTATION_SYSTEM_L6);
        add(NEBlocks.COMPUTATION_SYSTEM_L9);
        add(NEBlocks.COMPUTATION_THREADING_CORE_L4);
        add(NEBlocks.COMPUTATION_THREADING_CORE_L6);
        add(NEBlocks.COMPUTATION_THREADING_CORE_L9);
        add(NEBlocks.COMPUTATION_PARALLEL_CORE_L4);
        add(NEBlocks.COMPUTATION_PARALLEL_CORE_L6);
        add(NEBlocks.COMPUTATION_PARALLEL_CORE_L9);
        add(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4);
        add(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6);
        add(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L9);
        add(NEBlocks.COMPUTATION_INTERFACE);
        add(NEBlocks.COMPUTATION_TRANSMITTER);
        add(NEBlocks.COMPUTATION_DRIVE);
        add(NEBlocks.COMPUTATION_CASING);

        // --- Crafting System ---
        add(NEBlocks.CRAFTING_SYSTEM_L4);
        add(NEBlocks.CRAFTING_SYSTEM_L6);
        add(NEBlocks.CRAFTING_SYSTEM_L9);
        add(NEBlocks.CRAFTING_INTERFACE);
        add(NEBlocks.CRAFTING_PARALLEL_CORE_L4);
        add(NEBlocks.CRAFTING_PARALLEL_CORE_L6);
        add(NEBlocks.CRAFTING_PARALLEL_CORE_L9);
        add(NEBlocks.CRAFTING_WORKER);
        add(NEBlocks.CRAFTING_PATTERN_BUS);
        add(NEBlocks.INPUT_HATCH);
        add(NEBlocks.OUTPUT_HATCH);
        add(NEBlocks.CRAFTING_VENT);
        add(NEBlocks.CRAFTING_CASING);

        // ═════════════════════════════════════════════════════════════
        // Tools — Aluminum
        // ═════════════════════════════════════════════════════════════
        add(NEItems.ALUMINUM_AXE);
        add(NEItems.ALUMINUM_HOE);
        add(NEItems.ALUMINUM_SHOVEL);
        add(NEItems.ALUMINUM_PICKAXE);
        add(NEItems.ALUMINUM_SWORD);

        // --- Tungsten ---
        add(NEItems.TUNGSTEN_AXE);
        add(NEItems.TUNGSTEN_HOE);
        add(NEItems.TUNGSTEN_SHOVEL);
        add(NEItems.TUNGSTEN_PICKAXE);
        add(NEItems.TUNGSTEN_SWORD);

        // --- Aluminum Alloy ---
        add(NEItems.ALUMINUM_ALLOY_UPGRADE_SMITHING_TEMPLATE);
        add(NEItems.ALUMINUM_ALLOY_AXE);
        add(NEItems.ALUMINUM_ALLOY_HOE);
        add(NEItems.ALUMINUM_ALLOY_SHOVEL);
        add(NEItems.ALUMINUM_ALLOY_PICKAXE);
        add(NEItems.ALUMINUM_ALLOY_SWORD);

        // --- Black Tungsten Alloy ---
        add(NEItems.BLACK_TUNGSTEN_ALLOY_UPGRADE_SMITHING_TEMPLATE);
        add(NEItems.BLACK_TUNGSTEN_ALLOY_AXE);
        add(NEItems.BLACK_TUNGSTEN_ALLOY_HOE);
        add(NEItems.BLACK_TUNGSTEN_ALLOY_SHOVEL);
        add(NEItems.BLACK_TUNGSTEN_ALLOY_PICKAXE);
        add(NEItems.BLACK_TUNGSTEN_ALLOY_SWORD);

        // ═════════════════════════════════════════════════════════════
        // Materials
        // ═════════════════════════════════════════════════════════════
        add(NEItems.IRON_DUST);
        add(NEItems.RAW_ALUMINUM_ORE);
        add(NEItems.ALUMINUM_INGOT);
        add(NEItems.ALUMINUM_DUST);
        add(NEItems.RAW_TUNGSTEN_ORE);
        add(NEItems.TUNGSTEN_INGOT);
        add(NEItems.TUNGSTEN_DUST);
        add(NEItems.ALUMINUM_ALLOY_INGOT);
        add(NEItems.ALUMINUM_ALLOY_DUST);
        add(NEItems.BLACK_TUNGSTEN_ALLOY_INGOT);
        add(NEItems.BLACK_TUNGSTEN_ALLOY_DUST);

        // --- Crystal Materials ---
        add(NEItems.ENERGIZED_CRYSTAL);
        add(NEItems.ENERGIZED_CRYSTAL_DUST);
        add(NEItems.ENERGIZED_FLUIX_CRYSTAL);
        add(NEItems.ENERGIZED_FLUIX_CRYSTAL_DUST);
        add(NEItems.CRYSTAL_INGOT);
        add(NEItems.CRYSTAL_MATRIX);
        add(NEItems.ENERGIZED_SUPERCONDUCTIVE_INGOT);
        add(NEItems.CRYOTHEUM);
        add(NEItems.CRYOTHEUM_CRYSTAL);

        // --- Processors ---
        add(NEItems.SUPERCONDUCTING_PROCESSOR_PRESS);
        add(NEItems.SUPERCONDUCTING_PROCESSOR_PRINT);
        add(NEItems.SUPERCONDUCTING_PROCESSOR);

        // ═════════════════════════════════════════════════════════════
        // Storage Cells
        // ═════════════════════════════════════════════════════════════
        add(NEItems.ECO_CELL_COMPONENT_16M);
        add(NEItems.ECO_CELL_COMPONENT_64M);
        add(NEItems.ECO_CELL_COMPONENT_256M);
        add(NEItems.ECO_ITEM_CELL_HOUSING);
        add(NEItems.ECO_FLUID_CELL_HOUSING);
        add(NEItems.ECO_ITEM_CELL_16M);
        add(NEItems.ECO_ITEM_CELL_64M);
        add(NEItems.ECO_ITEM_CELL_256M);
        add(NEItems.ECO_FLUID_CELL_16M);
        add(NEItems.ECO_FLUID_CELL_64M);
        add(NEItems.ECO_FLUID_CELL_256M);

        // --- Chemical Cells (looked up by registry name; only present if AppMek
        // loaded) ---
        addIfPresent("eco_chemical_cell_housing");
        addIfPresent("eco_chemical_storage_cell_16m");
        addIfPresent("eco_chemical_storage_cell_64m");
        addIfPresent("eco_chemical_storage_cell_256m");

        // ═════════════════════════════════════════════════════════════
        // Computation Cells
        // ═════════════════════════════════════════════════════════════
        add(NEItems.ECO_COMPUTATION_CELL_L4);
        add(NEItems.ECO_COMPUTATION_CELL_L6);
        add(NEItems.ECO_COMPUTATION_CELL_L9);

        // ═════════════════════════════════════════════════════════════
        // Miscellaneous
        // ═════════════════════════════════════════════════════════════
        add(NEItems.STRUCTURE_TERMINAL);
    }

    /**
     * Look up an item by its registry path name (under the neoecoae namespace)
     * and add it to the entry list if it exists in the registry.
     * <p>
     * This is used for items registered by conditional integrations
     * (e.g. AppMek chemical cells) where the class may not be loadable.
     */
    private static void addIfPresent(String registryPath) {
        ResourceLocation id = ResourceLocation.tryBuild("neoecoae", registryPath);
        if (id != null && ForgeRegistries.ITEMS.containsKey(id)) {
            ENTRIES.add(() -> new ItemStack(ForgeRegistries.ITEMS.getValue(id)));
        }
    }

    private static void add(Supplier<?> entry) {
        ENTRIES.add(() -> {
            Object obj = entry.get();
            if (obj instanceof ItemStack stack) {
                return stack;
            }
            if (obj instanceof net.minecraft.world.level.ItemLike itemLike) {
                return new ItemStack(itemLike);
            }
            return ItemStack.EMPTY;
        });
    }

    /**
     * Call this during construction (e.g. in
     * {@link cn.dancingsnow.neoecoae.NeoECOAE NeoECOAE} constructor) to
     * register the event handler.
     *
     * @param modBus the mod event bus
     */
    public static void register(IEventBus modBus) {
        modBus.addListener(NECreativeTabContents::onBuildCreativeTabContents);
    }

    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != NECreativeTabs.ECO.getKey()) {
            return;
        }

        for (Supplier<ItemStack> supplier : ENTRIES) {
            ItemStack stack = supplier.get();
            if (!stack.isEmpty()) {
                event.accept(stack);
            }
        }
    }
}
