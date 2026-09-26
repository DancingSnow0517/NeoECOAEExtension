package cn.dancingsnow.neoecoae.integration.dataenergistics;

import appeng.items.materials.MaterialItem;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Rarity;
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
            RecipeOutput dataenergisticsInstalled = prov.withConditions(new ModLoadedCondition("data_energistics"));
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

    public static final ItemEntry<ECODataStorageCellItem> ECO_DATA_CELL_16M = REGISTRATE
        .item("eco_data_storage_cell_16m", p -> new ECODataStorageCellItem(
            p,
            ECOTier.L4
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))
        .recipe((ctx, prov) -> {
            RecipeOutput dataenergisticsInstalled = prov.withConditions(new ModLoadedCondition("data_energistics"));
            IntegratedWorkingStationRecipe.builder()
                .require(NEItems.ECO_CELL_COMPONENT_16M, 10)
                .require(NEDataItems.ECO_DATA_CELL_HOUSING)
                .energy(energyFor(ECOTier.L4))
                .itemOutput(ctx.get())
                .save(dataenergisticsInstalled, ctx.getId().withPrefix("integrated_working_station/"));
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(
                NEDataItems.ECO_DATA_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_16M.asStack(10)));
            dataenergisticsInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE4 Storage Matrix (Data)")
        .model(ItemModelUtil.cellModel("data", "16m"))
        .register();

    public static final ItemEntry<ECODataStorageCellItem> ECO_DATA_CELL_64M = REGISTRATE
        .item("eco_data_storage_cell_64m", p -> new ECODataStorageCellItem(
            p,
            ECOTier.L6
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.RARE))
        .recipe((ctx, prov) -> {
            RecipeOutput dataenergisticsInstalled = prov.withConditions(new ModLoadedCondition("data_energistics"));
            IntegratedWorkingStationRecipe.builder()
                .require(NEItems.ECO_CELL_COMPONENT_64M, 10)
                .require(NEDataItems.ECO_DATA_CELL_HOUSING)
                .energy(energyFor(ECOTier.L6))
                .itemOutput(ctx.get())
                .save(dataenergisticsInstalled, ctx.getId().withPrefix("integrated_working_station/"));
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(
                NEDataItems.ECO_DATA_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_64M.asStack(10)));
            dataenergisticsInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE6 Storage Matrix (Data)")
        .model(ItemModelUtil.cellModel("data", "64m"))
        .register();

    public static final ItemEntry<ECODataStorageCellItem> ECO_DATA_CELL_256M = REGISTRATE
        .item("eco_data_storage_cell_256m", p -> new ECODataStorageCellItem(
            p,
            ECOTier.L9
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.EPIC))
        .recipe((ctx, prov) -> {
            RecipeOutput dataenergisticsInstalled = prov.withConditions(new ModLoadedCondition("data_energistics"));
            IntegratedWorkingStationRecipe.builder()
                .require(NEItems.ECO_CELL_COMPONENT_256M, 10)
                .require(NEDataItems.ECO_DATA_CELL_HOUSING)
                .energy(energyFor(ECOTier.L9))
                .itemOutput(ctx.get())
                .save(dataenergisticsInstalled, ctx.getId().withPrefix("integrated_working_station/"));
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(
                NEDataItems.ECO_DATA_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_256M.asStack(10)));
            dataenergisticsInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
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
