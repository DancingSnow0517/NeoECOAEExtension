package cn.dancingsnow.neoecoae.integration.jei.categories.multiblock;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import com.lowdragmc.lowdraglib.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class MultiBlockInfoCategory extends ModularUIRecipeCategory<MultiBlockInfoWrapper> {
    public static final RecipeType<MultiBlockInfoWrapper> RECIPE_TYPE = new RecipeType<>(
        NeoECOAE.id("multiblock"),
        MultiBlockInfoWrapper.class
    );

    private final IDrawable icon;

    public MultiBlockInfoCategory(IJeiHelpers helpers) {
        IGuiHelper guiHelper = helpers.getGuiHelper();
        this.icon = guiHelper.createDrawableItemStack(NEBlocks.COMPUTATION_SYSTEM_L4.asStack());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiBlockInfoWrapper recipe, IFocusGroup focuses) {
        super.setRecipe(builder, recipe, focuses);
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
            .addItemLike(recipe.getDef().getOwner().value());
    }

    @Override
    public RecipeType<MultiBlockInfoWrapper> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("category.neoecoae.multiblock");
    }

    @Override
    public int getWidth() {
        return 160;
    }

    @Override
    public int getHeight() {
        return 160;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(RECIPE_TYPE,
            NEMultiBlocks.DEFINITIONS.stream().map(MultiBlockInfoWrapper::new).toList()
        );
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        NEMultiBlocks.DEFINITIONS.stream().map(it -> it.getOwner().value())
            .forEach(it -> registration.addRecipeCatalyst(it, RECIPE_TYPE));
    }
}
