package cn.dancingsnow.neoecoae.integration.appliedpneumatics;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEAppliedPneumaticsItems {
    static final ItemEntry<Item> AIR_CELL_HOUSING = REGISTRATE
        .item("eco_air_cell_housing", Item::new)
        .lang("ECO Storage Matrix Housing (Air)")
        // Uses the imported model in models/item/cell/eco_air_cell_housing.json.
        .model((ctx, prov) -> {})
        .register();

    static final ItemEntry<ECOStorageCellItem> AIR_CELL_16M = cell("eco_air_cell_16m", ECOTier.L4, Rarity.UNCOMMON);
    static final ItemEntry<ECOStorageCellItem> AIR_CELL_64M = cell("eco_air_cell_64m", ECOTier.L6, Rarity.RARE);
    static final ItemEntry<ECOStorageCellItem> AIR_CELL_256M = cell("eco_air_cell_256m", ECOTier.L9, Rarity.EPIC);

    private static ItemEntry<ECOStorageCellItem> cell(String name, ECOTier tier, Rarity rarity) {
        return REGISTRATE.item(name, properties -> new ECOStorageCellItem(
                properties.stacksTo(1).rarity(rarity),
                tier,
                ExternalCellKeyTypes.byId("appliedpneumatics", "air_type"),
                NEAppliedPneumaticsCellTypes.AIR
            ))
            .lang("ECO - LE" + tier.getTier() + " Storage Matrix (Air)")
            // The registered item model aliases the matching imported model under models/item/cell.
            .model((ctx, prov) -> {})
            .register();
    }

    private NEAppliedPneumaticsItems() {
    }

    static void register() {
    }
}
