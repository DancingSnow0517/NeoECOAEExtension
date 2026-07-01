package cn.dancingsnow.neoecoae.compat.appbot;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;

public class NEAppBotItems {
    public static final ItemEntry<MaterialItem> ECO_MANA_CELL_HOUSING = REGISTRATE
            .item("eco_mana_cell_housing", MaterialItem::new)
            .lang("ECO Mana Storage Matrix Housing")
            .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_MANA_CELL_16M = REGISTRATE
            .item(
                    "eco_mana_storage_cell_16m",
                    p -> new ECOStorageCellItem(
                            p.stacksTo(1).rarity(Rarity.UNCOMMON),
                            ECOTier.L4,
                            AppBotCompat.getManaKeyType(),
                            NEAppBotCellTypes.MANA))
            .lang("ECO - LE4 Mana Storage Matrix")
            .model(ItemModelUtil.cellModel("mana", "16m"))
            .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_MANA_CELL_64M = REGISTRATE
            .item(
                    "eco_mana_storage_cell_64m",
                    p -> new ECOStorageCellItem(
                            p.stacksTo(1).rarity(Rarity.RARE),
                            ECOTier.L6,
                            AppBotCompat.getManaKeyType(),
                            NEAppBotCellTypes.MANA))
            .lang("ECO - LE6 Mana Storage Matrix")
            .model(ItemModelUtil.cellModel("mana", "64m"))
            .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_MANA_CELL_256M = REGISTRATE
            .item(
                    "eco_mana_storage_cell_256m",
                    p -> new ECOStorageCellItem(
                            p.stacksTo(1).rarity(Rarity.EPIC),
                            ECOTier.L9,
                            AppBotCompat.getManaKeyType(),
                            NEAppBotCellTypes.MANA))
            .lang("ECO - LE9 Mana Storage Matrix")
            .model(ItemModelUtil.cellModel("mana", "256m"))
            .register();

    public static void register() {}
}
