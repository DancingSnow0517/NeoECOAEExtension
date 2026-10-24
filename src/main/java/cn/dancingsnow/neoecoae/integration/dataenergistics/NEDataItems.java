package cn.dancingsnow.neoecoae.integration.dataenergistics;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Rarity;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
public class NEDataItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final ItemEntry<MaterialItem> ECO_DATA_CELL_HOUSING = REGISTRATE
        .item("eco_data_cell_housing", MaterialItem::new)

        .lang("ECO Storage Matrix Housing (Data)")
        .model(ItemModelUtil.importedCellModel("eco_data_cell_housing"))
        .register();

    public static final ItemEntry<ECODataStorageCellItem> ECO_DATA_CELL_16M = REGISTRATE
        .item("eco_data_storage_cell_16m", p -> new ECODataStorageCellItem(
            p,
            ECOTier.L4
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))

        .lang("ECO - LE4 Storage Matrix (Data)")
        .model(ItemModelUtil.cellModel("data", "16m"))
        .register();

    public static final ItemEntry<ECODataStorageCellItem> ECO_DATA_CELL_64M = REGISTRATE
        .item("eco_data_storage_cell_64m", p -> new ECODataStorageCellItem(
            p,
            ECOTier.L6
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.RARE))

        .lang("ECO - LE6 Storage Matrix (Data)")
        .model(ItemModelUtil.cellModel("data", "64m"))
        .register();

    public static final ItemEntry<ECODataStorageCellItem> ECO_DATA_CELL_256M = REGISTRATE
        .item("eco_data_storage_cell_256m", p -> new ECODataStorageCellItem(
            p,
            ECOTier.L9
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.EPIC))

        .lang("ECO - LE9 Storage Matrix (Data)")
        .model(ItemModelUtil.cellModel("data", "256m"))
        .register();

    public static void register() {
    }

    private static int energyFor(ECOTier tier) {
        return switch (tier) {
            case L4 -> 1_000;
            case L6 -> 12_000;
            case L9 -> 144_000;
        };
    }
}
