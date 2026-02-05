package cn.dancingsnow.neoecoae.integration.appmek;

import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.api.ECOAETypeCounts;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import net.minecraft.world.item.Rarity;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
public class NEAppMekItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final ItemEntry<ECOStorageCellItem> ECO_CHEMICAL_CELL_16M = REGISTRATE
        .item("eco_chemical_storage_cell_16m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.UNCOMMON),
            ECOTier.L4,
            MekanismKeyType.TYPE
        ))
        .lang("ECO - LE4 Storage Matrix (Chemical)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_CHEMICAL_CELL_64M = REGISTRATE
        .item("eco_chemical_storage_cell_64m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.RARE),
            ECOTier.L6,
            MekanismKeyType.TYPE
        ))
        .lang("ECO - LE6 Storage Matrix (Chemical)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_CHEMICAL_CELL_256M = REGISTRATE
        .item("eco_chemical_storage_cell_256m", p -> new ECOStorageCellItem(
            p.stacksTo(1).rarity(Rarity.EPIC),
            ECOTier.L9,
            MekanismKeyType.TYPE
        ))
        .lang("ECO - LE9 Storage Matrix (Chemical)")
        .register();

    public static void register() {
        ECOAETypeCounts.register(MekanismKeyType.TYPE, 25);
    }
}
