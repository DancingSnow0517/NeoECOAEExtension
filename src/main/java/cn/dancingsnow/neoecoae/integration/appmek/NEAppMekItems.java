package cn.dancingsnow.neoecoae.integration.appmek;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.IntegrationCells.Size;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.appmek.item.ECOChemicalStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
public class NEAppMekItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final ItemEntry<MaterialItem> ECO_CHEMICAL_CELL_HOUSING = REGISTRATE
        .item("eco_chemical_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            RecipeOutput appmekInstalled = prov.withConditions(new ModLoadedCondition(ModIds.APPMEK));
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("B B")
                .pattern("CCC")
                .define('A', NEItems.CRYSTAL_MATRIX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)
                .unlockedBy("has_crystal_matrix", RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                .unlockedBy("has_black_tungsten_alloy", RegistrateRecipeProvider.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT))
                .save(appmekInstalled);
        })
        .lang("ECO Storage Matrix Housing (Chemical)")
        .model(ItemModelUtil.importedCellModel("eco_chemical_cell_housing"))
        .register();

    public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_16M = IntegrationCells
        .cell("eco_chemical_storage_cell_16m", Size.L4, (p, tier) -> new ECOChemicalStorageCellItem(
            p,
            tier
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.APPMEK, ECO_CHEMICAL_CELL_HOUSING, Size.L4))
        .lang("ECO - LE4 Storage Matrix (Chemical)")
        .model(ItemModelUtil.cellModel("chemical", Size.L4.suffix()))
        .register();

    public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_64M = IntegrationCells
        .cell("eco_chemical_storage_cell_64m", Size.L6, (p, tier) -> new ECOChemicalStorageCellItem(
            p,
            tier
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.APPMEK, ECO_CHEMICAL_CELL_HOUSING, Size.L6))
        .lang("ECO - LE6 Storage Matrix (Chemical)")
        .model(ItemModelUtil.cellModel("chemical", Size.L6.suffix()))
        .register();

    public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_256M = IntegrationCells
        .cell("eco_chemical_storage_cell_256m", Size.L9, (p, tier) -> new ECOChemicalStorageCellItem(
            p,
            tier
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.APPMEK, ECO_CHEMICAL_CELL_HOUSING, Size.L9))
        .lang("ECO - LE9 Storage Matrix (Chemical)")
        .model(ItemModelUtil.cellModel("chemical", Size.L9.suffix()))
        .register();

    public static void register() {
    }
}
