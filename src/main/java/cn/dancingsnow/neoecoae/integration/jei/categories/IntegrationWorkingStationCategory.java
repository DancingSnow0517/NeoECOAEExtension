package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.integration.jei.TextureConstants;
import cn.dancingsnow.neoecoae.integration.jei.WorkstationItemStackRenderer;
import cn.dancingsnow.neoecoae.integration.recipe.LargeWorkstationRecipeDisplay;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.Arrays;
import java.util.List;

public class IntegrationWorkingStationCategory implements IRecipeCategory<RecipeHolder<IntegratedWorkingStationRecipe>> {
    private final IDrawable icon;
    private final Component title;
    private final IDrawable background;
    private final IDrawableAnimated progress;
    private final boolean large;

    public IntegrationWorkingStationCategory(IGuiHelper helper) {
        this(helper, false);
    }

    public IntegrationWorkingStationCategory(IGuiHelper helper, boolean large) {
        this.large = large;
        icon = helper.createDrawableItemStack(NEBlocks.INTEGRATED_WORKING_STATION.asStack());
        title = Component.translatable("category.neoecoae.integrated_working_station");
        var texture = large ? cn.dancingsnow.neoecoae.NeoECOAE.id("textures/guis/large_integration_working_station.png")
            : TextureConstants.INTEGRATED_WORKING_STATION;
        int height = large ? LargeWorkstationRecipeDisplay.BACKGROUND_HEIGHT : 75;
        background = helper.drawableBuilder(texture, 0, 0, 168, height)
            .setTextureSize(168, height)
            .build();
        progress = helper.drawableBuilder(TextureConstants.PROGRESS_BAR, 0, 0, 6, 18)
            .setTextureSize(6, 18)
            .buildAnimated(100, IDrawableAnimated.StartDirection.BOTTOM, false);
    }

    @Override
    public RecipeType<RecipeHolder<IntegratedWorkingStationRecipe>> getRecipeType() {
        return NeoECOAEJeiPlugin.INTEGRATED_WORKING_STATION_TYPE;
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
        return background.getWidth();
    }

    @Override
    public int getHeight() {
        return background.getHeight();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<IntegratedWorkingStationRecipe> holder, IFocusGroup focuses) {
        IntegratedWorkingStationRecipe recipe = holder.value();

        // input fluid
        SizedFluidIngredient inputFluid = recipe.inputFluid();
        if (!inputFluid.ingredient().isEmpty()) {
            builder.addInputSlot(5, 9)
                .addIngredients(NeoForgeTypes.FLUID_STACK, Arrays.asList(inputFluid.getFluids()))
                .setFluidRenderer(16000, false, 16, 58);
        }

        // input items
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            int columns = large ? 4 : 3;
            var x = (large ? 26 : 38) + i % columns * 18;
            var y = 12 + i / columns * 18;
            if (!input.ingredient().isEmpty()) {
                var slot = builder.addInputSlot(x, y).addIngredients(VanillaTypes.ITEM_STACK, Arrays.asList(input.getItems()));
                if (large) WorkstationItemStackRenderer.configure(slot);
            }
        }

        // output item
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            var slot = builder.addOutputSlot(114, 31).addItemStack(itemOutput);
            if (large) WorkstationItemStackRenderer.configure(slot);
        }

        // output fluid
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            builder.addOutputSlot(147, 9)
                .addFluidStack(fluidOutput.getFluid(), fluidOutput.getAmount())
                .setFluidRenderer(16000, false, 16, 58);
        }
    }

    @Override
    public void draw(RecipeHolder<IntegratedWorkingStationRecipe> recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics);
        progress.draw(guiGraphics, 136, 30);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<IntegratedWorkingStationRecipe> holder, IFocusGroup focuses) {
        IntegratedWorkingStationRecipe recipe = holder.value();
        Component text = Component.translatable("gui.neoecoae.integrated_working_station.energy", recipe.energy() / 1000);
        builder.addText(text, 120, 12).setPosition(24, 66).setColor(0x403e53);
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
            NeoECOAEJeiPlugin.INTEGRATED_WORKING_STATION_TYPE,
            Minecraft.getInstance().getConnection().getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())
        );
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(
            NeoECOAEJeiPlugin.INTEGRATED_WORKING_STATION_TYPE,
            NEBlocks.INTEGRATED_WORKING_STATION
        );
    }
}
