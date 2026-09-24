package cn.dancingsnow.neoecoae.integration.ae2lt;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

final class NELightningItems {
    static final ItemEntry<Item> ECO_LIGHTNING_CELL_HOUSING = REGISTRATE
            .item("eco_lightning_cell_housing", Item::new)
            .lang("ECO Lightning Storage Matrix Housing")
            .model((ctx, prov) -> {})
            .register();

    static final ItemEntry<ECOStorageCellItem> ECO_LIGHTNING_CELL_16M =
            cell("eco_lightning_cell_16m", ECOTier.L4, Rarity.UNCOMMON, 1_048_576L, 32_768);
    static final ItemEntry<ECOStorageCellItem> ECO_LIGHTNING_CELL_64M =
            cell("eco_lightning_cell_64m", ECOTier.L6, Rarity.RARE, 4_194_304L, 131_072);
    static final ItemEntry<ECOStorageCellItem> ECO_LIGHTNING_CELL_256M =
            cell("eco_lightning_cell_256m", ECOTier.L9, Rarity.EPIC, 16_777_216L, 524_288);

    private static ItemEntry<ECOStorageCellItem> cell(
            String id, ECOTier tier, Rarity rarity, long usableCapacity, double idleDrain) {
        return REGISTRATE
                .item(
                        id,
                        properties -> new ECOStorageCellItem(
                                properties.stacksTo(1).rarity(rarity),
                                tier,
                                AE2LightningTechCompat.getLightningKeyType(),
                                NELightningCellTypes.LIGHTNING,
                                usableCapacity + 16,
                                8,
                                2,
                                idleDrain))
                .lang("ECO - LE" + tier.name().substring(1) + " Storage Matrix (Lightning)")
                .model((ctx, prov) -> {})
                .register();
    }

    private NELightningItems() {}

    static void register() {}
}
