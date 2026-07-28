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
            recipeState::setPreview
        ).createModularUI());
        this.definition = definition;
        this.recipeState = recipeState;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        var previewVariant = definition.getPreviewVariant(recipeState.variant);
        var context = MultiBlockContext.dummyDelegated(
            recipeState.expand,
            new TrackedDummyWorld(),
            previewVariant.blockOverrides()
        );
        definition.createPreviewLevel(context, recipeState.variant);
        return context.getRequiredItems().stream()
            .filter(requiredItem -> !requiredItem.isEmpty())
            .map(requiredItem -> (EmiIngredient) EmiStack.of(requiredItem.stackWithCount()))
            .toList();
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return definition.getPreviewVariants().stream()
            .flatMap(variant -> variant.blockOverrides().values().stream())
            .map(state -> (EmiIngredient) EmiStack.of(state.getBlock()))
            .distinct()
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
        private int variant = 0;

        private void setPreview(int expand, int variant) {
            this.expand = expand;
            this.variant = variant;
        }
    }
}
