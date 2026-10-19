package cn.dancingsnow.neoecoae.integration.appliedcreate;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEStressItems {
    static final ItemEntry<Item> HOUSING = REGISTRATE.item("eco_stress_cell_housing", Item::new)
        .lang("ECO Storage Matrix Housing (Stress)")
        .model(ItemModelUtil.compatHousingModel("stress_cell_housing"))
        .register();

    static final ItemEntry<ECOStorageCellItem> CELL_16M = cell("16m", ECOTier.L4, Rarity.UNCOMMON);
    static final ItemEntry<ECOStorageCellItem> CELL_64M = cell("64m", ECOTier.L6, Rarity.RARE);
    static final ItemEntry<ECOStorageCellItem> CELL_256M = cell("256m", ECOTier.L9, Rarity.EPIC);

    private static ItemEntry<ECOStorageCellItem> cell(String size, ECOTier tier, Rarity rarity) {
        return REGISTRATE.item("eco_stress_cell_" + size, properties -> new ECOStorageCellItem(
                properties.stacksTo(1).rarity(rarity), tier,
                ExternalCellKeyTypes.byId("appliedcreate", "stress"), NEStressCellTypes.STRESS))
            .lang("ECO - LE" + switch (tier) { case L4 -> "4"; case L6 -> "6"; case L9 -> "9"; }
                + " Storage Matrix (Stress)")
            .model(ItemModelUtil.compatCellModel("stress_cell_housing", size))
            .register();
    }

    static List<ItemEntry<ECOStorageCellItem>> cells() {
        return List.of(CELL_16M, CELL_64M, CELL_256M);
    }

    private NEStressItems() {}

    static void register() {}
}
