package com.extendedae_plus.api.crafting;

import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
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
    public List<GenericStack> getOutputs() { return original.getOutputs(); }
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flag) { return original.getTooltip(level, flag); }
    public ItemStack assemble(CraftingInput input, Level level) { return original.assemble(input, level); }
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) { return original.getRemainingItems(input); }
    public boolean isItemValid(int slot, AEItemKey key, Level level) { return original.isItemValid(slot, key, level); }
    public boolean isSlotEnabled(int slot) { return original.isSlotEnabled(slot); }
    public void fillCraftingGrid(KeyCounter[] table, CraftingGridAccessor grid) { original.fillCraftingGrid(table, grid); }
}
