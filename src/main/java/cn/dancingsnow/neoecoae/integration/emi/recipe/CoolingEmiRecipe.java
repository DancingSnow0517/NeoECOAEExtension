package cn.dancingsnow.neoecoae.integration.emi.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

public class CoolingEmiRecipe extends BasicEmiRecipe {
    private final CoolingRecipe recipe;

    public CoolingEmiRecipe(RecipeHolder<CoolingRecipe> holder) {
        super(NeoECOAEEmiPlugin.COOLING, holder.id(), 100, 50);
        this.recipe = holder.value();

        inputs.add(NeoECOAEEmiPlugin.of(recipe.input()));

        FluidStack output = recipe.output();
        if (!output.isEmpty()) {
            outputs.add(EmiStack.of(output.getFluid(), output.getAmount()));
        }
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTank(NeoECOAEEmiPlugin.of(recipe.input()), 9, 10, 18, 18, 1);

        FluidStack output = recipe.output();
        widgets.addTank(EmiStack.of(output.getFluid(), output.getAmount()), 71, 10, 18, 18, 1).recipeContext(this);

        widgets.addTexture(NeoECOAE.id("textures/gui/jei/cooling_progress_empty.png"), 35, 5, 30, 30, 0, 0, 30, 30, 30, 30);
        widgets.addAnimatedTexture(NeoECOAE.id("textures/gui/jei/cooling_progress.png"), 35, 5, 30, 30, 0, 0, 30, 30, 30, 30, 2000, false, true, false);

        widgets.addText(Component.translatable("category.neoecoae.cooling.coolant", recipe.coolant()), 5, 40, 0, false);
    }
}
