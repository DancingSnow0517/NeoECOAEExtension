package cn.dancingsnow.neoecoae.integration.appex;

import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.IntegrationCells.Size;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEAppliedExperiencedItems {
    static final ItemEntry<Item> EXPERIENCE_CELL_HOUSING = REGISTRATE
        .item("eco_experience_cell_housing", Item::new)
        .lang("ECO Storage Matrix Housing (Experience)")
        .model((ctx, prov) -> {})
        .register();

    static final ItemEntry<ECOStorageCellItem> EXPERIENCE_CELL_16M = cell("eco_experience_cell_16m", Size.L4);
    static final ItemEntry<ECOStorageCellItem> EXPERIENCE_CELL_64M = cell("eco_experience_cell_64m", Size.L6);
    static final ItemEntry<ECOStorageCellItem> EXPERIENCE_CELL_256M = cell("eco_experience_cell_256m", Size.L9);

    private static ItemEntry<ECOStorageCellItem> cell(String name, Size size) {
        return IntegrationCells.cell(name, size, (properties, tier) -> new ECOStorageCellItem(
                properties,
                tier,
                ExternalCellKeyTypes.byId("appex", "experience"),
                NEAppliedExperiencedCellTypes.EXPERIENCE
            ))
            .lang("ECO - LE" + size.tier().getTier() + " Storage Matrix (Experience)")
            .model((ctx, prov) -> {})
            .register();
    }

    private NEAppliedExperiencedItems() {
    }

    static void register() {
    }
}
