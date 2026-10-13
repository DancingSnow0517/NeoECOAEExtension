package cn.dancingsnow.neoecoae.integration.appliedsoul;

import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.IntegrationCells.Size;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

final class NEAppliedSoulItems {
    static final ItemEntry<Item> SOUL_CELL_HOUSING = REGISTRATE
        .item("eco_soul_cell_housing", Item::new)
        .lang("ECO Storage Matrix Housing (Soul)")
        .model((ctx, prov) -> {})
        .register();

    static final ItemEntry<ECOStorageCellItem> SOUL_CELL_16M = cell("eco_soul_cell_16m", Size.L4);
    static final ItemEntry<ECOStorageCellItem> SOUL_CELL_64M = cell("eco_soul_cell_64m", Size.L6);
    static final ItemEntry<ECOStorageCellItem> SOUL_CELL_256M = cell("eco_soul_cell_256m", Size.L9);

    private static ItemEntry<ECOStorageCellItem> cell(String name, Size size) {
        return IntegrationCells.cell(name, size, properties -> new ECOStorageCellItem(
                properties,
                size.tier(),
                ExternalCellKeyTypes.byId("soulplied_energistics", "soul"),
                NEAppliedSoulCellTypes.SOUL
            ))
            .lang("ECO - LE" + size.tier().getTier() + " Storage Matrix (Soul)")
            .model((ctx, prov) -> {})
            .register();
    }

    private NEAppliedSoulItems() {
    }

    static void register() {
    }
}
