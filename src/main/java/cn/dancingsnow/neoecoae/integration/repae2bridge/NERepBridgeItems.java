package cn.dancingsnow.neoecoae.integration.repae2bridge;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

/** Names come from NELangGenerator so datagen without Replication AE2 Bridge still emits them. */
final class NERepBridgeItems {
    static final ItemEntry<Item> MATTER_CELL_HOUSING = REGISTRATE
        .item("eco_rep_matter_cell_housing", Item::new)
        .setData(ProviderType.LANG, NonNullBiConsumer.noop())
        .model(ItemModelUtil.compatHousingModel("rep_ae2_bridge", "rep_matter_cell_housing"))
        .register();

    static final ItemEntry<ECOStorageCellItem> MATTER_CELL_16M = cell("16m", ECOTier.L4, Rarity.UNCOMMON);
    static final ItemEntry<ECOStorageCellItem> MATTER_CELL_64M = cell("64m", ECOTier.L6, Rarity.RARE);
    static final ItemEntry<ECOStorageCellItem> MATTER_CELL_256M = cell("256m", ECOTier.L9, Rarity.EPIC);

    private static ItemEntry<ECOStorageCellItem> cell(String size, ECOTier tier, Rarity rarity) {
        return REGISTRATE.item("eco_rep_matter_cell_" + size, properties -> new ECOStorageCellItem(
                properties.stacksTo(1).rarity(rarity), tier,
                ExternalCellKeyTypes.byId("rep_ae2_bridge", "matter"), NERepBridgeCellTypes.MATTER))
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .model(ItemModelUtil.compatCellModel("rep_ae2_bridge", "rep_matter_cell_housing", size))
            .register();
    }

    static List<ItemEntry<ECOStorageCellItem>> cells() {
        return List.of(MATTER_CELL_16M, MATTER_CELL_64M, MATTER_CELL_256M);
    }

    private NERepBridgeItems() {}

    static void register() {}
}
