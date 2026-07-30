package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.Level;

/** Restricts an AE2 pattern's input slots to a planner-verified concrete selection. */
public final class ECOSelectedInputPatternDetails implements IPatternDetails {
    private final IPatternDetails delegate;
    private final IInput[] inputs;

    public ECOSelectedInputPatternDetails(IPatternDetails delegate, List<GenericStack> selectedInputs) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        List<GenericStack> selected = List.copyOf(Objects.requireNonNull(selectedInputs, "selectedInputs"));
        IInput[] sourceInputs = delegate.getInputs();
        if (sourceInputs.length != selected.size()) {
            throw new IllegalArgumentException("Selected input count does not match pattern input count");
        }
        this.inputs = new IInput[sourceInputs.length];
        for (int i = 0; i < sourceInputs.length; i++) {
            this.inputs[i] = new SelectedInput(sourceInputs[i], selected.get(i));
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
    public List<GenericStack> getOutputs() {
        return delegate.getOutputs();
    }

    private record SelectedInput(IPatternDetails.IInput delegate, GenericStack selected) implements IInput {
        private SelectedInput {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(selected, "selected");
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { selected };
        }

        @Override
        public long getMultiplier() {
            return delegate.getMultiplier();
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
