package cn.dancingsnow.neoecoae.integration.appflux;

import appeng.items.materials.MaterialItem;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.appflux.item.ECOFeStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.glodblock.github.appflux.common.AFSingletons;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEAppFluxItems {

    public static final ItemEntry<MaterialItem> ECO_FE_CELL_HOUSING = REGISTRATE
        .item("eco_fe_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            RecipeOutput appFluxInstalled = prov.withConditions(new ModLoadedCondition("appflux"));
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
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_16M = REGISTRATE
        .item("eco_fe_storage_cell_16m", p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L4,
            NEAppFluxCellTypes.FLUX
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))
        .recipe((ctx, prov) -> {
            RecipeOutput appFluxInstalled = prov.withConditions(new ModLoadedCondition("appflux"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(ECO_FE_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_16M)
                .unlockedBy("has_16m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_16M))
                .save(appFluxInstalled);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(ECO_FE_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_16M.asStack()));
            appFluxInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE4 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", "16m"))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_64M = REGISTRATE
        .item("eco_fe_storage_cell_64m", p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L6,
            NEAppFluxCellTypes.FLUX
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.RARE))
        .recipe((ctx, prov) -> {
            RecipeOutput appFluxInstalled = prov.withConditions(new ModLoadedCondition("appflux"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(ECO_FE_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_64M)
                .unlockedBy("has_64m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_64M))
                .save(appFluxInstalled);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(ECO_FE_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_64M.asStack()));
            appFluxInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE6 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", "64m"))
        .register();

    public static final ItemEntry<ECOFeStorageCellItem> ECO_FE_CELL_256M = REGISTRATE
        .item("eco_fe_storage_cell_256m", p -> new ECOFeStorageCellItem(
            p,
            ECOTier.L9,
            NEAppFluxCellTypes.FLUX
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.EPIC))
        .recipe((ctx, prov) -> {
            RecipeOutput appFluxInstalled = prov.withConditions(new ModLoadedCondition("appflux"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(ECO_FE_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_256M)
                .unlockedBy("has_256m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_256M))
                .save(appFluxInstalled);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(ECO_FE_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_256M.asStack()));
            appFluxInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE9 Storage Matrix (FE)")
        .model(ItemModelUtil.cellModel("fe", "256m"))
        .register();

    public static void register() {
    }
}
