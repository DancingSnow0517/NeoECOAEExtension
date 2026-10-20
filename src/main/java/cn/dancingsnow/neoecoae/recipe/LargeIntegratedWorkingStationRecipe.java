package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Native multiblock recipes stay out of the single-block workstation's nine-input type. */
public record LargeIntegratedWorkingStationRecipe(IntegratedWorkingStationRecipe display)
        implements Recipe<IntegratedWorkingStationRecipe.Input> {
    public static final int MAX_ITEM_INPUTS = 12;

    @Override public boolean matches(IntegratedWorkingStationRecipe.Input input, Level level) { return display.matches(input, level); }
    @Override public ItemStack assemble(IntegratedWorkingStationRecipe.Input input, RegistryAccess registries) { return display.assemble(input, registries); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return display.getResultItem(registries); }
    @Override public ResourceLocation getId() { return display.getId(); }
    @Override public RecipeSerializer<?> getSerializer() { return NERecipeTypes.LARGE_INTEGRATED_WORKING_STATION_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NERecipeTypes.LARGE_INTEGRATED_WORKING_STATION.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<LargeIntegratedWorkingStationRecipe> {
        @Override public LargeIntegratedWorkingStationRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new LargeIntegratedWorkingStationRecipe(IntegratedWorkingStationRecipe.Serializer.fromJson(id, json, MAX_ITEM_INPUTS));
        }
        @Override public LargeIntegratedWorkingStationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new LargeIntegratedWorkingStationRecipe(IntegratedWorkingStationRecipe.Serializer.read(id, buffer, MAX_ITEM_INPUTS));
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, LargeIntegratedWorkingStationRecipe recipe) {
            IntegratedWorkingStationRecipe.Serializer.write(buffer, recipe.display(), MAX_ITEM_INPUTS);
        }
    }
}
