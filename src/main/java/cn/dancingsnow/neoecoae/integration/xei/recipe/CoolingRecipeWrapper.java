package cn.dancingsnow.neoecoae.integration.xei.recipe;

import cn.dancingsnow.neoecoae.gui.NEGuiColors;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.NETextures;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.ScrollDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record CoolingRecipeWrapper(CoolingRecipe recipe) {
    public static final int WIDTH = 100;
    public static final int HEIGHT = 50;
    public static final int TANK_CAPACITY = 1000;

    public CoolingRecipeWrapper(RecipeHolder<CoolingRecipe> recipeHolder) {
        this(recipeHolder.value());
    }

    public CoolingRecipe getRecipe() {
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

        UIElement recipeArea = new UIElement().layout(layout -> layout
            .flexDirection(FlexDirection.ROW)
            .alignItems(AlignItems.CENTER)
            .justifyContent(AlignContent.CENTER));
        recipeArea.addChild(inputFluidSlot());
        recipeArea.addChild(createProgressBarElement());
        recipeArea.addChild(outputFluidSlot());

        root.addChild(recipeArea);

        root.addChild(new TextElement()
            .setText(Component.translatable("category.neoecoae.cooling.coolant", recipe.coolant()))
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL)
                .adaptiveHeight(true)
                .textShadow(false)
                .textColor(NEGuiColors.textColor(0x403e53))));

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
        slot.layout(layout -> layout.width(18).height(18));
        slot.xeiRecipeIngredient(IngredientIO.INPUT, this::inputFluidVariants);
        slot.xeiRecipeSlot(IngredientIO.INPUT, 1, recipe.inputAmount(), this::inputFluidVariants);
        inputFluid.addChild(slot);
        return inputFluid;
    }

    private UIElement outputFluidSlot() {
        UIElement outputFluid = new UIElement().addClass("panel_border");
        FluidStack fluidOutput = recipe.output();
        FluidSlot slot = new FluidSlot();
        slot.setFluid(fluidOutput, false);
        slot.setCapacity(TANK_CAPACITY);
        slot.slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP));
        slot.setAllowClickFilled(false);
        slot.setAllowClickDrained(false);
        slot.layout(layout -> layout.width(18).height(18));
        slot.xeiRecipeIngredient(IngredientIO.OUTPUT, () -> fluidOutput.isEmpty() ? Stream.empty() : Stream.of(fluidOutput));
        slot.xeiRecipeSlot(IngredientIO.OUTPUT, 1, fluidOutput.getAmount(), () -> fluidOutput.isEmpty() ? Stream.empty() : Stream.of(fluidOutput));
        outputFluid.addChild(slot);
        return outputFluid;
    }

    private UIElement createProgressBarElement() {
        UIElement progress = new UIElement().layout(layout -> {
            layout.marginLeft(5);
            layout.marginRight(5);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.justifyContent(AlignContent.CENTER);
        });

        ProgressBar bar = new ProgressBar();
        bar.setRange(0.0f, 20f);
        bar.bindDataSource(ScrollDataSource.of(IntStream.range(0, 20).boxed().map(Integer::floatValue).toList()).frequency(1));
        bar.progressBarStyle(style -> style
            .fillDirection(FillDirection.LEFT_TO_RIGHT)
            .interpolate(false));
        bar.barContainer(element ->
            element.layout(layout -> layout.paddingAll(0)).style(style -> style.background(NETextures.COOLING_PROGRESS_EMPTY))
        );
        bar.bar(element ->
            element.style(style -> style.background(NETextures.COOLING_PROGRESS))
        );
        bar.label.removeSelf();
        bar.layout(layout -> layout.height(30).width(30));
        progress.addChild(bar);
        return progress;
    }

    private Stream<FluidStack> inputFluidVariants() {
        return inputFluidVariants(recipe.input());
    }

    private Stream<FluidStack> inputFluidVariants(SizedFluidIngredient ingredient) {
        return ingredient.ingredient().fluids().stream()
            .map(holder -> new FluidStack(holder, ingredient.amount()));
    }
}
