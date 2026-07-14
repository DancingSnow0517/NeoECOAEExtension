package cn.dancingsnow.neoecoae.integration.jei.categories;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JEI recipe category for ECO Cooling recipes.
 * <p>
 * Cooling recipes are consumed by the Crafting System Controller
 * ({@code ECOCraftingSystemBlockEntity}) to provide coolant and
 * overclock capability. The input fluid is consumed from the
 * input fluid hatch and the output fluid is deposited into the
 * output fluid hatch.
 * </p>
 */
public class CoolingJeiCategory implements IRecipeCategory<CoolingRecipe> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoolingJeiCategory.class);

    // ── Textures ──
    private static final ResourceLocation TEX_PROGRESS_EMPTY =
            NeoECOAE.id("textures/gui/jei/cooling_progress_empty.png");
    private static final ResourceLocation TEX_PROGRESS = NeoECOAE.id("textures/gui/jei/cooling_progress.png");

    // ── Layout constants ──
    private static final int WIDTH = 150;
    private static final int HEIGHT = 66;

    private static final int INPUT_TANK_X = 12;
    private static final int INPUT_TANK_Y = 12;
    private static final int TANK_SIZE = 18;
    private static final int FLUID_RENDER_SIZE = 16;

    private static final int PROGRESS_X = 60;
    private static final int PROGRESS_Y = 6;

    private static final int OUTPUT_TANK_X = 122;
    private static final int OUTPUT_TANK_Y = 12;

    private static final int TEXT_X = 6;
    private static final int COOLANT_TEXT_Y = 44;
    private static final int OVERCLOCK_TEXT_Y = 55;
    private static final int TEXT_COLOR = 0xFF404040;

    private final IDrawable icon;
    private final Component title;
    private final IDrawable progressEmpty;
    private final IDrawableAnimated progress;

    public CoolingJeiCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(NEBlocks.CRAFTING_SYSTEM_L9.asStack());
        this.title = Component.translatable("category.neoecoae.cooling");

        this.progressEmpty = helper.drawableBuilder(TEX_PROGRESS_EMPTY, 0, 0, 30, 30)
                .setTextureSize(30, 30)
                .build();

        this.progress = helper.createAnimatedDrawable(
                helper.drawableBuilder(TEX_PROGRESS, 0, 0, 30, 30)
                        .setTextureSize(30, 30)
                        .build(),
                20,
                IDrawableAnimated.StartDirection.TOP,
                false);
    }

    @Override
    public RecipeType<CoolingRecipe> getRecipeType() {
        return NeoECOAEJeiPlugin.COOLING_RECIPE_TYPE;
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
    public void setRecipe(IRecipeLayoutBuilder builder, CoolingRecipe recipe, IFocusGroup focuses) {
        // ── Input fluid ──
        List<FluidStack> inputs = new ArrayList<>();
        FluidStack[] rawInputs = recipe.input().getFluids();
        if (rawInputs != null) {
            for (FluidStack raw : rawInputs) {
                if (raw == null || raw.isEmpty()) continue;
                FluidStack copy = raw.copy();
                copy.setAmount(recipe.inputAmount());
                inputs.add(copy);
            }
        }

        if (!inputs.isEmpty()) {
            builder.addInputSlot(INPUT_TANK_X + 1, INPUT_TANK_Y + 1)
                    .addIngredients(ForgeTypes.FLUID_STACK, inputs)
                    .setFluidRenderer(
                            Math.max(recipe.inputAmount(), 1000), false, FLUID_RENDER_SIZE, FLUID_RENDER_SIZE);
        } else {
            LOGGER.warn("CoolingRecipe {} has no input fluids", recipe.getId());
        }

        // ── Output fluid ──
        FluidStack output = recipe.output();
        if (!output.isEmpty()) {
            builder.addOutputSlot(OUTPUT_TANK_X + 1, OUTPUT_TANK_Y + 1)
                    .addIngredient(ForgeTypes.FLUID_STACK, output.copy())
                    .setFluidRenderer(Math.max(output.getAmount(), 1000), false, FLUID_RENDER_SIZE, FLUID_RENDER_SIZE);
        }
    }

    @Override
    public void draw(CoolingRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();

        NELDLibAe2StyleRenderer.drawAeFluidTankSimple(
                g, INPUT_TANK_X, INPUT_TANK_Y, TANK_SIZE, TANK_SIZE, FluidStack.EMPTY, 0, 1000);
        NELDLibAe2StyleRenderer.drawAeFluidTankSimple(
                g, OUTPUT_TANK_X, OUTPUT_TANK_Y, TANK_SIZE, TANK_SIZE, FluidStack.EMPTY, 0, 1000);

        // ── Cooling progress animation ──
        progressEmpty.draw(g, PROGRESS_X, PROGRESS_Y);
        progress.draw(g, PROGRESS_X, PROGRESS_Y);

        // ── Coolant amount (line 1) ──
        g.drawString(
                mc.font,
                Component.translatable("category.neoecoae.cooling.coolant", recipe.coolant()),
                TEXT_X,
                COOLANT_TEXT_Y,
                TEXT_COLOR,
                false);

        // ── Max overclock (line 2) ──
        g.drawString(
                mc.font,
                Component.translatable("category.neoecoae.cooling.max_overclock", recipe.maxOverclock()),
                TEXT_X,
                OVERCLOCK_TEXT_Y,
                TEXT_COLOR,
                false);
    }
}
