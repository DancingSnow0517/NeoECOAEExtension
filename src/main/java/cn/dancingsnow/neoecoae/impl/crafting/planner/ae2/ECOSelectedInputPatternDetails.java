package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.TooltipFlag;
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

    /**
     * Resolves planner templates to the concrete component variants currently held by the CPU.
     * The returned view is strict: extraction and provider validation see only those concrete
     * keys, while the planner itself remains component-insensitive.
     */
    public static ECOSelectedInputPatternDetails resolve(
        IPatternDetails delegate,
        List<ECOAE2InputSelection> selectedInputs,
        ListCraftingInventory inventory,
        Set<ResourceLocation> fuzzyItemIds
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(fuzzyItemIds, "fuzzyItemIds");
        var available = new LinkedHashMap<AEKey, Long>();
        for (var entry : inventory.list) {
            if (entry.getLongValue() > 0L) {
                available.put(entry.getKey(), entry.getLongValue());
            }
        }
        List<ECOAE2InputSelection> resolved = new ArrayList<>(selectedInputs.size());
        for (ECOAE2InputSelection selection : selectedInputs) {
            List<ECOAE2InputSelection.Alternative> alternatives = new ArrayList<>();
            for (ECOAE2InputSelection.Alternative alternative : selection.alternatives()) {
                GenericStack template = alternative.template();
                long remainingUnits = alternative.multiplier();
                boolean fuzzy = template.what().getType() == AEKeyType.items()
                    && fuzzyItemIds.contains(template.what().getId());
                for (var entry : available.entrySet()) {
                    if (remainingUnits <= 0L) {
                        break;
                    }
                    AEKey candidate = entry.getKey();
                    if (fuzzy
                        ? candidate.getType() != AEKeyType.items()
                            || !fuzzyItemIds.contains(candidate.getId())
                            || !candidate.getId().equals(template.what().getId())
                        : !candidate.equals(template.what())) {
                        continue;
                    }
                    long units = Math.min(remainingUnits, entry.getValue() / template.amount());
                    if (units <= 0L) {
                        continue;
                    }
                    alternatives.add(new ECOAE2InputSelection.Alternative(
                        new GenericStack(candidate, template.amount()), units
                    ));
                    entry.setValue(entry.getValue() - Math.multiplyExact(template.amount(), units));
                    remainingUnits -= units;
                }
                if (remainingUnits > 0L) {
                    throw new IllegalStateException(
                        "Planner-selected input is not available in the CPU inventory: " + template
                    );
                }
            }
            resolved.add(new ECOAE2InputSelection(alternatives));
        }
        return new ECOSelectedInputPatternDetails(delegate, resolved);
    }

    /** Adds the molecular-assembler contract while retaining the selected exact input view. */
    public IPatternDetails asMolecularPattern() {
        if (!(delegate instanceof IMolecularAssemblerSupportedPattern molecular)) {
            return this;
        }
        return new MolecularView(molecular);
    }

    /** The planner-unaware AE2 pattern this view restricts. */
    public IPatternDetails unwrap() {
        return delegate;
    }

    /**
     * Peels ECO selected-input wrappers so FastPath type checks see the original AE2 pattern.
     * {@code MolecularView} is not an {@code AECraftingPattern}, so leaving it wrapped makes
     * every planner-selected crafting pattern look unsupported.
     */
    public static IPatternDetails unwrap(IPatternDetails details) {
        IPatternDetails current = details;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof ECOSelectedInputPatternDetails selected) {
                current = selected.delegate;
                continue;
            }
            if (current instanceof MolecularView view) {
                current = view.delegate;
                continue;
            }
            return current;
        }
        return current;
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
    public List<GenericStack> getOutputs() {
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

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        return delegate.getTooltip(level, flags);
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
            // The planner has already validated normal inputs, or selected this exact key through
            // the computation interface's explicit fuzzy-item rule. Re-running the delegate's
            // component comparison here would reject the configured dynamic variant before it can
            // reach the provider.
            return selected.what().equals(what);
        }

        @Override
        public AEKey getRemainingKey(AEKey what) {
            return delegate.getRemainingKey(what);
        }
    }

    private final class MolecularView implements IMolecularAssemblerSupportedPattern {
        private final IMolecularAssemblerSupportedPattern delegate;

        private MolecularView(IMolecularAssemblerSupportedPattern delegate) {
            this.delegate = delegate;
        }

        @Override
        public AEItemKey getDefinition() {
            return ECOSelectedInputPatternDetails.this.getDefinition();
        }

        @Override
        public IInput[] getInputs() {
            return ECOSelectedInputPatternDetails.this.getInputs();
        }

        @Override
        public GenericStack getPrimaryOutput() {
            return ECOSelectedInputPatternDetails.this.getPrimaryOutput();
        }

        @Override
        public List<GenericStack> getOutputs() {
            return ECOSelectedInputPatternDetails.this.getOutputs();
        }

        @Override
        public boolean supportsPushInputsToExternalInventory() {
            return ECOSelectedInputPatternDetails.this.supportsPushInputsToExternalInventory();
        }

        @Override
        public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
            ECOSelectedInputPatternDetails.this.pushInputsToExternalInventory(inputHolder, inputSink);
        }

        @Override
        public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
            return ECOSelectedInputPatternDetails.this.getTooltip(level, flags);
        }

        @Override
        public ItemStack assemble(CraftingInput input, Level level) {
            return delegate.assemble(input, level);
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            return delegate.getRemainingItems(input);
        }

        @Override
        public boolean isItemValid(int slot, AEItemKey key, Level level) {
            return slot >= 0
                && slot < inputs.length
                && inputs[slot].isValid(key, level);
        }

        @Override
        public boolean isSlotEnabled(int slot) {
            if (slot < 0 || slot >= originalSlots.length) {
                return false;
            }
            return delegate.isSlotEnabled(originalSlots[slot]);
        }

        @Override
        public void fillCraftingGrid(KeyCounter[] inputHolder, CraftingGridAccessor accessor) {
            KeyCounter[] collapsed = inputHolder.length == originalInputCount
                ? inputHolder
                : collapseInputHolder(inputHolder);
            delegate.fillCraftingGrid(collapsed, accessor);
        }
    }
}
