package cn.dancingsnow.neoecoae.integration.emi.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.integration.recipe.LargeWorkstationRecipeDisplay;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.minecraft.resources.ResourceLocation;
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
    private final long energy;
    private List<SizedFluidIngredient> additionalFluidInputs = List.of();
    private List<GenericStack> additionalOutputs = List.of();
    private LargeWorkstationRecipe largeRecipe;

    public IntegrationWorkingStationEmiRecipe(RecipeHolder<IntegratedWorkingStationRecipe> holder) {
        this(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION, holder.id(), holder.value(), holder.value().energy());
    }

    public IntegrationWorkingStationEmiRecipe(LargeWorkstationRecipe recipe) {
        this(NeoECOAEEmiPlugin.LARGE_WORKING_STATION,
            NeoECOAE.id("/large_workstation/" + recipe.id().getNamespace() + "/" + recipe.id().getPath()),
            recipe.display(), recipe.energy());
        additionalFluidInputs = recipe.additionalFluidInputs();
        additionalOutputs = recipe.additionalOutputs();
        largeRecipe = recipe;
        for (var input : additionalFluidInputs) inputs.add(NeoECOAEEmiPlugin.of(input));
        for (var input : recipe.extraInputs()) inputs.add(of(input));
        for (var output : additionalOutputs) outputs.add(of(output));
        height = LargeWorkstationRecipeDisplay.height(additionalFluidInputs.size(), additionalOutputs.size());
    }

    private IntegrationWorkingStationEmiRecipe(EmiRecipeCategory category, ResourceLocation id,
        IntegratedWorkingStationRecipe recipe, long energy) {
        super(category, id, 168, 75);
        this.recipe = recipe;
        this.energy = energy;

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
        boolean large = category == NeoECOAEEmiPlugin.LARGE_WORKING_STATION;
        String texture = large ? "large_integration_working_station" : "integration_working_station";
        int backgroundHeight = large ? LargeWorkstationRecipeDisplay.BACKGROUND_HEIGHT : 75;
        widgets.addTexture(NeoECOAE.id("textures/guis/" + texture + ".png"), 0, 0, 168, backgroundHeight,
            0, 0, 168, backgroundHeight, 168, backgroundHeight);
        widgets.addAnimatedTexture(NeoECOAE.id("textures/gui/recipe/progress_bar.png"), 136, 30, 6, 18, 0, 0, 6, 18, 6, 18, 2000, false, true, false);

        if (large) {
            widgets.addDrawable(0, 0, 168, backgroundHeight,
                (graphics, mouseX, mouseY, delta) -> LargeWorkstationRecipeDisplay.drawInfo(graphics, largeRecipe));
            widgets.addTooltipText(LargeWorkstationRecipeDisplay.infoTooltip(largeRecipe),
                LargeWorkstationRecipeDisplay.INFO_X, LargeWorkstationRecipeDisplay.INFO_Y,
                LargeWorkstationRecipeDisplay.CARD_X - LargeWorkstationRecipeDisplay.INFO_X - 2,
                LargeWorkstationRecipeDisplay.INFO_HEIGHT);
            var card = LargeWorkstationRecipeDisplay.upgradeCard(largeRecipe);
            if (!card.isEmpty()) {
                var cardSlot = widgets.addSlot(EmiStack.of(card), LargeWorkstationRecipeDisplay.CARD_X - 1,
                    LargeWorkstationRecipeDisplay.CARD_Y - 1).drawBack(false).catalyst(true);
                for (var line : LargeWorkstationRecipeDisplay.moduleTooltip(largeRecipe)) cardSlot.appendTooltip(line);
            }
        } else {
            widgets.addText(Component.translatable("gui.neoecoae.integrated_working_station.energy", energy / 1000),
                24, 66, 0x403e53, false);
        }
        int additionalSlot = 0;
        for (var fluid : additionalFluidInputs) {
            widgets.addSlot(NeoECOAEEmiPlugin.of(fluid), LargeWorkstationRecipeDisplay.slotX(additionalSlot) - 1,
                LargeWorkstationRecipeDisplay.slotY(additionalSlot++) - 1);
        }
        additionalSlot = 0;
        for (var output : additionalOutputs) {
            widgets.addSlot(of(output), LargeWorkstationRecipeDisplay.slotX(additionalSlot) - 1,
                LargeWorkstationRecipeDisplay.slotY(additionalSlot++) - 1
                    + LargeWorkstationRecipeDisplay.rows(additionalFluidInputs.size()) * 18).recipeContext(this);
        }

        // input fluid
        SizedFluidIngredient inputFluid = recipe.inputFluid();
        if (!inputFluid.ingredient().isEmpty()) {
            widgets.addTank(NeoECOAEEmiPlugin.of(inputFluid), 4, 8, 18, 60, 16000).drawBack(false);
        }

        // input items
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            int columns = large ? 4 : 3;
            var x = (large ? 25 : 37) + i % columns * 18;
            var y = 11 + i / columns * 18;
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
            widgets.addTank(EmiStack.of(fluidOutput.getFluid(), fluidOutput.getAmount()), 146, 8, 18, 60, 16000).recipeContext(this).drawBack(false);
        }
    }

    private static EmiStack of(GenericStack stack) {
        if (stack.what() instanceof AEItemKey item) return EmiStack.of(item.toStack()).setAmount(stack.amount());
        if (stack.what() instanceof AEFluidKey fluid) return EmiStack.of(fluid.getFluid(), stack.amount());
        return EmiStack.of(GenericStack.wrapInItemStack(stack.what(), stack.amount()));
    }
}
