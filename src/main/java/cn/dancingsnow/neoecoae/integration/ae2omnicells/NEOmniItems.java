package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;

public final class NEOmniItems {
    private static final long QUANTUM_CAPACITY_MULTIPLIER = 4L;

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
            "eco_omni_cell_16m", ECOTier.L4, NEOmniCellTypes.OMNI, 8, 63, "omni_cell_housing", "16m", Rarity.UNCOMMON);
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_OMNI_CELL_64M = registerCell(
            "eco_omni_cell_64m", ECOTier.L6, NEOmniCellTypes.OMNI, 9, 63, "omni_cell_housing", "64m", Rarity.RARE);
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_OMNI_CELL_256M = registerCell(
            "eco_omni_cell_256m", ECOTier.L9, NEOmniCellTypes.OMNI, 10, 63, "omni_cell_housing", "256m", Rarity.EPIC);

    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_COMPLEX_OMNI_CELL_16M = registerCell(
            "eco_complex_omni_cell_16m",
            ECOTier.L4,
            NEOmniCellTypes.COMPLEX_OMNI,
            256,
            1600,
            "complex_omni_cell_housing",
            "16m",
            Rarity.UNCOMMON);
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_COMPLEX_OMNI_CELL_64M = registerCell(
            "eco_complex_omni_cell_64m",
            ECOTier.L6,
            NEOmniCellTypes.COMPLEX_OMNI,
            512,
            3200,
            "complex_omni_cell_housing",
            "64m",
            Rarity.RARE);
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_COMPLEX_OMNI_CELL_256M = registerCell(
            "eco_complex_omni_cell_256m",
            ECOTier.L9,
            NEOmniCellTypes.COMPLEX_OMNI,
            1024,
            6400,
            "complex_omni_cell_housing",
            "256m",
            Rarity.EPIC);

    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_QUANTUM_OMNI_CELL_16M =
            registerQuantumCell("eco_quantum_omni_cell_16m", ECOTier.L4, 6561, "16m", Rarity.UNCOMMON);
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_QUANTUM_OMNI_CELL_64M =
            registerQuantumCell("eco_quantum_omni_cell_64m", ECOTier.L6, 19683, "64m", Rarity.RARE);
    public static final ItemEntry<ECOUniversalStorageCellItem> ECO_QUANTUM_OMNI_CELL_256M =
            registerQuantumCell("eco_quantum_omni_cell_256m", ECOTier.L9, 59049, "256m", Rarity.EPIC);

    private static ItemEntry<ECOUniversalStorageCellItem> registerCell(
            String name,
            ECOTier tier,
            NECellTypeEntry cellType,
            double idleDrain,
            int totalTypes,
            String housing,
            String size,
            Rarity rarity) {
        return REGISTRATE
                .item(
                        name,
                        properties -> new ECOUniversalStorageCellItem(
                                properties.rarity(rarity), tier, cellType, idleDrain, totalTypes))
                .lang(cellName(name, tier))
                .model(ItemModelUtil.compatCellModel(housing, size))
                .register();
    }

    private static ItemEntry<ECOUniversalStorageCellItem> registerQuantumCell(
            String name, ECOTier tier, double idleDrain, String size, Rarity rarity) {
        return REGISTRATE
                .item(
                        name,
                        properties -> new ECOUniversalStorageCellItem(
                                properties.rarity(rarity),
                                tier,
                                NEOmniCellTypes.QUANTUM_OMNI,
                                idleDrain,
                                -1,
                                Math.multiplyExact(tier.getStorageTotalBytes(), QUANTUM_CAPACITY_MULTIPLIER)))
                .lang(cellName(name, tier))
                .model(ItemModelUtil.compatCellModel("quantum_omni_cell_housing", size, "quantum_omni_cell_layer"))
                .register();
    }

    private static String cellName(String name, ECOTier tier) {
        String family = name.contains("quantum") ? "Quantum Omni" : name.contains("complex") ? "Complex Omni" : "Omni";
        return "ECO - LE" + (tier == ECOTier.L4 ? "4" : tier == ECOTier.L6 ? "6" : "9") + " Storage Matrix (" + family
                + ")";
    }

    private NEOmniItems() {}

    public static void register() {}
}
