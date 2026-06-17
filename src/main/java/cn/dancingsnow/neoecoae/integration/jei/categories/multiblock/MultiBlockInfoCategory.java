package cn.dancingsnow.neoecoae.integration.jei.categories.multiblock;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.integration.jei.NEJeiRecipeType;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

public class MultiBlockInfoCategory extends ModularUIRecipeCategory<MultiBlockInfoWrapper> {


    private final IDrawable icon;

    public MultiBlockInfoCategory(IGuiHelper helper) {
        super(MultiBlockInfoWrapper::createModularUI);
        this.icon = helper.createDrawableItemStack(NEBlocks.COMPUTATION_SYSTEM_L4.asStack());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiBlockInfoWrapper recipe, IFocusGroup focuses) {
        super.setRecipe(builder, recipe, focuses);
        builder.addOutputSlot().add(recipe.getDefinition().getOwner().value()).setSlotName("multiblock_output");
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MultiBlockInfoWrapper recipe, IFocusGroup focuses) {
        super.createRecipeExtras(builder, recipe, focuses);
        builder.getRecipeSlots().findSlotByName("multiblock_output").ifPresent(slot -> {
            builder.addSlottedWidget(new ISlottedRecipeWidget() {
                @Override
                public @NonNull Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
                    return Optional.empty();
                }

                @Override
                public @NonNull ScreenPosition getPosition() {
                    return new ScreenPosition(0, 0);
                }
            }, List.of(slot));
        });
    }

    @Override
    public IRecipeType<MultiBlockInfoWrapper> getRecipeType() {
        return NEJeiRecipeType.MULTIBLOCK;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("category.neoecoae.multiblock");
    }

    @Override
    public int getWidth() {
        return 170;
    }

    @Override
    public int getHeight() {
        return 170;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
            NEJeiRecipeType.MULTIBLOCK,
            NEMultiBlocks.DEFINITIONS.stream().map(MultiBlockInfoWrapper::new).toList()
        );
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        NEMultiBlocks.DEFINITIONS.stream().map(it -> it.getOwner().value())
            .forEach(it -> registration.addCraftingStation(NEJeiRecipeType.MULTIBLOCK, it));
    }
}
