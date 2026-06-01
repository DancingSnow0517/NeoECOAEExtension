package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.compat.ldlib.MultiblockPreviewWidget;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib.emi.ModularEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class MultiblockEmiRecipe extends ModularEmiRecipe<MultiblockPreviewWidget> {
    private final MultiBlockDefinition definition;
    private final ResourceLocation id;

    public MultiblockEmiRecipe(MultiBlockDefinition definition) {
        super(() -> new MultiblockPreviewWidget(definition));
        this.definition = definition;
        this.id = definition.getOwner().unwrapKey()
                .map(key -> {
                    ResourceLocation ownerId = key.location();
                    return ResourceLocation.fromNamespaceAndPath(ownerId.getNamespace(), "multiblock/" + ownerId.getPath());
                })
                .orElse(null);
        this.width = MultiblockPreviewWidget.WIDTH;
        this.height = MultiblockPreviewWidget.HEIGHT;
        this.outputs.add(EmiStack.of(definition.getOwner().value()));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return NeoECOAEEmiPlugin.MULTIBLOCK;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return id;
    }
}
