package cn.dancingsnow.neoecoae.impl.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Execution-only pattern view that relaxes validation for configured item IDs. */
public final class ECOFuzzyInputPatternDetails implements IPatternDetails {
    private final IPatternDetails delegate;
    private final IInput[] inputs;
    private final Set<ResourceLocation> fuzzyItemIds;

    public ECOFuzzyInputPatternDetails(IPatternDetails delegate, Set<ResourceLocation> fuzzyItemIds) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.fuzzyItemIds = Set.copyOf(Objects.requireNonNull(fuzzyItemIds, "fuzzyItemIds"));
        IInput[] source = delegate.getInputs();
        this.inputs = new IInput[source.length];
        for (int i = 0; i < source.length; i++) {
            this.inputs[i] = new FuzzyInput(source[i], this.fuzzyItemIds);
        }
    }

    @Override
    public AEItemKey getDefinition() {
        return delegate.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return inputs.clone();
    }

    @Override
    public GenericStack getPrimaryOutput() {
        return delegate.getPrimaryOutput();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return delegate.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        return delegate.getTooltip(level, flags);
    }

    private record FuzzyInput(IInput delegate, Set<ResourceLocation> fuzzyItemIds) implements IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return delegate.getPossibleInputs();
        }

        @Override
        public long getMultiplier() {
            return delegate.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey what, Level level) {
            if (ECOFuzzyCraftingInventory.isConfiguredFuzzy(what, fuzzyItemIds)) {
                return possibleInputHasSameId(what);
            }
            return delegate.isValid(what, level);
        }

        @Override
        public AEKey getRemainingKey(AEKey what) {
            return delegate.getRemainingKey(what);
        }

        private boolean possibleInputHasSameId(AEKey what) {
            for (GenericStack possible : delegate.getPossibleInputs()) {
                if (possible != null && possible.what().getId().equals(what.getId())) {
                    return true;
                }
            }
            return false;
        }
    }
}
