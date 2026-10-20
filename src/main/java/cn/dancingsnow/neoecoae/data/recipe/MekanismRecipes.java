package cn.dancingsnow.neoecoae.data.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import mekanism.api.datagen.recipe.builder.ItemStackToItemStackRecipeBuilder;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

public class MekanismRecipes {
    public static void init(RegistrateRecipeProvider provider) {
        RecipeOutput hasMekanism = provider.withConditions(new ModLoadedCondition("mekanism"));

        // aluminum
        ItemStackToItemStackRecipeBuilder.crushing(
            itemFrom(NETags.Items.ALUMINUM_INGOT),
            NEItems.ALUMINUM_DUST.asStack()

        ).build(hasMekanism, NeoECOAE.id("crushing/aluminum_dust"));
        ItemStackToItemStackRecipeBuilder.enriching(
            itemFrom(NETags.Items.ALUMINUM_ORE),
            NEItems.ALUMINUM_DUST.asStack(2)
        ).build(hasMekanism, NeoECOAE.id("enriching/aluminum_dust_from_ore"));
        ItemStackToItemStackRecipeBuilder.enriching(
            itemFrom(NETags.Items.ALUMINUM_RAW, 3),
            NEItems.ALUMINUM_DUST.asStack(4)
        ).build(hasMekanism, NeoECOAE.id("enriching/aluminum_dust_from_raw"));
        ItemStackToItemStackRecipeBuilder.enriching(
            itemFrom(NETags.Items.RAW_ALUMINUM_STORAGE_BLOCK),
            NEItems.ALUMINUM_DUST.asStack(12)
        ).build(hasMekanism, NeoECOAE.id("enriching/aluminum_dust_from_raw_block"));

        // tungsten
        ItemStackToItemStackRecipeBuilder.crushing(
            itemFrom(NETags.Items.TUNGSTEN_INGOT),
            NEItems.TUNGSTEN_DUST.asStack()

        ).build(hasMekanism, NeoECOAE.id("crushing/tungsten_dust"));
        ItemStackToItemStackRecipeBuilder.enriching(
            itemFrom(NETags.Items.TUNGSTEN_ORE),
            NEItems.TUNGSTEN_DUST.asStack(2)
        ).build(hasMekanism, NeoECOAE.id("enriching/tungsten_dust_from_ore"));
        ItemStackToItemStackRecipeBuilder.enriching(
            itemFrom(NETags.Items.TUNGSTEN_RAW, 3),
            NEItems.TUNGSTEN_DUST.asStack(4)
        ).build(hasMekanism, NeoECOAE.id("enriching/tungsten_dust_from_raw"));
        ItemStackToItemStackRecipeBuilder.enriching(
            itemFrom(NETags.Items.RAW_TUNGSTEN_STORAGE_BLOCK),
            NEItems.TUNGSTEN_DUST.asStack(12)
        ).build(hasMekanism, NeoECOAE.id("enriching/tungsten_dust_from_raw_block"));

        // crushing dust
        ItemStackToItemStackRecipeBuilder.crushing(
            itemFrom(NETags.Items.ALUMINUM_ALLOY_INGOT),
            NEItems.ALUMINUM_ALLOY_DUST.asStack()
        ).build(hasMekanism, NeoECOAE.id("crushing/aluminum_alloy_dust"));

        ItemStackToItemStackRecipeBuilder.crushing(
            itemFrom(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT),
            NEItems.BLACK_TUNGSTEN_ALLOY_DUST.asStack()
        ).build(hasMekanism, NeoECOAE.id("crushing/black_tungsten_alloy_dust"));

        ItemStackToItemStackRecipeBuilder.crushing(
            itemFrom(NETags.Items.ENERGIZED_CRYSTAL),
            NEItems.ENERGIZED_CRYSTAL_DUST.asStack()
        ).build(hasMekanism, NeoECOAE.id("crushing/energized_crystal_dust"));

        ItemStackToItemStackRecipeBuilder.crushing(
            itemFrom(NETags.Items.ENERGIZED_FLUIX_CRYSTAL),
            NEItems.ENERGIZED_FLUIX_CRYSTAL_DUST.asStack()
        ).build(hasMekanism, NeoECOAE.id("crushing/energized_flux_crystal_dust"));
    }

    private static ItemStackIngredient itemFrom(TagKey<Item> tag, int amount) {
        return IngredientCreatorAccess.item().from(tag, amount);
    }

    private static ItemStackIngredient itemFrom(TagKey<Item> tag) {
        return IngredientCreatorAccess.item().from(tag);
    }

    private static ItemStackIngredient itemFrom(ItemLike item, int amount) {
        return IngredientCreatorAccess.item().from(item, amount);
    }

    private static ItemStackIngredient itemFrom(ItemLike item) {
        return IngredientCreatorAccess.item().from(item);
    }
}
