package cn.dancingsnow.neoecoae.integration.appbot;

import appbot.ae2.ManaKeyType;
import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppBotItems {

    public static final ItemEntry<MaterialItem> ECO_MANA_CELL_HOUSING = REGISTRATE
        .item("eco_mana_cell_housing", MaterialItem::new)

        .lang("ECO Storage Matrix Housing (Mana)")
        .model(ItemModelUtil.importedCellModel("eco_mana_cell_housing"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_MANA_CELL_16M = REGISTRATE
        .item("eco_mana_storage_cell_16m", p -> new ECOStorageCellItem(
            p,
            ECOTier.L4,
            ManaKeyType.TYPE,
            NEAppBotCellTypes.MANA
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))

        .lang("ECO - LE4 Storage Matrix (Mana)")
        .model(ItemModelUtil.cellModel("mana", "16m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_MANA_CELL_64M = REGISTRATE
        .item("eco_mana_storage_cell_64m", p -> new ECOStorageCellItem(
            p,
            ECOTier.L6,
            ManaKeyType.TYPE,
            NEAppBotCellTypes.MANA
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.RARE))

        .lang("ECO - LE6 Storage Matrix (Mana)")
        .model(ItemModelUtil.cellModel("mana", "64m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_MANA_CELL_256M = REGISTRATE
        .item("eco_mana_storage_cell_256m", p -> new ECOStorageCellItem(
            p,
            ECOTier.L9,
            ManaKeyType.TYPE,
            NEAppBotCellTypes.MANA
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.EPIC))

        .lang("ECO - LE9 Storage Matrix (Mana)")
        .model(ItemModelUtil.cellModel("mana", "256m"))
        .register();

    public static void register() {

    }
}
