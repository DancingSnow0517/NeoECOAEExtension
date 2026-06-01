package cn.dancingsnow.neoecoae.compat.jei;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.compat.ldlib.MultiblockPreviewWidget;
import cn.dancingsnow.neoecoae.compat.xei.MultiblockInfoRecipe;
import com.lowdragmc.lowdraglib.jei.ModularUIRecipeCategory;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class MultiblockJeiCategory extends ModularUIRecipeCategory<MultiblockInfoRecipe> {
    private final IDrawable icon;

    public MultiblockJeiCategory(IGuiHelper helper) {
        super(recipe -> new ModularWrapper<>(new MultiblockPreviewWidget(recipe.definition())));
        this.icon = helper.createDrawableItemStack(NEMultiBlocks.STORAGE_SYSTEM_L4.getOwner().value().asItem().getDefaultInstance());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiblockInfoRecipe recipe, IFocusGroup focuses) {
        super.setRecipe(builder, recipe, focuses);
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                .addItemLike(recipe.ownerBlock());
    }

    @Override
    public RecipeType<MultiblockInfoRecipe> getRecipeType() {
        return NeoECOAEJeiPlugin.MULTIBLOCK_RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("category.neoecoae.multiblock");
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return MultiblockPreviewWidget.WIDTH;
    }

    @Override
    public int getHeight() {
        return MultiblockPreviewWidget.HEIGHT;
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
                NeoECOAEJeiPlugin.MULTIBLOCK_RECIPE_TYPE,
                NEMultiBlocks.DEFINITIONS.stream().map(MultiblockInfoRecipe::new).toList());
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        NEMultiBlocks.DEFINITIONS.stream()
                .map(definition -> definition.getOwner().value())
                .forEach(block -> registration.addRecipeCatalyst(block.asItem().getDefaultInstance(),
                        NeoECOAEJeiPlugin.MULTIBLOCK_RECIPE_TYPE));
    }
}
