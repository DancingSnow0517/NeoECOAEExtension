package cn.dancingsnow.neoecoae.compat.jei;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
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
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

    private static final int WIDTH = 120;
    private static final int HEIGHT = 58;

    private final IDrawable icon;
    private final Component title;

    public CoolingJeiCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(NEBlocks.CRAFTING_SYSTEM_L9.asStack());
        this.title = Component.translatable("category.neoecoae.cooling");
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
            builder.addInputSlot(10, 13)
                .addIngredients(ForgeTypes.FLUID_STACK, inputs)
                .setFluidRenderer(Math.max(recipe.inputAmount(), 1000), false, 16, 16);
        } else {
            LOGGER.warn("CoolingRecipe {} has no input fluids", recipe.getId());
        }

        // ── Output fluid ──
        FluidStack output = recipe.output();
        if (!output.isEmpty()) {
            builder.addOutputSlot(84, 13)
                .addIngredient(ForgeTypes.FLUID_STACK, output.copy())
                .setFluidRenderer(Math.max(output.getAmount(), 1000), false, 16, 16);
        }
    }

    @Override
    public void draw(CoolingRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();

        // Arrow between input and output
        g.drawString(mc.font,
            Component.literal("→"),
            55, 17,
            0xFF404040,
            false);

        // Coolant amount
        g.drawString(mc.font,
            Component.translatable("category.neoecoae.cooling.coolant", recipe.coolant()),
            5, 40,
            0xFF404040,
            false);

        // Max overclock (only show if > 0)
        if (recipe.maxOverclock() > 0) {
            g.drawString(mc.font,
                Component.translatable("category.neoecoae.cooling.max_overclock", recipe.maxOverclock()),
                62, 40,
                0xFF404040,
                false);
        }
    }
}
