package cn.dancingsnow.neoecoae.integration.emi.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.theme.Palette;
import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.integration.xei.TextureConstants;
import cn.dancingsnow.neoecoae.integration.xei.RecipeLayouts.Workstation;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import appeng.api.stacks.GenericStack;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
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
    private final List<GenericStack> extraInputs;
    private final Component sourceType;

    public IntegrationWorkingStationEmiRecipe(RecipeHolder<IntegratedWorkingStationRecipe> holder) {
        this(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION, holder.id(), holder.value(), holder.value().energy(), List.of(), null);
    }

    public IntegrationWorkingStationEmiRecipe(LargeWorkstationRecipe recipe) {
        this(NeoECOAEEmiPlugin.LARGE_WORKING_STATION,
            NeoECOAE.id("/large_workstation/" + recipe.id().getNamespace() + "/" + recipe.id().getPath()),
            recipe.display(), recipe.energy(), recipe.extraInputs(), recipe.sourceTypeDescription());
    }

    private IntegrationWorkingStationEmiRecipe(EmiRecipeCategory category, ResourceLocation id,
        IntegratedWorkingStationRecipe recipe, long energy, List<GenericStack> extraInputs, Component sourceType) {
        super(category, id, Workstation.WIDTH, sourceType != null ? Workstation.LARGE_HEIGHT : Workstation.HEIGHT);
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
        widgets.addTexture(TextureConstants.INTEGRATED_WORKING_STATION, 0, 0, Workstation.WIDTH, Workstation.HEIGHT,
            0, 0, Workstation.WIDTH, Workstation.HEIGHT, Workstation.WIDTH, Workstation.HEIGHT);
        widgets.addAnimatedTexture(TextureConstants.PROGRESS_BAR,
            Workstation.PROGRESS_X, Workstation.PROGRESS_Y, Workstation.PROGRESS_WIDTH, Workstation.PROGRESS_HEIGHT,
            0, 0, Workstation.PROGRESS_WIDTH, Workstation.PROGRESS_HEIGHT, Workstation.PROGRESS_WIDTH, Workstation.PROGRESS_HEIGHT,
            2000, false, true, false);

        Component text = category == NeoECOAEEmiPlugin.LARGE_WORKING_STATION
            ? Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_energy", energy)
            : Component.translatable("gui.neoecoae.integrated_working_station.energy", energy / 1000);
        widgets.addText(text, Workstation.ENERGY_X, Workstation.ENERGY_Y, Palette.INK_403E53, false);
        if (!extraInputs.isEmpty()) {
            var extra = extraInputs.getFirst();
            widgets.addText(Component.translatable("gui.neoecoae.large_integrated_working_station.lightning_amount",
                extra.amount()), 0, 79, Palette.INK_403E53, false);
            widgets.addText(extra.what().getDisplayName(), 0, 91, Palette.INK_403E53, false);
        }
        if (sourceType != null) {
            var font = Minecraft.getInstance().font;
            var lines = font.split(sourceType, Workstation.WIDTH);
            for (int i = 0; i < lines.size(); i++) {
                widgets.addText(lines.get(i), 0, 105 + i * font.lineHeight, Palette.INK_403E53, false);
            }
            widgets.addTooltipText(List.of(sourceType), 0, 105, Workstation.WIDTH, 34);
        }

        // input fluid
        SizedFluidIngredient inputFluid = recipe.inputFluid();
        if (!inputFluid.ingredient().isEmpty()) {
            widgets.addTank(NeoECOAEEmiPlugin.of(inputFluid), Workstation.FLUID_INPUT_X, Workstation.FLUID_Y,
                Workstation.FLUID_WIDTH, Workstation.FLUID_HEIGHT, Workstation.FLUID_CAPACITY).drawBack(false);
        }

        // input items
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            var x = Workstation.ITEM_INPUT_X + i % Workstation.ITEM_COLUMNS * Workstation.SLOT_SIZE;
            var y = Workstation.ITEM_INPUT_Y + i / Workstation.ITEM_COLUMNS * Workstation.SLOT_SIZE;
            if (!input.ingredient().isEmpty()) {
                widgets.addSlot(NeoECOAEEmiPlugin.of(input), x, y).drawBack(false);
            }
        }

        // output item
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            widgets.addSlot(EmiStack.of(itemOutput), Workstation.ITEM_OUTPUT_X, Workstation.ITEM_OUTPUT_Y)
                .recipeContext(this).drawBack(false);
        }

        // output fluid
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            widgets.addTank(EmiStack.of(fluidOutput.getFluid(), fluidOutput.getAmount()),
                Workstation.FLUID_OUTPUT_X, Workstation.FLUID_Y, Workstation.FLUID_WIDTH, Workstation.FLUID_HEIGHT,
                Workstation.FLUID_CAPACITY).recipeContext(this).drawBack(false);
        }
    }
}
