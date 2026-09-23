package cn.dancingsnow.neoecoae.integration.megacells;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.ExternalCellKeyTypes;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.List;
import java.util.function.Supplier;

import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.MEGA_4G_CAPACITY;

/** MEGA channel cells that only exist when their resource integration is loaded. */
final class NEMegaExternalItems {
    private NEMegaExternalItems() {
    }

    static List<ItemEntry<ECOStorageCellItem>> registerAir() {
        return Air.CELLS;
    }

    static List<ItemEntry<ECOStorageCellItem>> registerExperience() {
        return Experience.CELLS;
    }

    static List<ItemEntry<ECOStorageCellItem>> registerSoul() {
        return Soul.CELLS;
    }

    static List<ItemEntry<ECOStorageCellItem>> registerMana() {
        return Mana.CELLS;
    }

    static List<ItemEntry<ECOStorageCellItem>> registerSource() {
        return Source.CELLS;
    }

    private static ItemEntry<Item> housing(String family, String displayFamily) {
        return NEMegaItems.housing(family, displayFamily);
    }

    private static List<ItemEntry<ECOStorageCellItem>> cells(
        String family,
        String displayFamily,
        Supplier<AEKeyType> keyType,
        Supplier<ECOCellType> cellType
    ) {
        return List.of(cell(family, displayFamily, "4g", ECOTier.L9, MEGA_4G_CAPACITY,
            Rarity.EPIC, keyType, cellType));
    }

    private static ItemEntry<ECOStorageCellItem> cell(
        String family,
        String displayFamily,
        String size,
        ECOTier tier,
        long capacity,
        Rarity rarity,
        Supplier<AEKeyType> keyType,
        Supplier<ECOCellType> cellType
    ) {
        return NEMegaItems.optionalCell(family, displayFamily, size, tier, capacity, rarity,
            (properties, cellTier, type, cellCapacity) -> new ECOStorageCellItem(
                properties,
                cellTier,
                keyType,
                type,
                cellCapacity,
                MegaCellCapacities.normalBytesPerType(cellTier),
                MegaCellCapacities.normalIdleDrain(cellCapacity)
            ),
            cellType
        );
    }

    private static final class Air {
        private static final ItemEntry<Item> HOUSING = housing("mega_air", "Mega Air");
        private static final List<ItemEntry<ECOStorageCellItem>> CELLS = cells("mega_air", "Mega Air",
            ExternalCellKeyTypes.byId("appliedpneumatics", "air_type"), NEMegaExternalCellTypes.air());
    }

    private static final class Experience {
        private static final ItemEntry<Item> HOUSING = housing("mega_experience", "Mega Experience");
        private static final List<ItemEntry<ECOStorageCellItem>> CELLS = cells("mega_experience", "Mega Experience",
            ExternalCellKeyTypes.byId("appex", "experience"), NEMegaExternalCellTypes.experience());
    }

    private static final class Soul {
        private static final ItemEntry<Item> HOUSING = housing("mega_soul", "Mega Soul");
        private static final List<ItemEntry<ECOStorageCellItem>> CELLS = cells("mega_soul", "Mega Soul",
            ExternalCellKeyTypes.byId("soulplied_energistics", "soul"), NEMegaExternalCellTypes.soul());
    }

    private static final class Mana {
        private static final ItemEntry<Item> HOUSING = housing("mega_mana", "Mega Mana");
        private static final List<ItemEntry<ECOStorageCellItem>> CELLS = cells("mega_mana", "Mega Mana",
            ExternalCellKeyTypes.byId("appbot", "mana"), NEMegaExternalCellTypes.mana());
    }

    private static final class Source {
        private static final ItemEntry<Item> HOUSING = housing("mega_source", "Mega Source");
        private static final List<ItemEntry<ECOStorageCellItem>> CELLS = cells("mega_source", "Mega Source",
            ExternalCellKeyTypes.byId("arseng", "source"), NEMegaExternalCellTypes.source());
    }
}
