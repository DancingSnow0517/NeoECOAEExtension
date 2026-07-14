package cn.dancingsnow.neoecoae.integration.arsenergistique;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;

public class NEArsEnergistiqueItems {
    public static final ItemEntry<MaterialItem> ECO_SOURCE_CELL_HOUSING = REGISTRATE
            .item("eco_source_cell_housing", MaterialItem::new)
            .lang("ECO Source Storage Matrix Housing")
            .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_16M = REGISTRATE
            .item(
                    "eco_source_storage_cell_16m",
                    p -> new ECOStorageCellItem(
                            p.stacksTo(1).rarity(Rarity.UNCOMMON),
                            ECOTier.L4,
                            ArsEnergistiqueCompat.getSourceKeyType(),
                            NEArsEnergistiqueCellTypes.SOURCE))
            .lang("ECO - LE4 Source Storage Matrix")
            .model(ItemModelUtil.cellModel("source", "16m"))
            .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_64M = REGISTRATE
            .item(
                    "eco_source_storage_cell_64m",
                    p -> new ECOStorageCellItem(
                            p.stacksTo(1).rarity(Rarity.RARE),
                            ECOTier.L6,
                            ArsEnergistiqueCompat.getSourceKeyType(),
                            NEArsEnergistiqueCellTypes.SOURCE))
            .lang("ECO - LE6 Source Storage Matrix")
            .model(ItemModelUtil.cellModel("source", "64m"))
            .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_256M = REGISTRATE
            .item(
                    "eco_source_storage_cell_256m",
                    p -> new ECOStorageCellItem(
                            p.stacksTo(1).rarity(Rarity.EPIC),
                            ECOTier.L9,
                            ArsEnergistiqueCompat.getSourceKeyType(),
                            NEArsEnergistiqueCellTypes.SOURCE))
            .lang("ECO - LE9 Source Storage Matrix")
            .model(ItemModelUtil.cellModel("source", "256m"))
            .register();

    public static void register() {}
}
