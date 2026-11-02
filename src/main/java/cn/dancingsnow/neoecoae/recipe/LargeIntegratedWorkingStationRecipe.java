package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Native multiblock recipes are kept out of the single-block workstation's recipe type. */
public record LargeIntegratedWorkingStationRecipe(IntegratedWorkingStationRecipe recipe)
    implements Recipe<IntegratedWorkingStationRecipe.Input> {
    public static final int MAX_ITEM_INPUTS = 12;

    public static final MapCodec<LargeIntegratedWorkingStationRecipe> CODEC =
        IntegratedWorkingStationRecipe.createCodec(MAX_ITEM_INPUTS)
            .xmap(LargeIntegratedWorkingStationRecipe::new, LargeIntegratedWorkingStationRecipe::recipe);
    public static final StreamCodec<RegistryFriendlyByteBuf, LargeIntegratedWorkingStationRecipe> STREAM_CODEC =
        IntegratedWorkingStationRecipe.createStreamCodec(MAX_ITEM_INPUTS)
            .map(LargeIntegratedWorkingStationRecipe::new, LargeIntegratedWorkingStationRecipe::recipe);
    public static final RecipeSerializer<LargeIntegratedWorkingStationRecipe> SERIALIZER =
        new RecipeSerializer<>(CODEC, STREAM_CODEC);

    @Override
    public boolean matches(IntegratedWorkingStationRecipe.Input input, Level level) {
        return recipe.matches(input, level);
    }

    @Override
    public ItemStack assemble(IntegratedWorkingStationRecipe.Input input) {
        return recipe.assemble(input);
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<? extends Recipe<IntegratedWorkingStationRecipe.Input>> getSerializer() {
        return NERecipeTypes.LARGE_INTEGRATED_WORKING_STATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<IntegratedWorkingStationRecipe.Input>> getType() {
        return NERecipeTypes.LARGE_INTEGRATED_WORKING_STATION.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }
}
