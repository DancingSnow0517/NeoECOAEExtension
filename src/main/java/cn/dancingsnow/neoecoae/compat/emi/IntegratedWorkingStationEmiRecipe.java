package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.compat.crafting.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.compat.crafting.SizedIngredient;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * EMI recipe display for the ECO Integrated Working Station.
 * Layout matches the JEI category (same positions, same 168×75 background).
 */
public class IntegratedWorkingStationEmiRecipe implements EmiRecipe {

    private static final ResourceLocation TEX_BG =
        NeoECOAE.id("textures/gui/jei/integration_working_station.png");
    private static final ResourceLocation TEX_PROGRESS =
        NeoECOAE.id("textures/gui/jei/progress_bar.png");

    private final ResourceLocation id;
    private final IntegratedWorkingStationRecipe recipe;
    private final List<EmiIngredient> itemInputs = new ArrayList<>();
    private EmiIngredient fluidInput = null;
    private final List<EmiStack> outputStacks = new ArrayList<>();

    public IntegratedWorkingStationEmiRecipe(IntegratedWorkingStationRecipe recipe) {
        this.id = recipe.getId();
        this.recipe = recipe;

        // ── Input items ──
        for (SizedIngredient input : recipe.inputItems()) {
            if (input.ingredient().isEmpty()) continue;
            ItemStack[] stacks = input.ingredient().getItems();
            if (stacks == null || stacks.length == 0) continue;

            List<EmiStack> variants = new ArrayList<>();
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;
                ItemStack copy = stack.copy();
                copy.setCount(input.count());
                variants.add(EmiStack.of(copy));
            }
            if (!variants.isEmpty()) {
                itemInputs.add(EmiIngredient.of(variants, input.count()));
            }
        }

        // ── Input fluid ──
        SizedFluidIngredient inputFluid = recipe.inputFluid();
        if (!inputFluid.ingredient().isEmpty()) {
            FluidStack[] rawFluids = inputFluid.getFluids();
            if (rawFluids != null && rawFluids.length > 0) {
                List<EmiStack> fluidVariants = new ArrayList<>();
                for (FluidStack fs : rawFluids) {
                    if (fs == null || fs.isEmpty()) continue;
                    fluidVariants.add(EmiStack.of(fs.getFluid(), inputFluid.amount()));
                }
                if (!fluidVariants.isEmpty()) {
                    this.fluidInput = EmiIngredient.of(fluidVariants, inputFluid.amount());
                }
            }
        }

        // ── Output item ──
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            outputStacks.add(EmiStack.of(itemOutput.copy()));
        }

        // ── Output fluid ──
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            outputStacks.add(EmiStack.of(fluidOutput.getFluid(), fluidOutput.getAmount()));
        }
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        List<EmiIngredient> all = new ArrayList<>(itemInputs);
        if (fluidInput != null) all.add(fluidInput);
        return all;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputStacks;
    }

    @Override
    public int getDisplayWidth() {
        return 168;
    }

    @Override
    public int getDisplayHeight() {
        return 75;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // ── Background texture ──
        widgets.addTexture(TEX_BG, 0, 0, 168, 75, 0, 0, 168, 75, 168, 75);

        // ── Input fluid slot (x=5, y=9, 16×58) ──
        if (fluidInput != null) {
            widgets.addTank(fluidInput, 4, 8, 18, 60, 16000).drawBack(false);
        }

        // ── Input item slots (3×3 grid, x=38, y=12, 18px spacing) ──
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            if (input.ingredient().isEmpty()) continue;
            ItemStack[] stacks = input.ingredient().getItems();
            if (stacks == null || stacks.length == 0) continue;

            int x = 37 + i % 3 * 18;
            int y = 11 + i / 3 * 18;

            List<EmiStack> variants = new ArrayList<>();
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;
                ItemStack copy = stack.copy();
                copy.setCount(input.count());
                variants.add(EmiStack.of(copy));
            }
            if (!variants.isEmpty()) {
                widgets.addSlot(EmiIngredient.of(variants, input.count()), x, y)
                    .drawBack(false);
            }
        }

        // ── Output item slot (x=114, y=31) ──
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            widgets.addSlot(EmiStack.of(itemOutput.copy()), 113, 30)
                .recipeContext(this)
                .drawBack(false);
        }

        // ── Output fluid slot (x=147, y=9) ──
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            widgets.addTank(EmiStack.of(fluidOutput.getFluid(), fluidOutput.getAmount()),
                    146, 8, 18, 60, 16000)
                .drawBack(false);
        }

        // ── Animated progress bar (x=136, y=30) ──
        widgets.addAnimatedTexture(TEX_PROGRESS, 136, 30, 6, 18, 0, 0, 6, 18, 6, 18,
            2000, false, true, false);

        // ── Energy text (x=24, y=66) ──
        widgets.addText(
            Component.translatable("gui.neoecoae.integrated_working_station.energy",
                recipe.energy() / 1000),
            24, 66, 0xFF403E53, false);
    }
}
