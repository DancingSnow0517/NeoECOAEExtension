package cn.dancingsnow.neoecoae.integration.arsenergistique;


import appeng.items.materials.MaterialItem;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.IntegrationCells.Size;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.datagen.ItemTagProvider;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.tterrag.registrate.util.entry.ItemEntry;
import gripe._90.arseng.me.key.SourceKeyType;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEArsEnergistiqueItems {

    public static final ItemEntry<MaterialItem> ECO_SOURCE_CELL_HOUSING = REGISTRATE
        .item("eco_source_cell_housing", MaterialItem::new)
        .recipe((ctx, prov) -> {
            RecipeOutput arsengInstalled = prov.withConditions(new ModLoadedCondition(ModIds.ARSENG));
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
        .model(ItemModelUtil.importedCellModel("eco_source_cell_housing"))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_16M = IntegrationCells
        .cell("eco_source_storage_cell_16m", Size.L4, p -> new ECOStorageCellItem(
            p,
            ECOTier.L4,
            SourceKeyType.TYPE,
            NEArsEnergistiqueCellTypes.SOURCE
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.ARSENG, ECO_SOURCE_CELL_HOUSING, Size.L4))
        .lang("ECO - LE4 Storage Matrix (Source)")
        .model(ItemModelUtil.cellModel("source", Size.L4.suffix()))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_64M = IntegrationCells
        .cell("eco_source_storage_cell_64m", Size.L6, p -> new ECOStorageCellItem(
            p,
            ECOTier.L6,
            SourceKeyType.TYPE,
            NEArsEnergistiqueCellTypes.SOURCE
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.ARSENG, ECO_SOURCE_CELL_HOUSING, Size.L6))
        .lang("ECO - LE6 Storage Matrix (Source)")
        .model(ItemModelUtil.cellModel("source", Size.L6.suffix()))
        .register();

    public static final ItemEntry<ECOStorageCellItem> ECO_SOURCE_CELL_256M = IntegrationCells
        .cell("eco_source_storage_cell_256m", Size.L9, p -> new ECOStorageCellItem(
            p,
            ECOTier.L9,
            SourceKeyType.TYPE,
            NEArsEnergistiqueCellTypes.SOURCE
        ))
        .recipe(IntegrationCells.housingRecipe(ModIds.ARSENG, ECO_SOURCE_CELL_HOUSING, Size.L9))
        .lang("ECO - LE9 Storage Matrix (Source)")
        .model(ItemModelUtil.cellModel("source", Size.L9.suffix()))
        .register();

    public static void register() {

    }

}
