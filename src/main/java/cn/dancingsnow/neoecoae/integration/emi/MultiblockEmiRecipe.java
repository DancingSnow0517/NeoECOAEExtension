package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class MultiblockEmiRecipe extends ModularUIEMIRecipe {
    private final MultiBlockDefinition definition;

    public MultiblockEmiRecipe(MultiBlockDefinition definition) {
        super(recipe -> new MultiBlockInfoWrapper(definition).createModularUI());
        this.definition = definition;
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
        return 170;
    }

    @Override
    public int getDisplayHeight() {
        return 190;
    }
}
