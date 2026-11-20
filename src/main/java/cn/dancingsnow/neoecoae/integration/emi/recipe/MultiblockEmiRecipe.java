package cn.dancingsnow.neoecoae.integration.emi.recipe;

import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.integration.common.xei.multiblock.MultiBlockInfoWrapper;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MultiblockEmiRecipe extends ModularUIEMIRecipe {
    private final MultiBlockDefinition definition;
    private final RecipeState recipeState;

    public MultiblockEmiRecipe(MultiBlockDefinition definition) {
        this(definition, new RecipeState());
    }

    private MultiblockEmiRecipe(MultiBlockDefinition definition, RecipeState recipeState) {
        super(recipe -> {
            recipeState.preview = new MultiBlockInfoWrapper(
                definition, recipe.getDisplayWidth(), recipe.getDisplayHeight(),
                recipeState::setExpand, recipeState::setRequiredItems);
            return recipeState.preview.createModularUI();
        });
        this.definition = definition;
        this.recipeState = recipeState;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // Reopening the module's usages must restore its variant even when EMI reuses the UI.
        if (recipeState.preview != null) {
            recipeState.preview.setSmartCooling(definition == NEMultiBlocks.SMART_COOLING_WORKING_STATION);
        }
        super.addWidgets(widgets);
    }

    @Override
    public List<EmiIngredient> getInputs() {
        List<RequiredItem> items = recipeState.requiredItems;
        if (items == null) {
            var context = MultiBlockContext.dummyDelegated(recipeState.expand, new TrackedDummyWorld());
            definition.createLevel(context);
            items = context.getRequiredItems();
        }
        return items.stream()
            .filter(requiredItem -> !requiredItem.isEmpty())
            .map(requiredItem -> (EmiIngredient) EmiStack.of(requiredItem.stackWithCount()))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return NeoECOAEEmiPlugin.MULTIBLOCK;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return definition.getOwner().unwrapKey().map(key -> {
            var id = key.location().withPrefix("/");
            return definition == NEMultiBlocks.SMART_COOLING_WORKING_STATION ? id.withSuffix("/smart_cooling") : id;
        }).orElse(null);
    }

    @Override
    public int getDisplayWidth() {
        return MultiBlockInfoWrapper.DEFAULT_WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return MultiBlockInfoWrapper.DEFAULT_HEIGHT;
    }

    private static final class RecipeState {
        private MultiBlockInfoWrapper preview;
        private int expand = 1;
        private List<RequiredItem> requiredItems;

        private void setExpand(int expand) {
            this.expand = expand;
        }

        private void setRequiredItems(List<RequiredItem> requiredItems) {
            this.requiredItems = requiredItems;
        }
    }
}
