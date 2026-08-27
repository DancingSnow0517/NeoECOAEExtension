package cn.dancingsnow.neoecoae.integration.megacells;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaEnergyStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;

import java.util.List;

import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.BASE_16M_CAPACITY;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.BASE_256M_CAPACITY;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.BASE_64M_CAPACITY;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.COMPRESSED_4G_CAPACITY;

final class NEMegaEnergyItems {
    static final ItemEntry<MaterialItem> MEGA_ENERGY_CELL_HOUSING =
        NEMegaItems.optionalHousing("mega_energy", "Mega Energy");
    static final ItemEntry<ECOMegaEnergyStorageCellItem> CELL_16M = cell("16m", ECOTier.L4, BASE_16M_CAPACITY, Rarity.UNCOMMON);
    static final ItemEntry<ECOMegaEnergyStorageCellItem> CELL_64M = cell("64m", ECOTier.L6, BASE_64M_CAPACITY, Rarity.RARE);
    static final ItemEntry<ECOMegaEnergyStorageCellItem> CELL_256M = cell("256m", ECOTier.L9, BASE_256M_CAPACITY, Rarity.EPIC);
    static final ItemEntry<ECOMegaEnergyStorageCellItem> CELL_4G = cell("4g", ECOTier.L9, COMPRESSED_4G_CAPACITY, Rarity.EPIC);

    private static ItemEntry<ECOMegaEnergyStorageCellItem> cell(String size, ECOTier tier, long capacity, Rarity rarity) {
        return NEMegaItems.optionalCell("mega_energy", "Mega Energy", size, tier, capacity, rarity,
            ECOMegaEnergyStorageCellItem::new, NEMegaEnergyCellType.MEGA_ENERGY);
    }

    static List<ItemEntry<ECOMegaEnergyStorageCellItem>> cells() {
        return List.of(CELL_16M, CELL_64M, CELL_256M, CELL_4G);
    }

    static void register() {
    }
}
