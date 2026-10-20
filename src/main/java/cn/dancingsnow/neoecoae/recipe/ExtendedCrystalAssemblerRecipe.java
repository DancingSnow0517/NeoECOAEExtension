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

/** ECO-owned counterpart of main's EAE assembler recipe, absent from ExtendedAE 1.20.1. */
public record ExtendedCrystalAssemblerRecipe(IntegratedWorkingStationRecipe display)
        implements Recipe<IntegratedWorkingStationRecipe.Input> {
    public static final ResourceLocation SOURCE_TYPE = new ResourceLocation("expatternprovider", "crystal_assembler");
    @Override public boolean matches(IntegratedWorkingStationRecipe.Input input, Level level) { return display.matches(input, level); }
    @Override public ItemStack assemble(IntegratedWorkingStationRecipe.Input input, RegistryAccess registries) { return display.assemble(input, registries); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return display.getResultItem(registries); }
    @Override public ResourceLocation getId() { return display.getId(); }
    @Override public RecipeSerializer<?> getSerializer() { return NERecipeTypes.EXTENDED_CRYSTAL_ASSEMBLER_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return NERecipeTypes.EXTENDED_CRYSTAL_ASSEMBLER.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<ExtendedCrystalAssemblerRecipe> {
        @Override public ExtendedCrystalAssemblerRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ExtendedCrystalAssemblerRecipe(IntegratedWorkingStationRecipe.Serializer.fromJson(id, json, 12));
        }
        @Override public ExtendedCrystalAssemblerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new ExtendedCrystalAssemblerRecipe(IntegratedWorkingStationRecipe.Serializer.read(id, buffer, 12));
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, ExtendedCrystalAssemblerRecipe recipe) {
            IntegratedWorkingStationRecipe.Serializer.write(buffer, recipe.display(), 12);
        }
    }
}
