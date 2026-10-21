package com.extendedae_plus.api.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.level.Level;

/** Constructor/interface ABI fixture, not a simulation of EAP's queue or crafting engine. */
public final class ScaledMolecularAssemblerPattern implements IMolecularAssemblerSupportedPattern {
    private final IMolecularAssemblerSupportedPattern original;
    public final long multiplier;
    public ScaledMolecularAssemblerPattern(IMolecularAssemblerSupportedPattern original, long multiplier) {
        this.original = original;
        this.multiplier = multiplier;
    }
    public AEItemKey getDefinition() { return original.getDefinition(); }
    public IInput[] getInputs() { return original.getInputs(); }
    public GenericStack[] getOutputs() { return original.getOutputs(); }
    public ItemStack assemble(Container input, Level level) { return original.assemble(input, level); }
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) { return original.getRemainingItems(input); }
    public boolean isItemValid(int slot, AEItemKey key, Level level) { return original.isItemValid(slot, key, level); }
    public boolean isSlotEnabled(int slot) { return original.isSlotEnabled(slot); }
    public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor grid) { original.fillCraftingGrid(table, grid); }
}
