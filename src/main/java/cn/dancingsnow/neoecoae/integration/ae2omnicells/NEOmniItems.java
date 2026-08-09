package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.core.definitions.AEItems;
import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.wintercogs.ae2omnicells.common.init.OCItems;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public final class NEOmniItems {
    private static final long LE4_CAPACITY = 1L << 28;
    private static final long LE6_CAPACITY = 1L << 30;
    private static final long LE9_CAPACITY = 1L << 32;

    public static final ItemEntry<MaterialItem> ECO_OMNI_CELL_HOUSING = REGISTRATE
        .item("eco_omni_cell_housing", MaterialItem::new)
        .lang("ECO Omni Storage Matrix Housing")
        .model(ItemModelUtil.compatHousingModel("omni_cell_housing"))
        .register();

    public static final ItemEntry<MaterialItem> ECO_COMPLEX_OMNI_CELL_HOUSING = REGISTRATE
        .item("eco_complex_omni_cell_housing", MaterialItem::new)
        .lang("ECO Complex Omni Storage Matrix Housing")
        .model(ItemModelUtil.compatHousingModel("complex_omni_cell_housing"))
        .register();

    public static final ItemEntry<MaterialItem> ECO_QUANTUM_OMNI_CELL_HOUSING = REGISTRATE
        .item("eco_quantum_omni_cell_housing", MaterialItem::new)
        .lang("ECO Quantum Omni Storage Matrix Housing")
        .model(ItemModelUtil.compatHousingModel("quantum_omni_cell_housing", "quantum_omni_cell_layer"))
        .register();

    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_OMNI_CELL_16M = registerCell(
        "eco_omni_cell_16m", ECOTier.L4, NEOmniCellTypes.OMNI, 8, 63, LE4_CAPACITY,
        OCItems.OMNI_CELL_COMPONENT_16M, OCItems.OMNI_LINK_PROCESSOR, 2, 1, ECO_OMNI_CELL_HOUSING,
        "omni_cell_housing", "16m", false, Rarity.UNCOMMON
    );
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_OMNI_CELL_64M = registerCell(
        "eco_omni_cell_64m", ECOTier.L6, NEOmniCellTypes.OMNI, 9, 63, LE6_CAPACITY,
        OCItems.OMNI_CELL_COMPONENT_64M, OCItems.OMNI_LINK_PROCESSOR, 4, 4, ECO_OMNI_CELL_HOUSING,
        "omni_cell_housing", "64m", false, Rarity.RARE
    );
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_OMNI_CELL_256M = registerCell(
        "eco_omni_cell_256m", ECOTier.L9, NEOmniCellTypes.OMNI, 10, 63, LE9_CAPACITY,
        OCItems.OMNI_CELL_COMPONENT_256M, OCItems.OMNI_LINK_PROCESSOR, 8, 16, ECO_OMNI_CELL_HOUSING,
        "omni_cell_housing", "256m", false, Rarity.EPIC
    );

    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_COMPLEX_OMNI_CELL_16M = registerCell(
        "eco_complex_omni_cell_16m", ECOTier.L4, NEOmniCellTypes.COMPLEX_OMNI, 256, 1600, LE4_CAPACITY,
        OCItems.COMPLEX_OMNI_CELL_COMPONENT_16M, OCItems.COMPLEX_LINK_PROCESSOR, 2, 2,
        ECO_COMPLEX_OMNI_CELL_HOUSING, "complex_omni_cell_housing", "16m", false, Rarity.UNCOMMON
    );
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_COMPLEX_OMNI_CELL_64M = registerCell(
        "eco_complex_omni_cell_64m", ECOTier.L6, NEOmniCellTypes.COMPLEX_OMNI, 512, 3200, LE6_CAPACITY,
        OCItems.COMPLEX_OMNI_CELL_COMPONENT_64M, OCItems.COMPLEX_LINK_PROCESSOR, 4, 8,
        ECO_COMPLEX_OMNI_CELL_HOUSING, "complex_omni_cell_housing", "64m", false, Rarity.RARE
    );
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_COMPLEX_OMNI_CELL_256M = registerCell(
        "eco_complex_omni_cell_256m", ECOTier.L9, NEOmniCellTypes.COMPLEX_OMNI, 1024, 6400, LE9_CAPACITY,
        OCItems.COMPLEX_OMNI_CELL_COMPONENT_256M, OCItems.COMPLEX_LINK_PROCESSOR, 8, 32,
        ECO_COMPLEX_OMNI_CELL_HOUSING, "complex_omni_cell_housing", "256m", false, Rarity.EPIC
    );

    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_QUANTUM_OMNI_CELL_16M = registerCell(
        "eco_quantum_omni_cell_16m", ECOTier.L4, NEOmniCellTypes.QUANTUM_OMNI, 6561, -1, LE4_CAPACITY,
        OCItems.QUANTUM_OMNI_CELL_COMPONENT_16M, OCItems.MULTIDIMENSIONAL_EXPANSION_PROCESSOR, 2, 4,
        ECO_QUANTUM_OMNI_CELL_HOUSING, "quantum_omni_cell_housing", "16m", true, Rarity.UNCOMMON
    );
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_QUANTUM_OMNI_CELL_64M = registerCell(
        "eco_quantum_omni_cell_64m", ECOTier.L6, NEOmniCellTypes.QUANTUM_OMNI, 19683, -1, LE6_CAPACITY,
        OCItems.QUANTUM_OMNI_CELL_COMPONENT_64M, OCItems.MULTIDIMENSIONAL_EXPANSION_PROCESSOR, 4, 16,
        ECO_QUANTUM_OMNI_CELL_HOUSING, "quantum_omni_cell_housing", "64m", true, Rarity.RARE
    );
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_QUANTUM_OMNI_CELL_256M = registerCell(
        "eco_quantum_omni_cell_256m", ECOTier.L9, NEOmniCellTypes.QUANTUM_OMNI, 59049, -1, LE9_CAPACITY,
        OCItems.QUANTUM_OMNI_CELL_COMPONENT_256M, OCItems.MULTIDIMENSIONAL_EXPANSION_PROCESSOR, 8, 64,
        ECO_QUANTUM_OMNI_CELL_HOUSING, "quantum_omni_cell_housing", "256m", true, Rarity.EPIC
    );

    private static ItemEntry<ECOUniversalStorageCellItem> registerCell(
        String name,
        ECOTier tier,
        NECellTypeEntry cellType,
        double idleDrain,
        int totalTypes,
        long totalBytes,
        ItemLike component,
        ItemLike processor,
        int processorCount,
        int singularityCount,
        ItemLike housingItem,
        String housing,
        String size,
        boolean hasQuantumLayer,
        Rarity rarity
    ) {
        return REGISTRATE.item(name, properties -> new ECOUniversalStorageCellItem(
                properties.rarity(rarity), tier, cellType, idleDrain, totalTypes, totalBytes
            ))
            .recipe((ctx, prov) -> IntegratedWorkingStationRecipe.builder()
                .require(component, 10)
                .require(processor, processorCount)
                .require(AEItems.SINGULARITY, singularityCount)
                .require(housingItem)
                .energy(energyFor(tier))
                .itemOutput(ctx.get())
                .save(prov, ctx.getId().withPrefix("integrated_working_station/")))
            .lang(cellName(name, tier, totalBytes))
            .model(hasQuantumLayer
                ? ItemModelUtil.compatCellModel(housing, size, "quantum_omni_cell_layer")
                : ItemModelUtil.compatCellModel(housing, size))
            .register();
    }

    private static int energyFor(ECOTier tier) {
        return switch (tier) {
            case L4 -> 1_000;
            case L6 -> 12_000;
            case L9 -> 144_000;
        };
    }

    private static String cellName(String name, ECOTier tier, long totalBytes) {
        String family = name.contains("quantum") ? "Quantum Omni" : name.contains("complex") ? "Complex Omni" : "Omni";
        return "ECO - LE" + (tier == ECOTier.L4 ? "4" : tier == ECOTier.L6 ? "6" : "9")
            + " Storage Matrix (" + family + ", " + (totalBytes >> 20) + " MiB)";
    }

    private NEOmniItems() {
    }

    public static void register() {
    }
}
