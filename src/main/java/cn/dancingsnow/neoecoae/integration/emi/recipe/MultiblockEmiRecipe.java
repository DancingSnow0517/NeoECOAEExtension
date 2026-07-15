package cn.dancingsnow.neoecoae.integration.emi.recipe;

import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
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
        super(recipe -> new MultiBlockInfoWrapper(
            definition,
            recipe.getDisplayWidth(),
            recipe.getDisplayHeight(),
            recipeState::setExpand
        ).createModularUI());
        this.definition = definition;
        this.recipeState = recipeState;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        var context = MultiBlockContext.dummyDelegated(recipeState.expand, new TrackedDummyWorld());
        definition.createLevel(context);
        return context.getRequiredItems().stream()
            .filter(requiredItem -> !requiredItem.isEmpty())
            .map(requiredItem -> (EmiIngredient) EmiStack.of(requiredItem.stackWithCount()))
            .toList();
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return NeoECOAEEmiPlugin.MULTIBLOCK;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return definition.getOwner().unwrapKey().map(key -> key.location().withPrefix("/")).orElse(null);
    }

    @Override
    public int getDisplayWidth() {
        return MultiBlockInfoWrapper.DEFAULT_WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.clamp(
            screenHeight - MultiBlockInfoWrapper.EMI_VERTICAL_RESERVE,
            MultiBlockInfoWrapper.MIN_HEIGHT,
            MultiBlockInfoWrapper.DEFAULT_HEIGHT
        );
    }

    private static final class RecipeState {
        private int expand = 1;

        private void setExpand(int expand) {
            this.expand = expand;
        }
    }
}
