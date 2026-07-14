package cn.dancingsnow.neoecoae.integration.appmek;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.appmek.item.ECOChemicalStorageCellItem;
import cn.dancingsnow.neoecoae.data.recipe.ConditionalFinishedRecipe;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;

/**
 * Registers Applied Mekanistics chemical storage matrix housing and cells.
 *
 * <p>This class is loaded only by {@link AppMekIntegration}, after the
 * {@code appmek} mod is confirmed present.
 */
public class NEAppMekItems {
    public static final ItemEntry<MaterialItem> ECO_CHEMICAL_CELL_HOUSING = REGISTRATE
            .item("eco_chemical_cell_housing", MaterialItem::new)
            .recipe((ctx, prov) -> {
                Consumer<FinishedRecipe> appmekInstalled = appmekInstalled(prov);
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                        .pattern("ABA")
                        .pattern("B B")
                        .pattern("CCC")
                        .define('A', NEItems.CRYSTAL_MATRIX)
                        .define('B', Tags.Items.DUSTS_REDSTONE)
                        .define('C', NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)
                        .unlockedBy("has_crystal_matrix", RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                        .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                        .unlockedBy(
                                "has_black_tungsten_alloy",
                                RegistrateRecipeProvider.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT))
                        .save(appmekInstalled);
            })
            .lang("ECO Chemical Storage Matrix Housing")
            .register();

    public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_16M = REGISTRATE
            .item(
                    "eco_chemical_storage_cell_16m",
                    p -> new ECOChemicalStorageCellItem(p.stacksTo(1).rarity(Rarity.UNCOMMON), ECOTier.L4))
            .recipe((ctx, prov) -> {
                Consumer<FinishedRecipe> appmekInstalled = appmekInstalled(prov);
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                        .requires(ECO_CHEMICAL_CELL_HOUSING)
                        .requires(NEItems.ECO_CELL_COMPONENT_16M)
                        .unlockedBy("has_16m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_16M))
                        .save(appmekInstalled);
            })
            .lang("ECO - LE4 Chemical Storage Matrix")
            .model(ItemModelUtil.cellModel("chemical", "16m"))
            .register();

    public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_64M = REGISTRATE
            .item(
                    "eco_chemical_storage_cell_64m",
                    p -> new ECOChemicalStorageCellItem(p.stacksTo(1).rarity(Rarity.RARE), ECOTier.L6))
            .recipe((ctx, prov) -> {
                Consumer<FinishedRecipe> appmekInstalled = appmekInstalled(prov);
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                        .requires(ECO_CHEMICAL_CELL_HOUSING)
                        .requires(NEItems.ECO_CELL_COMPONENT_64M)
                        .unlockedBy("has_64m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_64M))
                        .save(appmekInstalled);
            })
            .lang("ECO - LE6 Chemical Storage Matrix")
            .model(ItemModelUtil.cellModel("chemical", "64m"))
            .register();

    public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_256M = REGISTRATE
            .item(
                    "eco_chemical_storage_cell_256m",
                    p -> new ECOChemicalStorageCellItem(p.stacksTo(1).rarity(Rarity.EPIC), ECOTier.L9))
            .recipe((ctx, prov) -> {
                Consumer<FinishedRecipe> appmekInstalled = appmekInstalled(prov);
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                        .requires(ECO_CHEMICAL_CELL_HOUSING)
                        .requires(NEItems.ECO_CELL_COMPONENT_256M)
                        .unlockedBy("has_256m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_256M))
                        .save(appmekInstalled);
            })
            .lang("ECO - LE9 Chemical Storage Matrix")
            .model(ItemModelUtil.cellModel("chemical", "256m"))
            .register();

    public static void register() {}

    private static Consumer<FinishedRecipe> appmekInstalled(RegistrateRecipeProvider provider) {
        return recipe -> provider.accept(new ConditionalFinishedRecipe(recipe, new ModLoadedCondition("appmek")));
    }
}
