package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.recipe.ingredient.SizedFluidIngredient;
import cn.dancingsnow.neoecoae.recipe.ingredient.SizedIngredient;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JEI recipe category for the ECO Integrated Working Station.
 * Uses 1.20.1 AE2-style rendering via {@link NELDLibAe2StyleRenderer}.
 */
public class IntegratedWorkingStationJeiCategory implements IRecipeCategory<IntegratedWorkingStationRecipe> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegratedWorkingStationJeiCategory.class);

    // ── Layout constants ──
    private static final int WIDTH = 176;
    private static final int HEIGHT = 75;

    // Fluid tanks
    private static final int FLUID_TANK_W = 18;
    private static final int FLUID_TANK_H = 54;
    // JEI fluid slot inside the inset border: +1 offset, 16×(h-2)
    private static final int FLUID_RENDER_W = 16;
    private static final int FLUID_RENDER_H = FLUID_TANK_H - 2; // 52

    private static final int INPUT_FLUID_TANK_X = 8;
    private static final int INPUT_FLUID_TANK_Y = 8;
    private static final int INPUT_FLUID_SLOT_X = INPUT_FLUID_TANK_X + 1;
    private static final int INPUT_FLUID_SLOT_Y = INPUT_FLUID_TANK_Y + 1;

    // 3×3 input item grid — always 9 slot backgrounds
    private static final int INPUT_GRID_COLS = 3;
    private static final int INPUT_SLOT_COUNT = 9;
    private static final int INPUT_GRID_X = 42;
    private static final int INPUT_GRID_Y = 8;
    private static final int SLOT_SPACING = 18;

    // Output item: Inscriber-style frame (26×26), slot +5 inside
    private static final int OUTPUT_FRAME_X = 110;
    private static final int OUTPUT_FRAME_Y = 26;
    private static final int OUTPUT_FRAME_W = 26;
    private static final int OUTPUT_FRAME_H = 26;
    private static final int OUTPUT_SLOT_X = OUTPUT_FRAME_X + 5; // 115
    private static final int OUTPUT_SLOT_Y = OUTPUT_FRAME_Y + 5; // 31

    // Output fluid tank
    private static final int OUTPUT_FLUID_TANK_X = 151;
    private static final int OUTPUT_FLUID_TANK_Y = 8;
    private static final int OUTPUT_FLUID_SLOT_X = OUTPUT_FLUID_TANK_X + 1;
    private static final int OUTPUT_FLUID_SLOT_Y = OUTPUT_FLUID_TANK_Y + 1;

    // AE2 inscriber-style progress bar
    private static final int PROGRESS_X = 140;
    private static final int PROGRESS_Y = 30;
    private static final int PROGRESS_W = 6;
    private static final int PROGRESS_H = 18;

    // Energy text
    private static final int ENERGY_TEXT_X = 36;
    private static final int ENERGY_TEXT_Y = 64;
    private static final int ENERGY_TEXT_COLOR = 0xFF404040;

    private final IDrawable icon;
    private final Component title;

    public IntegratedWorkingStationJeiCategory(IGuiHelper helper) {
        this.title = Component.translatable("category.neoecoae.integrated_working_station");
        this.icon = helper.createDrawableItemStack(NEBlocks.INTEGRATED_WORKING_STATION.asStack());
    }

    @Override
    public RecipeType<IntegratedWorkingStationRecipe> getRecipeType() {
        return NeoECOAEJeiPlugin.IWS_RECIPE_TYPE;
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
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, IntegratedWorkingStationRecipe recipe, IFocusGroup focuses) {

        // ── Input fluid slot ──
        SizedFluidIngredient inputFluid = recipe.inputFluid();
        if (!inputFluid.ingredient().isEmpty()) {
            FluidStack[] rawFluids = inputFluid.getFluids();
            if (rawFluids == null || rawFluids.length == 0) {
                LOGGER.warn(
                        "IWS JEI recipe {} has empty fluid ingredient: {}",
                        recipe.getId(),
                        inputFluid.ingredient().toJson());
            } else {
                List<FluidStack> stacks = new java.util.ArrayList<>();
                for (FluidStack fs : rawFluids) {
                    if (fs == null || fs.isEmpty()) continue;
                    FluidStack copy = fs.copy();
                    copy.setAmount(inputFluid.amount());
                    stacks.add(copy);
                }
                if (!stacks.isEmpty()) {
                    builder.addInputSlot(INPUT_FLUID_SLOT_X, INPUT_FLUID_SLOT_Y)
                            .addIngredients(ForgeTypes.FLUID_STACK, stacks)
                            .setFluidRenderer(16000, false, FLUID_RENDER_W, FLUID_RENDER_H);
                } else {
                    LOGGER.warn(
                            "IWS JEI recipe {} has no valid fluid stacks: {}",
                            recipe.getId(),
                            inputFluid.ingredient().toJson());
                }
            }
        }

        // ── Input item slots (3×3 grid) ──
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            if (input.ingredient().isEmpty()) {
                continue;
            }
            int col = i % INPUT_GRID_COLS;
            int row = i / INPUT_GRID_COLS;
            int x = INPUT_GRID_X + col * SLOT_SPACING;
            int y = INPUT_GRID_Y + row * SLOT_SPACING;

            ItemStack[] rawStacks = input.ingredient().getItems();
            if (rawStacks == null || rawStacks.length == 0) {
                LOGGER.warn(
                        "IWS JEI recipe {} has empty item ingredient at index {}: {}",
                        recipe.getId(),
                        i,
                        input.ingredient().toJson());
                continue;
            }

            List<ItemStack> stacks = new java.util.ArrayList<>();
            for (ItemStack raw : rawStacks) {
                if (raw == null || raw.isEmpty()) continue;
                ItemStack copy = raw.copy();
                copy.setCount(input.count());
                stacks.add(copy);
            }

            if (stacks.isEmpty()) {
                LOGGER.warn(
                        "IWS JEI recipe {} has no valid item stacks at index {}: {}",
                        recipe.getId(),
                        i,
                        input.ingredient().toJson());
                continue;
            }

            builder.addInputSlot(x, y).addIngredients(VanillaTypes.ITEM_STACK, stacks);
        }

        // ── Output item slot ──
        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            builder.addOutputSlot(OUTPUT_SLOT_X, OUTPUT_SLOT_Y).addItemStack(itemOutput.copy());
        }

        // ── Output fluid slot ──
        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            builder.addOutputSlot(OUTPUT_FLUID_SLOT_X, OUTPUT_FLUID_SLOT_Y)
                    .addIngredient(ForgeTypes.FLUID_STACK, fluidOutput.copy())
                    .setFluidRenderer(16000, false, FLUID_RENDER_W, FLUID_RENDER_H);
        }
    }

    @Override
    public void draw(
            IntegratedWorkingStationRecipe recipe,
            IRecipeSlotsView slots,
            GuiGraphics g,
            double mouseX,
            double mouseY) {

        // JEI draws the recipe card background; only draw the machine-specific parts here.
        // 1. Input fluid tank background (empty — JEI renders actual fluid)
        NELDLibAe2StyleRenderer.drawAeFluidTankSimple(
                g, INPUT_FLUID_TANK_X, INPUT_FLUID_TANK_Y, FLUID_TANK_W, FLUID_TANK_H, FluidStack.EMPTY, 0, 16000);

        // 2. Input item slot backgrounds — always all 9 slots
        for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
            int col = i % INPUT_GRID_COLS;
            int row = i / INPUT_GRID_COLS;
            NELDLibAe2StyleRenderer.drawAeSlot(
                    g, INPUT_GRID_X + col * SLOT_SPACING - 1, INPUT_GRID_Y + row * SLOT_SPACING - 1);
        }

        // 3. Output item Inscriber-style frame
        NELDLibAe2StyleRenderer.drawAeInscriberOutputFrame(
                g, OUTPUT_FRAME_X, OUTPUT_FRAME_Y, OUTPUT_FRAME_W, OUTPUT_FRAME_H);

        // 4. Output fluid tank background
        NELDLibAe2StyleRenderer.drawAeFluidTankSimple(
                g, OUTPUT_FLUID_TANK_X, OUTPUT_FLUID_TANK_Y, FLUID_TANK_W, FLUID_TANK_H, FluidStack.EMPTY, 0, 16000);

        // 5. AE2 inscriber-style progress bar
        int progress = (int) ((System.currentTimeMillis() / 50) % 100);
        NELDLibAe2StyleRenderer.drawAeProgressBar(g, PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, progress, 100);

        // 6. Energy text
        Component energyText =
                Component.translatable("gui.neoecoae.integrated_working_station.energy", recipe.energy() / 1000);
        g.drawString(Minecraft.getInstance().font, energyText, ENERGY_TEXT_X, ENERGY_TEXT_Y, ENERGY_TEXT_COLOR, false);
    }
}
