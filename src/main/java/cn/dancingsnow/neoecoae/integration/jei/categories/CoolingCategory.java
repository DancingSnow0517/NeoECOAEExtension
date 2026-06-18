package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.client.NEClientRecipe;
import cn.dancingsnow.neoecoae.integration.jei.NEJeiRecipeType;
import cn.dancingsnow.neoecoae.integration.xei.recipe.CoolingRecipeWrapper;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CoolingCategory extends ModularUIRecipeCategory<RecipeHolder<CoolingRecipe>> {
    private final IDrawable icon;
    private final Component title;


    public CoolingCategory(IGuiHelper helper) {
        super(recipe -> new CoolingRecipeWrapper(recipe).createModularUI());
        icon = helper.createDrawableItemStack(NEBlocks.CRAFTING_SYSTEM_L9.asStack());
        title = Component.translatable("category.neoecoae.cooling");
    }

    @Override
    public IRecipeType<RecipeHolder<CoolingRecipe>> getRecipeType() {
        return NEJeiRecipeType.COOLING;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 100;
    }

    @Override
    public int getHeight() {
        return 50;
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
            NEJeiRecipeType.COOLING,
            List.copyOf(NEClientRecipe.getSyncedRecipes(NERecipeTypes.COOLING.get()))
        );
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(
            NEJeiRecipeType.COOLING,
            NEBlocks.CRAFTING_SYSTEM_L4,
            NEBlocks.CRAFTING_SYSTEM_L6,
            NEBlocks.CRAFTING_SYSTEM_L9
        );
    }
}
