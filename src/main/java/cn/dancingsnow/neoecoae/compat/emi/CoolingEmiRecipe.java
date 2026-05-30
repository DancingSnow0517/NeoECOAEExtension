package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * EMI recipe display for ECO Cooling recipes.
 * <p>
 * Cooling recipes provide coolant and overclock capability to the
 * Crafting System Controller. Input fluid is consumed from the
 * input fluid hatch and output fluid is deposited into the
 * output fluid hatch.
 * </p>
 */
public class CoolingEmiRecipe implements EmiRecipe {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoolingEmiRecipe.class);

    private static final int WIDTH = 120;
    private static final int HEIGHT = 58;

    private final CoolingRecipe recipe;
    @Nullable
    private final EmiIngredient input;
    private final List<EmiStack> outputs = new ArrayList<>();

    public CoolingEmiRecipe(CoolingRecipe recipe) {
        this.recipe = recipe;

        // ── Input fluid ──
        List<EmiStack> inputVariants = new ArrayList<>();
        FluidStack[] rawInputs = recipe.input().getFluids();
        if (rawInputs != null) {
            for (FluidStack raw : rawInputs) {
                if (raw == null || raw.isEmpty()) continue;
                inputVariants.add(EmiStack.of(raw.getFluid(), recipe.inputAmount()));
            }
        }
        this.input = inputVariants.isEmpty()
            ? null
            : EmiIngredient.of(inputVariants, recipe.inputAmount());

        // ── Output fluid ──
        if (!recipe.output().isEmpty()) {
            outputs.add(EmiStack.of(recipe.output().getFluid(), recipe.output().getAmount()));
        }
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return NeoECOAEEmiPlugin.COOLING;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return recipe.getId();
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return input != null ? List.of(input) : List.of();
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // ── Input fluid tank ──
        if (input != null) {
            widgets.addTank(input, 10, 10, 18, 18, Math.max(recipe.inputAmount(), 1000));
        }

        // ── Arrow ──
        widgets.addText(Component.literal("→"), 55, 15, 0xFF404040, false);

        // ── Output fluid tank ──
        if (!recipe.output().isEmpty()) {
            widgets.addTank(
                EmiStack.of(recipe.output().getFluid(), recipe.output().getAmount()),
                84, 10, 18, 18, Math.max(recipe.outputAmount(), 1000)
            ).recipeContext(this);
        }

        // ── Coolant info ──
        widgets.addText(
            Component.translatable("category.neoecoae.cooling.coolant", recipe.coolant()),
            5, 40, 0xFF404040, false
        );

        // ── Max overclock (only show if > 0) ──
        if (recipe.maxOverclock() > 0) {
            widgets.addText(
                Component.translatable("category.neoecoae.cooling.max_overclock", recipe.maxOverclock()),
                62, 40, 0xFF404040, false
            );
        }
    }
}
