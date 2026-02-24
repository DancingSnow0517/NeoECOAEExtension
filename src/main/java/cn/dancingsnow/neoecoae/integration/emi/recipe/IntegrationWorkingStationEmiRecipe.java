package cn.dancingsnow.neoecoae.integration.emi.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;

public class IntegrationWorkingStationEmiRecipe extends BasicEmiRecipe {

    private final IntegratedWorkingStationRecipe recipe;

    public IntegrationWorkingStationEmiRecipe(RecipeHolder<IntegratedWorkingStationRecipe> holder) {
        super(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION, holder.id(), 168, 75);
        this.recipe = holder.value();

        // item inputs
        for (SizedIngredient inputItem : recipe.inputItems()) {
            inputs.add(NeoECOAEEmiPlugin.of(inputItem));
        }

        // fluid input
        SizedFluidIngredient inputFluid = recipe.inputFluid();
        if (!inputFluid.ingredient().isEmpty()) {
            inputs.add(NeoECOAEEmiPlugin.of(inputFluid));
        }

        // item output
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            outputs.add(EmiStack.of(itemOutput));
        }

        // fluid output
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            outputs.add(EmiStack.of(fluidOutput.getFluid(), fluidOutput.getAmount()));
        }
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(NeoECOAE.id("textures/gui/jei/integration_working_station.png"), 0, 0, 168, 75, 0, 0, 168, 75, 168, 75);
        widgets.addAnimatedTexture(NeoECOAE.id("textures/gui/jei/progress_bar.png"), 136, 30, 6, 18, 0, 0, 6, 18, 6, 18, 2000, false, true, false);

        Component text = Component.translatable("gui.neoecoae.integrated_working_station.energy", recipe.energy() / 1000);
        widgets.addText(text, 24, 66, 0x403e53, false);

        // input fluid
        SizedFluidIngredient inputFluid = recipe.inputFluid();
        if (!inputFluid.ingredient().isEmpty()) {
            widgets.addTank(NeoECOAEEmiPlugin.of(inputFluid), 4, 8, 18, 60, 16000).drawBack(false);
        }

        // input items
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            var x = 37 + i % 3 * 18;
            var y = 11 + i / 3 * 18;
            if (!input.ingredient().isEmpty()) {
                widgets.addSlot(NeoECOAEEmiPlugin.of(input), x, y).drawBack(false);
            }
        }

        // output item
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            widgets.addSlot(EmiStack.of(itemOutput), 113, 30).recipeContext(this).drawBack(false);
        }

        // output fluid
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            widgets.addTank(EmiStack.of(fluidOutput.getFluid(), fluidOutput.getAmount()), 146, 8, 18, 20, 16000).recipeContext(this).drawBack(false);
        }
    }
}
