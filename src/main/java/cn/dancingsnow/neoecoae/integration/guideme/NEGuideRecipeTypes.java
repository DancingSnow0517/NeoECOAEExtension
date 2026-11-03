package cn.dancingsnow.neoecoae.integration.guideme;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeIntegratedWorkingStationRecipe;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.LytRect;
import guideme.document.LytSize;
import guideme.document.block.AlignItems;
import guideme.document.block.LytBlock;
import guideme.document.block.LytGuiSprite;
import guideme.document.block.LytHBox;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytVBox;
import guideme.document.block.recipes.LytStandardRecipeBox;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.InteractiveElement;
import guideme.document.interaction.ItemTooltip;
import guideme.document.interaction.TextTooltip;
import guideme.layout.LayoutContext;
import guideme.render.GuiAssets;
import guideme.render.RenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Lets GuideME's {@code <RecipeFor>} and {@code <Recipe>} tags render workstation recipes.
 * Discovered by GuideME through the Java service loader, so this class is only linked when GuideME is present.
 */
public class NEGuideRecipeTypes implements RecipeTypeMappingSupplier {
    private static final String PREFIX = "gui.neoecoae.large_integrated_working_station.";
    private static final int CYCLE_MILLIS = 2000;

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(NERecipeTypes.INTEGRATED_WORKING_STATION.get(), holder -> box(holder, holder,
            NEBlocks.INTEGRATED_WORKING_STATION, "category.neoecoae.integrated_working_station", 3));
        mappings.add(NERecipeTypes.LARGE_INTEGRATED_WORKING_STATION.get(), holder -> box(holder,
            holder.display(), NEBlocks.INTEGRATED_WORKING_STATION,
            "category.neoecoae.large_integrated_working_station", 4));
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> LytStandardRecipeBox<T> box(
        T holder, IntegratedWorkingStationRecipe recipe, ItemLike icon, String titleKey, int columns) {
        var body = new LytHBox();
        body.setGap(2);
        body.setAlignItems(AlignItems.CENTER);

        var inputs = new LytHBox();
        inputs.setGap(2);
        inputs.setAlignItems(AlignItems.CENTER);
        if (!recipe.inputItems().isEmpty()) inputs.append(itemGrid(recipe.inputItems(), columns));
        FluidStack[] fluids = recipe.inputFluid().getFluids();
        if (fluids.length > 0 && !fluids[0].isEmpty()) inputs.append(new FluidSlot(fluids));
        body.append(inputs);

        body.append(new LytGuiSprite(GuiAssets.ARROW, new LytSize(24, 17)));

        var outputs = new LytHBox();
        outputs.setGap(2);
        outputs.setAlignItems(AlignItems.CENTER);
        if (recipe.hasItemOutput()) outputs.append(new ItemSlot(new ItemStack[] {recipe.itemOutput().copy()}));
        if (recipe.hasFluidOutput()) outputs.append(new FluidSlot(new FluidStack[] {recipe.fluidOutput().copy()}));
        body.append(outputs);

        var details = new LytParagraph();
        details.appendComponent(Component.translatable(PREFIX + "recipe_energy_value",
            Component.literal(DisplayNumbers.grouped(Integer.toString(recipe.energy())) + " ")
                .append(Component.literal("AE").withStyle(ChatFormatting.DARK_PURPLE))));
        details.appendBreak();
        details.appendComponent(Component.translatable(PREFIX + "time_label").append(": ")
            .append(Component.translatable(PREFIX + "recipe_duration", recipe.processingTicks(),
                BigDecimal.valueOf(recipe.processingTicks()).divide(BigDecimal.valueOf(20))
                    .stripTrailingZeros().toPlainString())));

        return LytStandardRecipeBox.builder()
            .icon(icon)
            .title(Component.translatable(titleKey).getString())
            .customBody(body)
            .addBottom(details)
            .build(holder);
    }

    private static LytBlock itemGrid(List<SizedIngredient> ingredients, int columns) {
        var grid = new LytVBox();
        LytHBox row = null;
        for (int i = 0; i < ingredients.size(); i++) {
            if (i % columns == 0) {
                row = new LytHBox();
                grid.append(row);
            }
            row.append(new ItemSlot(ingredients.get(i).getItems()));
        }
        return grid;
    }

    private static int cycle(int length) {
        return (int) ((System.currentTimeMillis() / CYCLE_MILLIS) % Math.max(1, length));
    }

    /** A standard slot that keeps stack counts, unlike an {@code Ingredient}-backed GuideME slot. */
    private static final class ItemSlot extends LytBlock implements InteractiveElement {
        private static final int SIZE = 18;
        private final ItemStack[] stacks;

        private ItemSlot(ItemStack[] stacks) {
            this.stacks = stacks;
        }

        private ItemStack displayed() {
            return stacks.length == 0 ? ItemStack.EMPTY : stacks[cycle(stacks.length)];
        }

        @Override
        protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
            return new LytRect(x, y, SIZE, SIZE);
        }

        @Override
        protected void onLayoutMoved(int deltaX, int deltaY) {}

        @Override
        public void renderBatch(RenderContext context, MultiBufferSource buffers) {}

        @Override
        public void render(RenderContext context) {
            context.fillIcon(bounds, GuiAssets.SLOT);
            ItemStack stack = displayed();
            if (!stack.isEmpty()) context.renderItem(stack, bounds.x() + 1, bounds.y() + 1, 1, 16, 16);
        }

        @Override
        public Optional<GuideTooltip> getTooltip(float x, float y) {
            ItemStack stack = displayed();
            return stack.isEmpty() ? Optional.empty() : Optional.of(new ItemTooltip(stack));
        }
    }

    /** Fluid slot showing the fluid texture, with name and exact amount in its tooltip. */
    private static final class FluidSlot extends LytBlock implements InteractiveElement {
        private static final int SIZE = 18;
        private final FluidStack[] stacks;

        private FluidSlot(FluidStack[] stacks) {
            this.stacks = stacks;
        }

        private FluidStack displayed() {
            return stacks.length == 0 ? FluidStack.EMPTY : stacks[cycle(stacks.length)];
        }

        @Override
        protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
            return new LytRect(x, y, SIZE, SIZE);
        }

        @Override
        protected void onLayoutMoved(int deltaX, int deltaY) {}

        @Override
        public void renderBatch(RenderContext context, MultiBufferSource buffers) {}

        @Override
        public void render(RenderContext context) {
            context.fillIcon(bounds, GuiAssets.SLOT);
            FluidStack stack = displayed();
            if (!stack.isEmpty()) context.renderFluid(stack, bounds.x() + 1, bounds.y() + 1, 1, 16, 16);
        }

        @Override
        public Optional<GuideTooltip> getTooltip(float x, float y) {
            FluidStack stack = displayed();
            if (stack.isEmpty()) return Optional.empty();
            return Optional.of(new TextTooltip(stack.getDisplayName(),
                Component.literal(DisplayNumbers.grouped(Integer.toString(stack.getAmount())) + " mB")
                    .withStyle(ChatFormatting.GRAY)));
        }
    }
}
