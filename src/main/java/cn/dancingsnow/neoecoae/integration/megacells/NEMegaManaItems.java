package cn.dancingsnow.neoecoae.integration.megacells;

import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.MEGA_4G_CAPACITY;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.appbot.AppBotCompat;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

final class NEMegaManaItems {
    static final ItemEntry<Item> MEGA_MANA_CELL_HOUSING = NEMegaItems.housing("mega_mana", "Mega Mana");
    static final ItemEntry<ECOStorageCellItem> CELL_4G = NEMegaItems.optionalCell(
            "mega_mana",
            "Mega Mana",
            "4g",
            ECOTier.L9,
            MEGA_4G_CAPACITY,
            Rarity.EPIC,
            (properties, tier, type, capacity) -> new ECOStorageCellItem(
                    properties,
                    tier,
                    AppBotCompat.getManaKeyType(),
                    type,
                    capacity,
                    MegaCellCapacities.normalBytesPerType(tier),
                    1,
                    MegaCellCapacities.normalIdleDrain(capacity)),
            NEMegaManaCellType.MEGA_MANA);

    static List<ItemEntry<ECOStorageCellItem>> cells() {
        return List.of(CELL_4G);
    }

    static void register() {
        // Class initialization registers the optional items.
    }
}
