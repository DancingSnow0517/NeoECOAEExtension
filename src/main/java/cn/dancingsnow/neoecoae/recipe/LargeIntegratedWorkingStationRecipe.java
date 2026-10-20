package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Native multiblock recipes are kept out of the single-block workstation's recipe type. */
public record LargeIntegratedWorkingStationRecipe(IntegratedWorkingStationRecipe display)
    implements Recipe<IntegratedWorkingStationRecipe.Input> {
    public static final int MAX_ITEM_INPUTS = 12;

    @Override
    public boolean matches(IntegratedWorkingStationRecipe.Input input, Level level) {
        return display.matches(input, level);
    }

    @Override
    public ItemStack assemble(IntegratedWorkingStationRecipe.Input input, HolderLookup.Provider registries) {
        return display.assemble(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) { return display.getResultItem(registries); }

    @Override
    public RecipeSerializer<?> getSerializer() { return NERecipeTypes.LARGE_INTEGRATED_WORKING_STATION_SERIALIZER.get(); }

    @Override
    public RecipeType<?> getType() { return NERecipeTypes.LARGE_INTEGRATED_WORKING_STATION.get(); }

    @Override
    public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<LargeIntegratedWorkingStationRecipe> {
        public static final MapCodec<LargeIntegratedWorkingStationRecipe> CODEC =
            IntegratedWorkingStationRecipe.Serializer.createCodec(MAX_ITEM_INPUTS)
                .xmap(LargeIntegratedWorkingStationRecipe::new, LargeIntegratedWorkingStationRecipe::display);
        public static final StreamCodec<FriendlyByteBuf, LargeIntegratedWorkingStationRecipe> STREAM_CODEC =
            IntegratedWorkingStationRecipe.Serializer.createStreamCodec(MAX_ITEM_INPUTS)
                .map(LargeIntegratedWorkingStationRecipe::new, LargeIntegratedWorkingStationRecipe::display);

        @Override
        public MapCodec<LargeIntegratedWorkingStationRecipe> codec() { return CODEC; }

        @Override
        public StreamCodec<FriendlyByteBuf, LargeIntegratedWorkingStationRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
