package cn.dancingsnow.neoecoae.integration.appliedsoul;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEAppliedSoulItems {
    static final ItemEntry<Item> SOUL_CELL_HOUSING = REGISTRATE
        .item("eco_soul_cell_housing", Item::new)
        .lang("ECO Storage Matrix Housing (Soul)")
        .model((ctx, prov) -> {})
        .register();

    static final ItemEntry<ECOStorageCellItem> SOUL_CELL_16M = cell("eco_soul_cell_16m", ECOTier.L4, Rarity.UNCOMMON);
    static final ItemEntry<ECOStorageCellItem> SOUL_CELL_64M = cell("eco_soul_cell_64m", ECOTier.L6, Rarity.RARE);
    static final ItemEntry<ECOStorageCellItem> SOUL_CELL_256M = cell("eco_soul_cell_256m", ECOTier.L9, Rarity.EPIC);

    private static ItemEntry<ECOStorageCellItem> cell(String name, ECOTier tier, Rarity rarity) {
        return REGISTRATE.item(name, properties -> new ECOStorageCellItem(
                properties.stacksTo(1).rarity(rarity),
                tier,
                ExternalCellKeyTypes.byId("soulplied_energistics", "soul"),
                NEAppliedSoulCellTypes.SOUL
            ))
            .lang("ECO - LE" + tier.getTier() + " Storage Matrix (Soul)")
            .model((ctx, prov) -> {})
            .register();
    }

    private NEAppliedSoulItems() {
    }

    static void register() {
    }
}
