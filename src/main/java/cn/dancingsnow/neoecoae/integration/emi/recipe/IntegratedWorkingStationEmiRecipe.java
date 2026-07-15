package cn.dancingsnow.neoecoae.integration.emi.recipe;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.ingredient.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.recipe.ingredient.SizedIngredient;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * EMI recipe display for the ECO Integrated Working Station.
 * Draws only AE2-style slot / tank / frame parts — the outer card
 * background is provided by EMI itself. No full machine panel is drawn.
 */
public class IntegratedWorkingStationEmiRecipe implements EmiRecipe {

    // ── EMI card layout ──
    private static final int WIDTH = 176;
    private static final int HEIGHT = 75;

    // Fluid tanks
    private static final int FLUID_TANK_W = 18;
    private static final int FLUID_TANK_H = 54;

    private static final int INPUT_FLUID_TANK_X = 8;
    private static final int INPUT_FLUID_TANK_Y = 8;

    // 3×3 input item grid — always display 9 slot backgrounds
    private static final int INPUT_GRID_COLS = 3;
    private static final int INPUT_GRID_X = 42;
    private static final int INPUT_GRID_Y = 8;
    private static final int SLOT_SPACING = 18;
    private static final int INPUT_SLOT_COUNT = 9;

    // Output item Inscriber-style frame
    private static final int OUTPUT_FRAME_X = 110;
    private static final int OUTPUT_FRAME_Y = 26;
    private static final int OUTPUT_FRAME_W = 26;
    private static final int OUTPUT_FRAME_H = 26;
    private static final int OUTPUT_SLOT_X = OUTPUT_FRAME_X + 4;
    private static final int OUTPUT_SLOT_Y = OUTPUT_FRAME_Y + 4;

    // Output fluid tank
    private static final int OUTPUT_FLUID_TANK_X = 151;
    private static final int OUTPUT_FLUID_TANK_Y = 8;

    // AE2 inscriber-style progress bar
    private static final int PROGRESS_X = 140;
    private static final int PROGRESS_Y = 30;
    private static final int PROGRESS_W = 6;
    private static final int PROGRESS_H = 18;

    // Energy text
    private static final int ENERGY_TEXT_X = 36;
    private static final int ENERGY_TEXT_Y = 64;
    private static final int ENERGY_TEXT_COLOR = 0xFF404040;

    // ── Instance state ──
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
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {

        // ═══════════════════════════════════════════════════════════
        // Layer 1 — AE2-style parts (NO full panel — EMI provides
        // the outer card background)
        // ═══════════════════════════════════════════════════════════
        widgets.addDrawable(0, 0, WIDTH, HEIGHT, (g, mouseX, mouseY, delta) -> {

            // 1.a Input fluid tank background
            NELDLibAe2StyleRenderer.drawAeFluidTankSimple(
                    g, INPUT_FLUID_TANK_X, INPUT_FLUID_TANK_Y, FLUID_TANK_W, FLUID_TANK_H, FluidStack.EMPTY, 0, 16000);

            // 1.b Input item slot backgrounds — always all 9 slots
            for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
                int col = i % INPUT_GRID_COLS;
                int row = i / INPUT_GRID_COLS;
                NELDLibAe2StyleRenderer.drawAeSlot(
                        g, INPUT_GRID_X + col * SLOT_SPACING, INPUT_GRID_Y + row * SLOT_SPACING);
            }

            // 1.c Output Inscriber-style frame
            NELDLibAe2StyleRenderer.drawAeInscriberOutputFrame(
                    g, OUTPUT_FRAME_X, OUTPUT_FRAME_Y, OUTPUT_FRAME_W, OUTPUT_FRAME_H);

            // 1.d Output fluid tank background
            NELDLibAe2StyleRenderer.drawAeFluidTankSimple(
                    g,
                    OUTPUT_FLUID_TANK_X,
                    OUTPUT_FLUID_TANK_Y,
                    FLUID_TANK_W,
                    FLUID_TANK_H,
                    FluidStack.EMPTY,
                    0,
                    16000);

            // 1.e AE2 inscriber-style progress bar
            int progress = (int) ((System.currentTimeMillis() / 50L) % 100L);
            NELDLibAe2StyleRenderer.drawAeProgressBar(g, PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, progress, 100);
        });

        // ═══════════════════════════════════════════════════════════
        // Layer 2 — EMI ingredient widgets (overlay, no default bg)
        // ═══════════════════════════════════════════════════════════

        // ── Input fluid slot (inside the inset border: +1, 16×(h-2)) ──
        if (fluidInput != null) {
            widgets.addTank(fluidInput, INPUT_FLUID_TANK_X + 1, INPUT_FLUID_TANK_Y + 1, 16, FLUID_TANK_H - 2, 16000)
                    .drawBack(false);
        }

        // ── Input item slots (same coords as background) ──
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            if (input.ingredient().isEmpty()) continue;
            ItemStack[] stacks = input.ingredient().getItems();
            if (stacks == null || stacks.length == 0) continue;

            int x = INPUT_GRID_X + i % INPUT_GRID_COLS * SLOT_SPACING;
            int y = INPUT_GRID_Y + i / INPUT_GRID_COLS * SLOT_SPACING;

            List<EmiStack> variants = new ArrayList<>();
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;
                ItemStack copy = stack.copy();
                copy.setCount(input.count());
                variants.add(EmiStack.of(copy));
            }
            if (!variants.isEmpty()) {
                widgets.addSlot(EmiIngredient.of(variants, input.count()), x, y).drawBack(false);
            }
        }

        // ── Output item slot (centred in Inscriber frame, +4 offset) ──
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            widgets.addSlot(EmiStack.of(itemOutput.copy()), OUTPUT_SLOT_X, OUTPUT_SLOT_Y)
                    .recipeContext(this)
                    .drawBack(false);
        }

        // ── Output fluid slot (inside the inset border: +1, 16×(h-2)) ──
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            widgets.addTank(
                            EmiStack.of(fluidOutput.getFluid(), fluidOutput.getAmount()),
                            OUTPUT_FLUID_TANK_X + 1,
                            OUTPUT_FLUID_TANK_Y + 1,
                            16,
                            FLUID_TANK_H - 2,
                            16000)
                    .drawBack(false);
        }

        // ── Energy text ──
        widgets.addText(
                Component.translatable("gui.neoecoae.integrated_working_station.energy", recipe.energy() / 1000),
                ENERGY_TEXT_X,
                ENERGY_TEXT_Y,
                ENERGY_TEXT_COLOR,
                false);
    }
}
