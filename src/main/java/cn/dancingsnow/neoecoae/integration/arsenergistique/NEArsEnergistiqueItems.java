package cn.dancingsnow.neoecoae.integration.arsenergistique;


import appeng.items.materials.MaterialItem;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.datagen.ItemTagProvider;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import gripe._90.arseng.me.key.SourceKeyType;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEArsEnergistiqueItems {

    public static final ItemEntry<MaterialItem> ECO_SOURCE_CELL_HOUSING = REGISTRATE
        .item("eco_source_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            RecipeOutput arsengInstalled = prov.withConditions(new ModLoadedCondition("arseng"));
            EnchantingApparatusRecipe recipe = new EnchantingApparatusRecipe(
                Ingredient.of(NEItems.ECO_ITEM_CELL_HOUSING),
                new ItemStack(ctx.get()),
                List.of(
                    Ingredient.of(ItemTagProvider.SOURCE_GEM_TAG),
                    Ingredient.of(ItemTagProvider.SOURCE_GEM_TAG),
                    Ingredient.of(ItemTagProvider.SOURCE_GEM_TAG),
                    Ingredient.of(ItemTagProvider.SOURCE_GEM_TAG),
                    Ingredient.of(NEItems.CRYSTAL_MATRIX),
                    Ingredient.of(NEItems.CRYSTAL_MATRIX),
                    Ingredient.of(ItemsRegistry.MANIPULATION_ESSENCE),
                    Ingredient.of(ItemsRegistry.MANIPULATION_ESSENCE)
                ),
                5000,
                false
            );
            arsengInstalled.accept(ctx.getId().withPrefix("enchanting_apparatus/"), recipe, null);
        })
        .lang("ECO Storage Matrix Housing (Source)")
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_16M = REGISTRATE
        .item("eco_source_storage_cell_16m", p -> new ECOStorageCellItem(
            p,
            ECOTier.L4,
            SourceKeyType.TYPE,
            NEArsEnergistiqueCellTypes.SOURCE
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))
        .recipe((ctx, prov) -> {
            RecipeOutput arsengInstalled = prov.withConditions(new ModLoadedCondition("arseng"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(ECO_SOURCE_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_16M)
                .unlockedBy("has_16m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_16M))
                .save(arsengInstalled);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(ECO_SOURCE_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_16M.asStack()));
            arsengInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE4 Storage Matrix (Source)")
        .model(ItemModelUtil.cellModel("source", "16m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_64M = REGISTRATE
        .item("eco_source_storage_cell_64m", p -> new ECOStorageCellItem(
            p,
            ECOTier.L6,
            SourceKeyType.TYPE,
            NEArsEnergistiqueCellTypes.SOURCE
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.RARE))
        .recipe((ctx, prov) -> {
            RecipeOutput arsengInstalled = prov.withConditions(new ModLoadedCondition("arseng"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(ECO_SOURCE_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_64M)
                .unlockedBy("has_64m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_64M))
                .save(arsengInstalled);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(ECO_SOURCE_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_64M.asStack()));
            arsengInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE6 Storage Matrix (Source)")
        .model(ItemModelUtil.cellModel("source", "64m"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_256M = REGISTRATE
        .item("eco_source_storage_cell_256m", p -> new ECOStorageCellItem(
            p,
            ECOTier.L9,
            SourceKeyType.TYPE,
            NEArsEnergistiqueCellTypes.SOURCE
        ))
        .properties(p -> p.stacksTo(1).rarity(Rarity.EPIC))
        .recipe((ctx, prov) -> {
            RecipeOutput arsengInstalled = prov.withConditions(new ModLoadedCondition("arseng"));
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                .requires(ECO_SOURCE_CELL_HOUSING)
                .requires(NEItems.ECO_CELL_COMPONENT_256M)
                .unlockedBy("has_256m_component", RegistrateRecipeProvider.has(NEItems.ECO_CELL_COMPONENT_256M))
                .save(arsengInstalled);
            StorageCellDisassemblyRecipe recipe = new StorageCellDisassemblyRecipe(ctx.get(), List.of(ECO_SOURCE_CELL_HOUSING.asStack(), NEItems.ECO_CELL_COMPONENT_256M.asStack()));
            arsengInstalled.accept(ctx.getId().withPrefix("disassembly/"), recipe, null);
        })
        .lang("ECO - LE9 Storage Matrix (Source)")
        .model(ItemModelUtil.cellModel("source", "256m"))
        .register();

    public static void register() {

    }

}
