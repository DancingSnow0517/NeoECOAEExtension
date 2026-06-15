package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.integration.xei.recipe.IntegratedWorkingStationRecipeWrapper;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegratedWorkingStationRecipeWrapperTest {

    @Test
    void declaresRecipeAsOnlyDataSource() {
        RecordComponent[] components = IntegratedWorkingStationRecipeWrapper.class.getRecordComponents();

        assertEquals(1, components.length);
        assertEquals("recipe", components[0].getName());
        assertEquals(IntegratedWorkingStationRecipe.class, components[0].getType());
    }

    @Test
    void usesRecipeCategoryDimensions() {
        assertEquals(168, IntegratedWorkingStationRecipeWrapper.WIDTH);
        assertEquals(75, IntegratedWorkingStationRecipeWrapper.HEIGHT);
    }

    @Test
    void exposesModularUiFactoryForXeiCategories() {
        assertTrue(Arrays.stream(IntegratedWorkingStationRecipeWrapper.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("createModularUI") && method.getParameterCount() == 0));
    }

    @Test
    void recipeFluidSlotsKeepAmountLabels() {
        UIElement root = new IntegratedWorkingStationRecipeWrapper(emptyRecipe()).createRootElement();

        List<FluidSlot> fluidSlots = root.selfAndAllChildren()
            .filter(FluidSlot.class::isInstance)
            .map(FluidSlot.class::cast)
            .toList();

        assertEquals(2, fluidSlots.size());
        assertTrue(fluidSlots.stream().allMatch(slot -> slot.amountLabel.isDisplayed()));
    }

    @Test
    void progressBarDoesNotRenderDefaultLabel() {
        UIElement root = new IntegratedWorkingStationRecipeWrapper(emptyRecipe()).createRootElement();

        ProgressBar progressBar = root.selfAndAllChildren()
            .filter(ProgressBar.class::isInstance)
            .map(ProgressBar.class::cast)
            .findFirst()
            .orElseThrow();

        assertTrue(progressBar.selfAndAllChildren().noneMatch(Label.class::isInstance));
    }

    @Test
    void progressBarAnimatesWhenModularUiTicks() {
        ModularUI ui = new IntegratedWorkingStationRecipeWrapper(emptyRecipe()).createModularUI();
        ui.init(IntegratedWorkingStationRecipeWrapper.WIDTH, IntegratedWorkingStationRecipeWrapper.HEIGHT);

        ProgressBar progressBar = ui.ui.rootElement
            .selectId(IntegratedWorkingStationRecipeWrapper.PROGRESS_BAR_ID, ProgressBar.class)
            .findFirst()
            .orElseThrow();

        float initialValue = progressBar.getValue();
        ui.tick();

        assertNotEquals(initialValue, progressBar.getValue());
    }

    private static IntegratedWorkingStationRecipe emptyRecipe() {
        return new IntegratedWorkingStationRecipe(List.of(), Optional.empty(), null, null, 1000);
    }
}
