package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.client.NEClientRecipe;
import cn.dancingsnow.neoecoae.integration.jei.NEJeiRecipeType;
import cn.dancingsnow.neoecoae.integration.xei.recipe.IntegratedWorkingStationRecipeWrapper;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import com.mojang.serialization.Codec;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class IntegrationWorkingStationCategory extends ModularUIRecipeCategory<IntegratedWorkingStationRecipeWrapper> {
    private final IDrawable icon;
    private final Component title;


    public IntegrationWorkingStationCategory(IGuiHelper helper) {
        super(IntegratedWorkingStationRecipeWrapper::createModularUI);
        icon = helper.createDrawableItemStack(NEBlocks.INTEGRATED_WORKING_STATION.asStack());
        title = Component.translatable("category.neoecoae.integrated_working_station");
    }

    @Override
    public IRecipeType<IntegratedWorkingStationRecipeWrapper> getRecipeType() {
        return NEJeiRecipeType.INTEGRATED_WORKING_STATION;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 168;
    }

    @Override
    public int getHeight() {
        return 75;
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    @Override
    public @Nullable Identifier getIdentifier(IntegratedWorkingStationRecipeWrapper recipe) {
        return null;
    }

    @Override
    public Codec<IntegratedWorkingStationRecipeWrapper> getCodec(ICodecHelper codecHelper, IRecipeManager recipeManager) {
        return IntegratedWorkingStationRecipe.CODEC.codec()
            .xmap(IntegratedWorkingStationRecipeWrapper::new, IntegratedWorkingStationRecipeWrapper::recipe);
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
            NEJeiRecipeType.INTEGRATED_WORKING_STATION,
            NEClientRecipe.getSyncedRecipes(NERecipeTypes.INTEGRATED_WORKING_STATION.get()).stream()
                .map(recipeHolder -> new IntegratedWorkingStationRecipeWrapper(recipeHolder.value()))
                .toList()
        );
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(NEJeiRecipeType.INTEGRATED_WORKING_STATION, NEBlocks.INTEGRATED_WORKING_STATION);
    }
}
