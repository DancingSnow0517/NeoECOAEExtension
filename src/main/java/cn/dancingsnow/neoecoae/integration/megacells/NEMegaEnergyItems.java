package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaStorageCellItem;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item;

import java.util.List;

import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.MEGA_4G_CAPACITY;

final class NEMegaEnergyItems {
    static final ItemEntry<Item> MEGA_ENERGY_CELL_HOUSING =
        NEMegaItems.housing("mega_energy", "Mega Energy");
    static final ItemEntry<ECOMegaStorageCellItem> CELL_4G = cell("4g", ECOTier.L9, MEGA_4G_CAPACITY, Rarity.EPIC);

    private static ItemEntry<ECOMegaStorageCellItem> cell(String size, ECOTier tier, long capacity, Rarity rarity) {
        return NEMegaItems.optionalCell("mega_energy", "Mega Energy", size, tier, capacity, rarity,
            (properties, cellTier, type, cellCapacity) -> new ECOMegaStorageCellItem(
                properties, cellTier, FluxKeyType.TYPE, type, cellCapacity, null),
            NEMegaEnergyCellType.MEGA_ENERGY);
    }

    static List<ItemEntry<ECOMegaStorageCellItem>> cells() {
        return List.of(CELL_4G);
    }

    static void register() {
        // Intentional class-initialization barrier: registration order must remain explicit.
    }
}
