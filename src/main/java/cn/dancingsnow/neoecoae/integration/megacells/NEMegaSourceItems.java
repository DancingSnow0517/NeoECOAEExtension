package cn.dancingsnow.neoecoae.integration.megacells;

import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.MEGA_4G_CAPACITY;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.arsenergistique.ArsEnergistiqueCompat;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

final class NEMegaSourceItems {
    static final ItemEntry<Item> MEGA_SOURCE_CELL_HOUSING = NEMegaItems.housing("mega_source", "Mega Source");
    static final ItemEntry<ECOStorageCellItem> CELL_4G = NEMegaItems.optionalCell(
            "mega_source", "Mega Source", "4g", ECOTier.L9, MEGA_4G_CAPACITY, Rarity.EPIC,
            (properties, tier, type, capacity) -> new ECOStorageCellItem(
                    properties, tier, ArsEnergistiqueCompat.getSourceKeyType(), type, capacity,
                    MegaCellCapacities.normalBytesPerType(tier), 1,
                    MegaCellCapacities.normalIdleDrain(capacity)),
            NEMegaSourceCellType.MEGA_SOURCE);

    static List<ItemEntry<ECOStorageCellItem>> cells() {
        return List.of(CELL_4G);
    }

    static void register() {
        // Class initialization registers the optional items.
    }
}
