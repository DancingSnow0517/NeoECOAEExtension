package cn.dancingsnow.neoecoae.integration.appex;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEAppliedExperiencedItems {
    static final ItemEntry<Item> EXPERIENCE_CELL_HOUSING = REGISTRATE
        .item("eco_experience_cell_housing", Item::new)
        .lang("ECO Storage Matrix Housing (Experience)")
        .model((ctx, prov) -> {})
        .register();

    static final ItemEntry<ECOStorageCellItem> EXPERIENCE_CELL_16M = cell("eco_experience_cell_16m", ECOTier.L4, Rarity.UNCOMMON);
    static final ItemEntry<ECOStorageCellItem> EXPERIENCE_CELL_64M = cell("eco_experience_cell_64m", ECOTier.L6, Rarity.RARE);
    static final ItemEntry<ECOStorageCellItem> EXPERIENCE_CELL_256M = cell("eco_experience_cell_256m", ECOTier.L9, Rarity.EPIC);

    private static ItemEntry<ECOStorageCellItem> cell(String name, ECOTier tier, Rarity rarity) {
        return REGISTRATE.item(name, properties -> new ECOStorageCellItem(
                properties.stacksTo(1).rarity(rarity),
                tier,
                ExternalCellKeyTypes.byId("appex", "experience"),
                NEAppliedExperiencedCellTypes.EXPERIENCE
            ))
            .lang("ECO - LE" + tier.getTier() + " Storage Matrix (Experience)")
            .model((ctx, prov) -> {})
            .register();
    }

    private NEAppliedExperiencedItems() {
    }

    static void register() {
    }
}
