package cn.dancingsnow.neoecoae.integration.jei.categories;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin;
import cn.dancingsnow.neoecoae.integration.jei.TextureConstants;
import cn.dancingsnow.neoecoae.integration.jei.WorkstationItemStackRenderer;
import cn.dancingsnow.neoecoae.integration.recipe.LargeWorkstationRecipeDisplay;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;

/**
 * Separate category makes the formed-multiblock restriction visible during recipe lookup.
 * <p>
 * On 26.1 the single-block category is LDLib-UI based, so this category carries main's native JEI
 * "large" layout itself (four input columns, large background, compact count overlays).
 */
public final class LargeWorkingStationCategory implements IRecipeCategory<LargeWorkstationRecipe> {
    private static final Identifier LARGE_BACKGROUND = NeoECOAE.id("textures/guis/large_integration_working_station.png");

    private final IDrawable icon;
    private final IDrawable background;
    private final IDrawableAnimated progress;

    public LargeWorkingStationCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(NEBlocks.INTEGRATED_WORKING_STATION.asStack());
        int height = LargeWorkstationRecipeDisplay.BACKGROUND_HEIGHT;
        background = helper.drawableBuilder(LARGE_BACKGROUND, 0, 0, 168, height)
            .setTextureSize(168, height)
            .build();
        progress = helper.drawableBuilder(TextureConstants.PROGRESS_BAR, 0, 0, 6, 18)
            .setTextureSize(6, 18)
            .buildAnimated(100, IDrawableAnimated.StartDirection.BOTTOM, false);
    }

    @Override public IRecipeType<LargeWorkstationRecipe> getRecipeType() { return NeoECOAEJeiPlugin.LARGE_WORKING_STATION_TYPE; }
    @Override public Component getTitle() { return Component.translatable("category.neoecoae.large_integrated_working_station"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return background.getWidth(); }
    // JEI's category size is only a fallback; the JEI layout mixins size each recipe separately.
    @Override public int getHeight() { return LargeWorkstationRecipeDisplay.BACKGROUND_HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LargeWorkstationRecipe recipe, IFocusGroup focuses) {
        setDisplayRecipe(builder, recipe.display());
        int slot = 0;
        for (var fluid : recipe.additionalFluidInputs()) {
            builder.addInputSlot(LargeWorkstationRecipeDisplay.slotX(slot), LargeWorkstationRecipeDisplay.slotY(recipe, slot++))
                .setStandardSlotBackground()
                .addIngredients(NeoForgeTypes.FLUID_STACK, fluidStacks(fluid));
        }
        for (var extra : recipe.extraInputs()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                .add(GenericStack.wrapInItemStack(extra.what(), extra.amount()));
        }
        slot = 0;
        for (var output : recipe.additionalOutputs()) {
            var outputSlot = builder.addOutputSlot(LargeWorkstationRecipeDisplay.slotX(slot),
                LargeWorkstationRecipeDisplay.slotY(recipe, slot++) + LargeWorkstationRecipeDisplay.rows(recipe.additionalFluidInputs().size()) * 18)
                .setStandardSlotBackground();
            if (output.what() instanceof AEItemKey item) outputSlot.add(item.toStack(Math.toIntExact(output.amount())));
            else if (output.what() instanceof AEFluidKey fluid) outputSlot.addIngredients(NeoForgeTypes.FLUID_STACK,
                List.of(fluid.toStack(Math.toIntExact(output.amount()))));
            else outputSlot.add(GenericStack.wrapInItemStack(output.what(), output.amount()));
            WorkstationItemStackRenderer.configure(outputSlot);
        }
        var cards = LargeWorkstationRecipeDisplay.cards(recipe);
        var infoLayout = LargeWorkstationRecipeDisplay.layout(recipe);
        for (int i = 0; i < cards.size(); i++) {
            var card = cards.get(i);
            builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, infoLayout.cardX(i), infoLayout.cardY(i))
                .add(card.stack())
                .addRichTooltipCallback((view, tooltip) -> tooltip.addAll(card.tooltip()));
        }
    }

    /** main's {@code IntegrationWorkingStationCategory} slot layout in large mode. */
    private static void setDisplayRecipe(IRecipeLayoutBuilder builder, IntegratedWorkingStationRecipe recipe) {
        recipe.inputFluid().ifPresent(inputFluid -> builder.addInputSlot(5, 9)
            .addIngredients(NeoForgeTypes.FLUID_STACK, fluidStacks(inputFluid))
            .setFluidRenderer(16000, false, 16, 58));

        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int i = 0; i < inputItems.size(); i++) {
            SizedIngredient input = inputItems.get(i);
            int columns = 4;
            var x = 26 + i % columns * 18;
            var y = 12 + i / columns * 18;
            List<ItemStack> stacks = input.ingredient().items()
                .map(item -> new ItemStack(item, input.count()))
                .toList();
            if (!stacks.isEmpty()) {
                var slot = builder.addInputSlot(x, y).addIngredients(VanillaTypes.ITEM_STACK, stacks);
                WorkstationItemStackRenderer.configure(slot);
            }
        }

        ItemStack itemOutput = recipe.itemOutput();
        if (!itemOutput.isEmpty()) {
            var slot = builder.addOutputSlot(114, 31).add(itemOutput);
            WorkstationItemStackRenderer.configure(slot);
        }

        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            builder.addOutputSlot(147, 9)
                .addIngredients(NeoForgeTypes.FLUID_STACK, List.of(fluidOutput.copy()))
                .setFluidRenderer(16000, false, 16, 58);
        }
    }

    private static List<FluidStack> fluidStacks(SizedFluidIngredient ingredient) {
        return ingredient.ingredient().fluids().stream()
            .map(fluid -> new FluidStack(fluid, ingredient.amount()))
            .toList();
    }

    @Override
    public void draw(LargeWorkstationRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double x, double y) {
        background.draw(graphics);
        progress.draw(graphics, 136, 30);
        LargeWorkstationRecipeDisplay.drawInfo(graphics, recipe);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, LargeWorkstationRecipe recipe, IRecipeSlotsView slots, double x, double y) {
        tooltip.addAll(LargeWorkstationRecipeDisplay.tooltip(recipe, x, y));
    }
}
