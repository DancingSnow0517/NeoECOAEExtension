package cn.dancingsnow.neoecoae.integration.xei.recipe;

import cn.dancingsnow.neoecoae.gui.NEGuiColors;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.ScrollDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record IntegratedWorkingStationRecipeWrapper(IntegratedWorkingStationRecipe recipe) {
    public static final int WIDTH = 168;
    public static final int HEIGHT = 75;
    public static final int TANK_CAPACITY = 16000;

    public static final String PROGRESS_BAR_ID = "integrated-working-station-progress";
    public static final String PROGRESS_COLUMN_ID = "integrated-working-station-progress-column";

    public IntegratedWorkingStationRecipeWrapper(RecipeHolder<IntegratedWorkingStationRecipe> recipeHolder) {
        this(recipeHolder.value());
    }

    public IntegratedWorkingStationRecipe getRecipe() {
        return recipe;
    }

    public ModularUI createModularUI() {
        return new ModularUI(UI.of(createRootElement(), List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))));
    }

    public UIElement createRootElement() {
        UIElement root = new UIElement().layout(layout -> layout
            .width(WIDTH)
            .height(HEIGHT)
            .paddingAll(4)
            .gapAll(2)
        ).addClass("panel_bg");

        root.addChild(new TextElement()
            .setText(Component.translatable("gui.neoecoae.integrated_working_station.energy", recipe.energy() / 1000))
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL)
                .adaptiveHeight(true)
                .textShadow(false)
                .textColor(NEGuiColors.textColor(0x403e53))));

        UIElement recipeArea = new UIElement().layout(layout -> layout
            .flexDirection(FlexDirection.ROW)
            .marginBottom(5));
        recipeArea.addChild(inputFluidSlot());
        recipeArea.addChild(inputItemSlots());
        recipeArea.addChild(outputItemSlot());
        recipeArea.addChild(createProgressBarElement());
        recipeArea.addChild(outputFluidSlot());

        root.addChild(recipeArea);
        return root;
    }

    private UIElement inputFluidSlot() {
        UIElement inputFluid = new UIElement().addClass("panel_border");
        FluidSlot slot = new FluidSlot();
        slot.bindDataSource(ScrollDataSource.of(inputFluidVariants().toList()));
        slot.setCapacity(TANK_CAPACITY);
        slot.slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP));
        slot.setAllowClickFilled(false);
        slot.setAllowClickDrained(false);
        slot.layout(style -> style.heightPercent(100));
        slot.xeiRecipeIngredient(IngredientIO.INPUT, this::inputFluidVariants);
        slot.xeiRecipeSlot(IngredientIO.INPUT, 1, recipe.inputFluid().map(SizedFluidIngredient::amount).orElse(0), this::inputFluidVariants);
        inputFluid.addChild(slot);
        return inputFluid;
    }

    private UIElement inputItemSlots() {
        UIElement inputSlots = new UIElement().addClass("panel_border").layout(layout -> layout.marginLeft(10).marginRight(10));
        List<SizedIngredient> inputItems = recipe.inputItems();
        for (int x = 0; x < 3; x++) {
            UIElement row = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW));
            for (int y = 0; y < 3; y++) {
                int index = x + y * 3;
                row.addChild(inputItemSlot(index, inputItems));
            }
            inputSlots.addChild(row);
        }
        return inputSlots;
    }

    private ItemSlot inputItemSlot(int index, List<SizedIngredient> inputItems) {
        if (index >= inputItems.size()) {
            return new ItemSlot().setItem(ItemStack.EMPTY);
        }

        SizedIngredient ingredient = inputItems.get(index);
        ItemSlot slot = new ItemSlot();
        slot.bindDataSource(ScrollDataSource.of(inputItemVariants(ingredient).toList()));
        slot.xeiRecipeIngredient(IngredientIO.INPUT, () -> inputItemVariants(ingredient));
        slot.xeiRecipeSlot(IngredientIO.INPUT, 1, ingredient.count(), () -> inputItemVariants(ingredient));
        return slot;
    }

    private UIElement outputItemSlot() {
        UIElement outputSlots = new UIElement().layout(layout -> {
            layout.marginLeft(5);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.justifyContent(AlignContent.CENTER);
        });
        UIElement outputSlot = new UIElement().addClass("panel_border").layout(layout -> layout.justifyContent(AlignContent.CENTER));
        ItemStack itemOutput = recipe.itemOutput();
        ItemSlot slot = new ItemSlot().setItem(itemOutput);
        slot.xeiRecipeIngredient(IngredientIO.OUTPUT, () -> itemOutput.isEmpty() ? Stream.empty() : Stream.of(itemOutput));
        slot.xeiRecipeSlot(IngredientIO.OUTPUT, 1, itemOutput.getCount(), () -> itemOutput.isEmpty() ? Stream.empty() : Stream.of(itemOutput));
        outputSlot.addChild(slot);
        outputSlots.addChild(outputSlot);
        return outputSlots;
    }

    private UIElement outputFluidSlot() {
        UIElement outputFluid = new UIElement().addClass("panel_border");
        FluidStack fluidOutput = recipe.fluidOutput();
        FluidSlot slot = new FluidSlot();
        slot.setFluid(fluidOutput, false);
        slot.setCapacity(TANK_CAPACITY);
        slot.slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP));
        slot.setAllowClickFilled(false);
        slot.setAllowClickDrained(false);
        slot.layout(style -> style.heightPercent(100));
        slot.xeiRecipeIngredient(IngredientIO.OUTPUT, () -> fluidOutput.isEmpty() ? Stream.empty() : Stream.of(fluidOutput));
        slot.xeiRecipeSlot(IngredientIO.OUTPUT, 1, fluidOutput.getAmount(), () -> fluidOutput.isEmpty() ? Stream.empty() : Stream.of(fluidOutput));
        outputFluid.addChild(slot);
        return outputFluid;
    }

    private UIElement createProgressBarElement() {
        UIElement progress = new UIElement()
            .setId(PROGRESS_COLUMN_ID)
            .layout(layout -> {
                layout.marginLeft(2);
                layout.marginRight(5);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.justifyContent(AlignContent.CENTER);
            });

        ProgressBar bar = new ProgressBar();
        bar.setRange(0.0f, 10f);
        bar.bindDataSource(ScrollDataSource.of(IntStream.rangeClosed(0, 10).boxed().map(Integer::floatValue).toList()).frequency(2));
        bar.progressBarStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP).interpolate(false));
        bar.barContainer(element -> element.layout(layout -> layout.paddingAll(1)));
        bar.label.removeSelf();
        bar.layout(layout -> layout.height(18).width(6));
        progress.addChild(bar);
        return progress;
    }

    private Stream<ItemStack> inputItemVariants(SizedIngredient ingredient) {
        return ingredient.ingredient().items().map(holder -> new ItemStack(holder.value(), ingredient.count()));
    }

    private Stream<FluidStack> inputFluidVariants() {
        return recipe.inputFluid()
            .map(this::inputFluidVariants)
            .orElseGet(Stream::empty);
    }

    private Stream<FluidStack> inputFluidVariants(SizedFluidIngredient ingredient) {
        return ingredient.ingredient().fluids().stream()
            .map(holder -> new FluidStack(holder, ingredient.amount()));
    }
}
