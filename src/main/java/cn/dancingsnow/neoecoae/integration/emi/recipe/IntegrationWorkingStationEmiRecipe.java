package cn.dancingsnow.neoecoae.integration.emi.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;

import java.util.List;

public class IntegrationWorkingStationEmiRecipe extends BasicEmiRecipe {

    private final IntegratedWorkingStationRecipe recipe;
    private final long energy;
    private final List<GenericStack> extraInputs;
    private final Component sourceType;
    private List<SizedFluidIngredient> additionalFluidInputs = List.of();
    private List<GenericStack> additionalOutputs = List.of();

    public IntegrationWorkingStationEmiRecipe(IntegratedWorkingStationRecipe holder) {
        this(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION, holder.getId(), holder, holder.energy(), List.of(), null);
    }

    public IntegrationWorkingStationEmiRecipe(LargeWorkstationRecipe recipe) {
        this(NeoECOAEEmiPlugin.LARGE_WORKING_STATION,
            NeoECOAE.id("/large_workstation/" + recipe.id().getNamespace() + "/" + recipe.id().getPath()),
            recipe.display(), recipe.energy(), recipe.extraInputs(), recipe.sourceTypeDescription());
        additionalFluidInputs = recipe.additionalFluidInputs();
        additionalOutputs = recipe.additionalOutputs();
        for (var input : additionalFluidInputs) inputs.add(NeoECOAEEmiPlugin.of(input));
        for (var input : extraInputs) inputs.add(of(input));
        for (var output : additionalOutputs) outputs.add(of(output));
        height = 175;
    }

    private IntegrationWorkingStationEmiRecipe(EmiRecipeCategory category, ResourceLocation id,
        IntegratedWorkingStationRecipe recipe, long energy, List<GenericStack> extraInputs, Component sourceType) {
        super(category, id, 168, sourceType != null ? 139 : 75);
        this.recipe = recipe;
        this.energy = energy;
        this.extraInputs = extraInputs;
        this.sourceType = sourceType;

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
        widgets.addTexture(NeoECOAE.id("textures/guis/" + texture + ".png"), 0, 0, 168, 75, 0, 0, 168, 75, 168, 75);
        widgets.addAnimatedTexture(NeoECOAE.id("textures/gui/recipe/progress_bar.png"), 136, 30, 6, 18, 0, 0, 6, 18, 6, 18, 2000, false, true, false);

        Component text = category == NeoECOAEEmiPlugin.LARGE_WORKING_STATION
            ? Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_energy", energy)
            : Component.translatable("gui.neoecoae.integrated_working_station.energy", energy / 1000);
        widgets.addText(text, 24, 66, 0x403e53, false);
        int additionalSlot = 0;
        for (var fluid : additionalFluidInputs) {
            widgets.addSlot(NeoECOAEEmiPlugin.of(fluid), 4 + additionalSlot++ * 18, 76);
        }
        for (var extra : extraInputs) {
            widgets.addSlot(of(extra), 4 + additionalSlot++ * 18, 76);
        }
        additionalSlot = 0;
        for (var output : additionalOutputs) {
            widgets.addSlot(of(output), 4 + additionalSlot++ * 18, 96).recipeContext(this);
        }
        if (sourceType != null) {
            var font = Minecraft.getInstance().font;
            var lines = font.split(sourceType, 168);
            for (int i = 0; i < lines.size(); i++) {
                widgets.addText(lines.get(i), 0, 119 + i * font.lineHeight, 0x403e53, false);
            }
            widgets.addTooltipText(List.of(sourceType), 0, 119, 168, 34);
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
