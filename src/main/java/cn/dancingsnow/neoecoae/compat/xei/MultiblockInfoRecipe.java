package cn.dancingsnow.neoecoae.compat.xei;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class MultiblockInfoRecipe {
    private final MultiBlockDefinition definition;

    public MultiblockInfoRecipe(MultiBlockDefinition definition) {
        this.definition = definition;
    }

    public MultiBlockDefinition definition() {
        return definition;
    }

    public Block ownerBlock() {
        return definition.getOwner().value();
    }

    public ResourceLocation id() {
        return definition.getOwner().unwrapKey()
                .map(key -> {
                    ResourceLocation ownerId = key.location();
                    return new ResourceLocation(ownerId.getNamespace(), "multiblock/" + ownerId.getPath());
                })
                .orElseGet(() -> NeoECOAE.id("multiblock/unknown"));
    }
}
