package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.Level;

/** Restricts an AE2 pattern's input slots to a planner-verified concrete selection. */
public final class ECOSelectedInputPatternDetails implements IPatternDetails {
    private final IPatternDetails delegate;
    private final IInput[] inputs;
    private final int[] originalSlots;
    private final int originalInputCount;

    public ECOSelectedInputPatternDetails(
        IPatternDetails delegate,
        List<ECOAE2InputSelection> selectedInputs
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        List<ECOAE2InputSelection> selected = List.copyOf(
            Objects.requireNonNull(selectedInputs, "selectedInputs")
        );
        IInput[] sourceInputs = delegate.getInputs();
        if (sourceInputs.length != selected.size()) {
            throw new IllegalArgumentException("Selected input count does not match pattern input count");
        }
        this.originalInputCount = sourceInputs.length;

        List<IInput> expandedInputs = new ArrayList<>();
        List<Integer> expandedSlots = new ArrayList<>();
        for (int i = 0; i < sourceInputs.length; i++) {
            ECOAE2InputSelection selection = selected.get(i);
            if (selection.totalMultiplier() != sourceInputs[i].getMultiplier()) {
                throw new IllegalArgumentException("Selected input multiplier does not match pattern input");
            }
            for (ECOAE2InputSelection.Alternative alternative : selection.alternatives()) {
                expandedInputs.add(new SelectedInput(
                    sourceInputs[i], alternative.template(), alternative.multiplier()
                ));
                expandedSlots.add(i);
            }
        }
        this.inputs = expandedInputs.toArray(IInput[]::new);
        this.originalSlots = new int[expandedSlots.size()];
        for (int i = 0; i < expandedSlots.size(); i++) {
            this.originalSlots[i] = expandedSlots.get(i);
        }
    }

    /** Restores the delegate's input-array shape after exact per-alternative extraction. */
    public KeyCounter[] collapseInputHolder(KeyCounter[] expanded) {
        Objects.requireNonNull(expanded, "expanded");
        if (expanded.length != originalSlots.length) {
            throw new IllegalArgumentException("Expanded input holder does not match selected inputs");
        }
        KeyCounter[] collapsed = new KeyCounter[originalInputCount];
        for (int i = 0; i < collapsed.length; i++) {
            collapsed[i] = new KeyCounter();
        }
        for (int i = 0; i < expanded.length; i++) {
            KeyCounter source = Objects.requireNonNull(expanded[i], "expanded input");
            KeyCounter target = collapsed[originalSlots[i]];
            for (var entry : source) {
                target.add(entry.getKey(), entry.getLongValue());
            }
        }
        return collapsed;
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
    public GenericStack[] getOutputs() {
        return delegate.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(collapseInputHolder(inputHolder), inputSink);
    }

    private record SelectedInput(
        IPatternDetails.IInput delegate,
        GenericStack selected,
        long multiplier
    ) implements IInput {
        private SelectedInput {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(selected, "selected");
            if (multiplier <= 0L) {
                throw new IllegalArgumentException("Selected input multiplier must be positive");
            }
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { selected };
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey what, Level level) {
            return selected.what().equals(what) && delegate.isValid(what, level);
        }

        @Override
        public AEKey getRemainingKey(AEKey what) {
            return delegate.getRemainingKey(what);
        }
    }
}
