package cn.dancingsnow.neoecoae.integration.dataenergistics;

import appeng.items.materials.MaterialItem;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.IntegrationCells.Size;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;
public class NEDataItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    public static final ItemEntry<MaterialItem> ECO_DATA_CELL_HOUSING = REGISTRATE
        .item("eco_data_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            RecipeOutput dataenergisticsInstalled = prov.withConditions(new ModLoadedCondition(ModIds.DATA_ENERGISTICS));
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                .pattern("ABA")
                .pattern("B B")
                .pattern("CDC")
                .define('A', NEItems.CRYSTAL_MATRIX)
                .define('B', Tags.Items.DUSTS_REDSTONE)
                .define('C', NETags.Items.OBSIDIAN_INGOT)
                .define('D', NETags.Items.DATA_CRYSTAL)
                .unlockedBy("has_crystal_matrix", RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                .unlockedBy("has_obsidian_ingot", RegistrateRecipeProvider.has(NETags.Items.OBSIDIAN_INGOT))
                .unlockedBy("has_data_crystal", RegistrateRecipeProvider.has(NETags.Items.DATA_CRYSTAL))
                .save(dataenergisticsInstalled);
        })
        .lang("ECO Storage Matrix Housing (Data)")
        .model(ItemModelUtil.importedCellModel("eco_data_cell_housing"))
        .register();

    public static final ItemEntry<ECODataStorageCellItem> ECO_DATA_CELL_16M = IntegrationCells
        .cell("eco_data_storage_cell_16m", Size.L4, p -> new ECODataStorageCellItem(
            p,
            ECOTier.L4
        ))
        .recipe(cellRecipe(Size.L4))
        .lang("ECO - LE4 Storage Matrix (Data)")
        .model(ItemModelUtil.cellModel("data", Size.L4.suffix()))
        .register();

    public static final ItemEntry<ECODataStorageCellItem> ECO_DATA_CELL_64M = IntegrationCells
        .cell("eco_data_storage_cell_64m", Size.L6, p -> new ECODataStorageCellItem(
            p,
            ECOTier.L6
        ))
        .recipe(cellRecipe(Size.L6))
        .lang("ECO - LE6 Storage Matrix (Data)")
        .model(ItemModelUtil.cellModel("data", Size.L6.suffix()))
        .register();

    public static final ItemEntry<ECODataStorageCellItem> ECO_DATA_CELL_256M = IntegrationCells
        .cell("eco_data_storage_cell_256m", Size.L9, p -> new ECODataStorageCellItem(
            p,
            ECOTier.L9
        ))
        .recipe(cellRecipe(Size.L9))
        .lang("ECO - LE9 Storage Matrix (Data)")
        .model(ItemModelUtil.cellModel("data", Size.L9.suffix()))
        .register();

    public static void register() {
    }

    /**
     * Integrated Working Station recipe (10 components plus the housing) and its disassembly for one cell size.
     */
    private static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrateRecipeProvider> cellRecipe(Size size) {
        return (ctx, prov) -> {
            RecipeOutput dataenergisticsInstalled = prov.withConditions(new ModLoadedCondition(ModIds.DATA_ENERGISTICS));
            IntegratedWorkingStationRecipe.builder()
                .require(size.component(), 10)
                .require(ECO_DATA_CELL_HOUSING)
                .energy(energyFor(size.tier()))
                .itemOutput(ctx.get())
                .save(dataenergisticsInstalled, ctx.getId().withPrefix("integrated_working_station/"));
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(
                ECO_DATA_CELL_HOUSING.asStack(), size.component().asStack(10)));
            dataenergisticsInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        };
    }

    private static int energyFor(ECOTier tier) {
        return switch (tier) {
            case L4 -> 1_000;
            case L6 -> 12_000;
            case L9 -> 144_000;
        };
    }
}
