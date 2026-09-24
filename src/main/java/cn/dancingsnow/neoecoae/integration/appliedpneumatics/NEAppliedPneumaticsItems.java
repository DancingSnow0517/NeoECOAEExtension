package cn.dancingsnow.neoecoae.integration.appliedpneumatics;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

final class NEAppliedPneumaticsItems {
    static final ItemEntry<Item> AIR_CELL_HOUSING = REGISTRATE
            .item("eco_air_cell_housing", Item::new)
            .lang("ECO Storage Matrix Housing (Air)")
            .model((ctx, prov) -> {})
            .register();

    static final ItemEntry<ECOStorageCellItem> AIR_CELL_16M = cell("eco_air_cell_16m", ECOTier.L4, Rarity.UNCOMMON);
    static final ItemEntry<ECOStorageCellItem> AIR_CELL_64M = cell("eco_air_cell_64m", ECOTier.L6, Rarity.RARE);
    static final ItemEntry<ECOStorageCellItem> AIR_CELL_256M = cell("eco_air_cell_256m", ECOTier.L9, Rarity.EPIC);

    private static ItemEntry<ECOStorageCellItem> cell(String id, ECOTier tier, Rarity rarity) {
        return REGISTRATE
                .item(
                        id,
                        properties -> new ECOStorageCellItem(
                                properties.stacksTo(1).rarity(rarity),
                                tier,
                                AppliedPneumaticsCompat.getAirKeyType(),
                                NEAppliedPneumaticsCellTypes.AIR))
                .lang("ECO - LE" + tier.getTier() + " Storage Matrix (Air)")
                .model((ctx, prov) -> {})
                .register();
    }

    private NEAppliedPneumaticsItems() {}

    static void register() {}
}
