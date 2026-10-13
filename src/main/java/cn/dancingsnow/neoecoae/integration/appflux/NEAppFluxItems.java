package cn.dancingsnow.neoecoae.integration.appflux;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.IntegrationCells.Size;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.appflux.item.ECOFeStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.glodblock.github.appflux.common.AFSingletons;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppFluxItems {

    public static final ItemEntry<MaterialItem> ECO_FE_CELL_HOUSING = REGISTRATE
        .item("eco_fe_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            RecipeOutput appFluxInstalled = prov.withConditions(new ModLoadedCondition(ModIds.APPFLUX));
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("B B")
                .pattern("CCC")
                .define('A', NEItems.CRYSTAL_MATRIX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', AFSingletons.HARDEN_INSULATING_RESIN)
                .unlockedBy("has_crystal_matrix", RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                .unlockedBy("has_harden_insulating_resin", RegistrateRecipeProvider.has(AFSingletons.HARDEN_INSULATING_RESIN))
                .save(appFluxInstalled);
        })
        .lang("ECO Storage Matrix Housing (FE)")
        .model(ItemModelUtil.importedCellModel("eco_energy_cell_housing"))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_16M = IntegrationCells
        .cell("eco_fe_storage_cell_16m", Size.L4, p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L4,
            NEAppFluxCellTypes.FLUX
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.APPFLUX, ECO_FE_CELL_HOUSING, Size.L4))
        .lang("ECO - LE4 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", Size.L4.suffix()))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_64M = IntegrationCells
        .cell("eco_fe_storage_cell_64m", Size.L6, p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L6,
            NEAppFluxCellTypes.FLUX
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.APPFLUX, ECO_FE_CELL_HOUSING, Size.L6))
        .lang("ECO - LE6 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", Size.L6.suffix()))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_256M = IntegrationCells
        .cell("eco_fe_storage_cell_256m", Size.L9, p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L9,
            NEAppFluxCellTypes.FLUX
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.APPFLUX, ECO_FE_CELL_HOUSING, Size.L9))
        .lang("ECO - LE9 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", Size.L9.suffix()))
        .register();

    public static void register() {
    }
}
