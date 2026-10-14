package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.gui.theme.Palette;
import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.integration.xei.TextureConstants;
import cn.dancingsnow.neoecoae.integration.xei.RecipeLayouts.Workstation;
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

    public IntegrationWorkingStationCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(NEBlocks.INTEGRATED_WORKING_STATION.asStack());
        title = Component.translatable("category.neoecoae.integrated_working_station");
        background = helper.drawableBuilder(TextureConstants.INTEGRATED_WORKING_STATION, 0, 0,
                Workstation.WIDTH, Workstation.HEIGHT)
            .setTextureSize(Workstation.WIDTH, Workstation.HEIGHT)
            .build();
        progress = helper.drawableBuilder(TextureConstants.PROGRESS_BAR, 0, 0,
                Workstation.PROGRESS_WIDTH, Workstation.PROGRESS_HEIGHT)
            .setTextureSize(Workstation.PROGRESS_WIDTH, Workstation.PROGRESS_HEIGHT)
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
            builder.addInputSlot(Workstation.FLUID_INPUT_X + 1, Workstation.FLUID_Y + 1)
                .addIngredients(NeoForgeTypes.FLUID_STACK, Arrays.asList(inputFluid.getFluids()))
                .setFluidRenderer(Workstation.FLUID_CAPACITY, false,
                    Workstation.FLUID_WIDTH - 2, Workstation.FLUID_HEIGHT - 2);
        }

        // input items
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            var x = Workstation.ITEM_INPUT_X + 1 + i % Workstation.ITEM_COLUMNS * Workstation.SLOT_SIZE;
            var y = Workstation.ITEM_INPUT_Y + 1 + i / Workstation.ITEM_COLUMNS * Workstation.SLOT_SIZE;
            if (!input.ingredient().isEmpty()) {
                builder.addInputSlot(x, y).addIngredients(VanillaTypes.ITEM_STACK, Arrays.asList(input.getItems()));
            }
        }

        // output item
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            builder.addOutputSlot(Workstation.ITEM_OUTPUT_X + 1, Workstation.ITEM_OUTPUT_Y + 1).addItemStack(itemOutput);
        }

        // output fluid
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            builder.addOutputSlot(Workstation.FLUID_OUTPUT_X + 1, Workstation.FLUID_Y + 1)
                .addFluidStack(fluidOutput.getFluid(), fluidOutput.getAmount())
                .setFluidRenderer(Workstation.FLUID_CAPACITY, false,
                    Workstation.FLUID_WIDTH - 2, Workstation.FLUID_HEIGHT - 2);
        }
    }

    @Override
    public void draw(RecipeHolder<IntegratedWorkingStationRecipe> recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics);
        progress.draw(guiGraphics, Workstation.PROGRESS_X, Workstation.PROGRESS_Y);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<IntegratedWorkingStationRecipe> holder, IFocusGroup focuses) {
        IntegratedWorkingStationRecipe recipe = holder.value();
        Component text = Component.translatable("gui.neoecoae.integrated_working_station.energy", recipe.energy() / 1000);
        builder.addText(text, 120, 12).setPosition(Workstation.ENERGY_X, Workstation.ENERGY_Y).setColor(Palette.INK_403E53);
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
