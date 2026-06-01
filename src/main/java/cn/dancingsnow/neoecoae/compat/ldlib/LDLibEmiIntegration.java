package cn.dancingsnow.neoecoae.compat.ldlib;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.compat.emi.MultiblockEmiRecipe;
import cn.dancingsnow.neoecoae.compat.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;

public final class LDLibEmiIntegration {
    private LDLibEmiIntegration() {
    }

    public static void registerMultiblocks(EmiRegistry registry) {
        registry.addCategory(NeoECOAEEmiPlugin.MULTIBLOCK);
        registry.addWorkstation(NeoECOAEEmiPlugin.MULTIBLOCK, EmiStack.of(NEBlocks.STORAGE_SYSTEM_L4));
        registry.addWorkstation(NeoECOAEEmiPlugin.MULTIBLOCK, EmiStack.of(NEBlocks.STORAGE_SYSTEM_L6));
        registry.addWorkstation(NeoECOAEEmiPlugin.MULTIBLOCK, EmiStack.of(NEBlocks.STORAGE_SYSTEM_L9));
        registry.addWorkstation(NeoECOAEEmiPlugin.MULTIBLOCK, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L4));
        registry.addWorkstation(NeoECOAEEmiPlugin.MULTIBLOCK, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L6));
        registry.addWorkstation(NeoECOAEEmiPlugin.MULTIBLOCK, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L9));
        registry.addWorkstation(NeoECOAEEmiPlugin.MULTIBLOCK, EmiStack.of(NEBlocks.COMPUTATION_SYSTEM_L4));
        registry.addWorkstation(NeoECOAEEmiPlugin.MULTIBLOCK, EmiStack.of(NEBlocks.COMPUTATION_SYSTEM_L6));
        registry.addWorkstation(NeoECOAEEmiPlugin.MULTIBLOCK, EmiStack.of(NEBlocks.COMPUTATION_SYSTEM_L9));

        for (MultiBlockDefinition definition : NEMultiBlocks.DEFINITIONS) {
            registry.addRecipe(new MultiblockEmiRecipe(definition));
        }
    }
}
