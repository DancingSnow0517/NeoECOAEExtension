package cn.dancingsnow.neoecoae.compat.appflux;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.compat.appflux.item.ECOFeStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;

public class NEAppFluxItems {
    public static final ItemEntry<MaterialItem> ECO_FE_CELL_HOUSING = REGISTRATE
            .item("eco_fe_cell_housing", MaterialItem::new)
            .lang("ECO Storage Matrix Housing (FE)")
            .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_16M = REGISTRATE
            .item(
                    "eco_fe_storage_cell_16m",
                    p -> new ECOFeStorageCellItem(p.stacksTo(1).rarity(Rarity.UNCOMMON), ECOTier.L4))
            .lang("ECO - LE4 Storage Matrix (FE)")
            .model(ItemModelUtil.cellModel("fe", "16m"))
            .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_64M = REGISTRATE
            .item(
                    "eco_fe_storage_cell_64m",
                    p -> new ECOFeStorageCellItem(p.stacksTo(1).rarity(Rarity.RARE), ECOTier.L6))
            .lang("ECO - LE6 Storage Matrix (FE)")
            .model(ItemModelUtil.cellModel("fe", "64m"))
            .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_256M = REGISTRATE
            .item(
                    "eco_fe_storage_cell_256m",
                    p -> new ECOFeStorageCellItem(p.stacksTo(1).rarity(Rarity.EPIC), ECOTier.L9))
            .lang("ECO - LE9 Storage Matrix (FE)")
            .model(ItemModelUtil.cellModel("fe", "256m"))
            .register();

    public static void register() {}
}
