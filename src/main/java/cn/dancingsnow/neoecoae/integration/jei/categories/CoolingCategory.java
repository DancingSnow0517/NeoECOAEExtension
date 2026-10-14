package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.integration.xei.TextureConstants;
import cn.dancingsnow.neoecoae.integration.xei.RecipeLayouts.Cooling;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class CoolingCategory implements IRecipeCategory<RecipeHolder<CoolingRecipe>> {
    private final IDrawable icon;
    private final Component title;
    private final IDrawableAnimated progress;
    private final IDrawable progessEmpty;

    public CoolingCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(NEBlocks.CRAFTING_SYSTEM_L9.asStack());
        title = Component.translatable("category.neoecoae.cooling");
        progessEmpty = helper.drawableBuilder(TextureConstants.COOLING_PROGRESS_EMPTY, 0, 0,
                Cooling.PROGRESS_SIZE, Cooling.PROGRESS_SIZE)
            .setTextureSize(Cooling.PROGRESS_SIZE, Cooling.PROGRESS_SIZE)
            .build();
        progress = helper.createAnimatedDrawable(
            helper.drawableBuilder(TextureConstants.COOLING_PROGRESS, 0, 0,
                    Cooling.PROGRESS_SIZE, Cooling.PROGRESS_SIZE)
                .setTextureSize(Cooling.PROGRESS_SIZE, Cooling.PROGRESS_SIZE)
                .build(),
            20,
            IDrawableAnimated.StartDirection.TOP,
            false
        );
    }

    @Override
    public RecipeType<RecipeHolder<CoolingRecipe>> getRecipeType() {
        return NeoECOAEJeiPlugin.COOLING_TYPE;
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
        return Cooling.WIDTH;
    }

    @Override
    public int getHeight() {
        return Cooling.HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CoolingRecipe> recipeHolder, IFocusGroup focuses) {
        CoolingRecipe recipe = recipeHolder.value();
        builder.addInputSlot(Cooling.INPUT_X + 1, Cooling.SLOT_Y + 1)
            .addIngredients(NeoForgeTypes.FLUID_STACK, Arrays.asList(recipe.input().getFluids()))
            .setStandardSlotBackground()
            .setFluidRenderer(1, false, Cooling.SLOT_SIZE - 2, Cooling.SLOT_SIZE - 2);
        builder.addOutputSlot(Cooling.OUTPUT_X + 1, Cooling.SLOT_Y + 1)
            .addFluidStack(recipe.output().getFluid(), recipe.output().getAmount())
            .setStandardSlotBackground()
            .setFluidRenderer(1, false, Cooling.SLOT_SIZE - 2, Cooling.SLOT_SIZE - 2);
    }

    @Override
    public void draw(RecipeHolder<CoolingRecipe> recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        progessEmpty.draw(guiGraphics, Cooling.PROGRESS_X, Cooling.PROGRESS_Y);
        progress.draw(guiGraphics, Cooling.PROGRESS_X, Cooling.PROGRESS_Y);

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(Cooling.TEXT_X, Cooling.TEXT_Y, 0);
        poseStack.scale(0.75f, 0.75f, 0.75f);
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            Component.translatable("category.neoecoae.cooling.coolant", recipe.value().coolant()),
            0,
            0,
            0,
            false
        );
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            Component.translatable("category.neoecoae.cooling.max_overclock", recipe.value().maxOverclock()),
            0,
            Cooling.TEXT_LINE_HEIGHT,
            0,
            false
        );
        poseStack.popPose();
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
            NeoECOAEJeiPlugin.COOLING_TYPE,
            Minecraft.getInstance().getConnection().getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())
        );
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(
            NeoECOAEJeiPlugin.COOLING_TYPE,
            NEBlocks.CRAFTING_SYSTEM_L4,
            NEBlocks.CRAFTING_SYSTEM_L6,
            NEBlocks.CRAFTING_SYSTEM_L9
        );
    }
}
