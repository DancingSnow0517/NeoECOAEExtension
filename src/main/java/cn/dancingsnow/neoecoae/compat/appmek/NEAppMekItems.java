package cn.dancingsnow.neoecoae.compat.appmek;

import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.compat.ae2.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.compat.appmek.item.ECOChemicalStorageCellItem;
import com.google.gson.JsonObject;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.common.Tags;

import java.util.List;
import java.util.function.Consumer;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

/**
 * Registers Applied Mekanistics chemical storage cell housing and cells.
 * <p>
 * Item registration only occurs when the {@code appmek} mod is loaded,
 * via the
 * {@link cn.dancingsnow.neoecoae.api.integration.Integration @Integration}
 * annotation on {@link AppMekIntegration}. Recipes are therefore safe
 * to register unconditionally — they reference only NeoECOAE and
 * vanilla items.
 * </p>
 */
public class NEAppMekItems {
    static {
        REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
    }

    // ═══════════════════════════════════════════════════════════════
    // Chemical Cell Housing
    // ═══════════════════════════════════════════════════════════════

    public static final ItemEntry<net.minecraft.world.item.Item> ECO_CHEMICAL_CELL_HOUSING = REGISTRATE
            .item("eco_chemical_cell_housing", net.minecraft.world.item.Item::new)
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
                        .unlockedBy("has_black_tungsten_alloy",
                                RegistrateRecipeProvider.has(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT))
                        .save(appmekInstalled);
            })
            .lang("ECO Storage Matrix Housing (Chemical)")
            .register();

    // ═══════════════════════════════════════════════════════════════
    // Chemical Storage Cells
    // ═══════════════════════════════════════════════════════════════

    public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_16M = REGISTRATE
            .item("eco_chemical_storage_cell_16m", p -> new ECOChemicalStorageCellItem(
                    p.stacksTo(1).rarity(Rarity.UNCOMMON),
                    ECOTier.L4))
            .recipe((ctx, prov) -> {
                Consumer<FinishedRecipe> appmekInstalled = appmekInstalled(prov);
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                        .requires(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING)
                        .requires(NEItems.ECO_CELL_COMPONENT_16M)
                        .unlockedBy("has_16m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_16M))
                        .save(appmekInstalled);
                StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(
                        ctx.get(),
                        List.of(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING.asStack(),
                                NEItems.ECO_CELL_COMPONENT_16M.asStack()));
                appmekInstalled.accept(recipe);
            })
            .lang("ECO - LE4 Storage Matrix (Chemical)")
            .register();

    public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_64M = REGISTRATE
            .item("eco_chemical_storage_cell_64m", p -> new ECOChemicalStorageCellItem(
                    p.stacksTo(1).rarity(Rarity.RARE),
                    ECOTier.L6))
            .recipe((ctx, prov) -> {
                Consumer<FinishedRecipe> appmekInstalled = appmekInstalled(prov);
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                        .requires(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING)
                        .requires(NEItems.ECO_CELL_COMPONENT_64M)
                        .unlockedBy("has_64m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_64M))
                        .save(appmekInstalled);
                StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(
                        ctx.get(),
                        List.of(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING.asStack(),
                                NEItems.ECO_CELL_COMPONENT_64M.asStack()));
                appmekInstalled.accept(recipe);
            })
            .lang("ECO - LE6 Storage Matrix (Chemical)")
            .register();

    public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_256M = REGISTRATE
            .item("eco_chemical_storage_cell_256m", p -> new ECOChemicalStorageCellItem(
                    p.stacksTo(1).rarity(Rarity.EPIC),
                    ECOTier.L9))
            .recipe((ctx, prov) -> {
                Consumer<FinishedRecipe> appmekInstalled = appmekInstalled(prov);
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                        .requires(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING)
                        .requires(NEItems.ECO_CELL_COMPONENT_256M)
                        .unlockedBy("has_256m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_256M))
                        .save(appmekInstalled);
                StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(
                        ctx.get(),
                        List.of(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING.asStack(),
                                NEItems.ECO_CELL_COMPONENT_256M.asStack()));
                appmekInstalled.accept(recipe);
            })
            .lang("ECO - LE9 Storage Matrix (Chemical)")
            .register();

    public static void register() {
    }

    private static Consumer<FinishedRecipe> appmekInstalled(RegistrateRecipeProvider provider) {
        ICondition condition = new ModLoadedCondition("appmek");
        return recipe -> provider.accept(new ConditionalFinishedRecipe(recipe, condition));
    }

    private record ConditionalFinishedRecipe(FinishedRecipe recipe, ICondition condition) implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            recipe.serializeRecipeData(json);
            json.add("forge:conditions", CraftingHelper.serialize(condition));
        }

        @Override
        public JsonObject serializeRecipe() {
            JsonObject json = recipe.serializeRecipe();
            json.add("forge:conditions", CraftingHelper.serialize(condition));
            return json;
        }

        @Override
        public net.minecraft.resources.ResourceLocation getId() {
            return recipe.getId();
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeSerializer<?> getType() {
            return recipe.getType();
        }

        @Override
        public JsonObject serializeAdvancement() {
            JsonObject json = recipe.serializeAdvancement();
            if (json != null) {
                json.add("forge:conditions", CraftingHelper.serialize(condition));
            }
            return json;
        }

        @Override
        public net.minecraft.resources.ResourceLocation getAdvancementId() {
            return recipe.getAdvancementId();
        }
    }
}
