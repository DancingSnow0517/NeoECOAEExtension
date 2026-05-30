package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
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

    // ── Textures ──
    private static final ResourceLocation TEX_PROGRESS_EMPTY =
        NeoECOAE.id("textures/gui/jei/cooling_progress_empty.png");
    private static final ResourceLocation TEX_PROGRESS =
        NeoECOAE.id("textures/gui/jei/cooling_progress.png");

    // ── Layout constants ──
    private static final int WIDTH = 150;
    private static final int HEIGHT = 66;

    private static final int INPUT_TANK_X = 12;
    private static final int INPUT_TANK_Y = 12;

    private static final int PROGRESS_X = 60;
    private static final int PROGRESS_Y = 6;

    private static final int OUTPUT_TANK_X = 122;
    private static final int OUTPUT_TANK_Y = 12;

    private static final int TEXT_X = 6;
    private static final int COOLANT_TEXT_Y = 44;
    private static final int OVERCLOCK_TEXT_Y = 55;
    private static final int TEXT_COLOR = 0xFF404040;

    // ── Instance state ──
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
                if (raw == null || raw.isEmpty())
                    continue;
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
            widgets.addTank(input, INPUT_TANK_X, INPUT_TANK_Y, 18, 18,
                Math.max(recipe.inputAmount(), 1000));
        }

        // ── Cooling progress empty background ──
        widgets.addTexture(TEX_PROGRESS_EMPTY,
            PROGRESS_X, PROGRESS_Y, 30, 30,
            0, 0, 30, 30, 30, 30);

        // ── Cooling progress animated overlay ──
        widgets.addAnimatedTexture(TEX_PROGRESS,
            PROGRESS_X, PROGRESS_Y, 30, 30,
            0, 0, 30, 30, 30, 30,
            2000, false, true, false);

        // ── Output fluid tank ──
        if (!recipe.output().isEmpty()) {
            widgets.addTank(
                EmiStack.of(recipe.output().getFluid(), recipe.output().getAmount()),
                OUTPUT_TANK_X, OUTPUT_TANK_Y, 18, 18,
                Math.max(recipe.outputAmount(), 1000)
            ).recipeContext(this);
        }

        // ── Coolant amount (line 1) ──
        widgets.addText(
            Component.translatable("category.neoecoae.cooling.coolant", recipe.coolant()),
            TEXT_X, COOLANT_TEXT_Y, TEXT_COLOR, false);

        // ── Max overclock (line 2) ──
        widgets.addText(
            Component.translatable("category.neoecoae.cooling.max_overclock", recipe.maxOverclock()),
            TEXT_X, OVERCLOCK_TEXT_Y, TEXT_COLOR, false);
    }
}
