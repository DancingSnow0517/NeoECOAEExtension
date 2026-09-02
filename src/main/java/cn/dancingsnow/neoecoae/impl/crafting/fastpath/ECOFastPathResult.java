package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOFastPathResult {
    private final boolean negative;
    private final List<GenericStack> outputEntries;
    private final List<GenericStack> remainingEntries;
    private final List<GenericStack> inputEntries;
    private final ECOReusableStateModel reusableStateModel;
    private final Set<FastPathCapability> capabilities;
    private final String rejectReason;
    private final ECORecipeClassifier.Type type;
    private final List<ECOFastPathComponentChange> inputComponentChanges;
    private final List<ECOFastPathComponentChange> outputComponentChanges;
    private final List<ECOFastPathDurabilityDelta> durabilityDeltas;
    private final List<GenericStack> reusableInputs;
    private final long createdTick;

    private ECOFastPathResult(
        boolean negative,
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long lastAccessTick,
        @Nullable ECOReusableStateModel reusableStateModel,
        Set<FastPathCapability> capabilities,
        String rejectReason,
        ECORecipeClassifier.Type type,
        List<ECOFastPathComponentChange> inputComponentChanges,
        List<ECOFastPathComponentChange> outputComponentChanges,
        List<ECOFastPathDurabilityDelta> durabilityDeltas,
        List<GenericStack> reusableInputs
    ) {
        this.negative = negative;
        this.outputEntries = List.copyOf(outputEntries);
        this.remainingEntries = List.copyOf(remainingEntries);
        this.inputEntries = List.copyOf(inputEntries);
        this.reusableStateModel = reusableStateModel;
        this.capabilities = Set.copyOf(capabilities);
        this.rejectReason = rejectReason == null ? "" : rejectReason;
        this.type = type == null ? ECORecipeClassifier.Type.NORMAL : type;
        this.inputComponentChanges = List.copyOf(inputComponentChanges);
        this.outputComponentChanges = List.copyOf(outputComponentChanges);
        this.durabilityDeltas = List.copyOf(durabilityDeltas);
        this.reusableInputs = List.copyOf(reusableInputs);
        this.createdTick = lastAccessTick;
    }

    public static ECOFastPathResult positive(
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long tick
    ) {
        return positive(outputEntries, remainingEntries, inputEntries, tick, null, Set.of(FastPathCapability.PURE_LINEAR),
            ECORecipeClassifier.Type.NORMAL, List.of(), List.of(), List.of(), List.of());
    }

    public static ECOFastPathResult positive(
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long tick,
        ECOReusableStateModel reusableStateModel
    ) {
        return positive(outputEntries, remainingEntries, inputEntries, tick, reusableStateModel,
            reusableStateModel == null ? Set.of(FastPathCapability.PURE_LINEAR) : Set.of(reusableStateModel.capability()),
            ECORecipeClassifier.Type.NORMAL, List.of(), List.of(), List.of(), List.of());
    }

    public static ECOFastPathResult positive(
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        List<GenericStack> inputEntries,
        long tick,
        @Nullable ECOReusableStateModel reusableStateModel,
        Set<FastPathCapability> capabilities,
        ECORecipeClassifier.Type type,
        List<ECOFastPathComponentChange> inputComponentChanges,
        List<ECOFastPathComponentChange> outputComponentChanges,
        List<ECOFastPathDurabilityDelta> durabilityDeltas,
        List<GenericStack> reusableInputs
    ) {
        return new ECOFastPathResult(false, outputEntries, remainingEntries, inputEntries, tick, reusableStateModel,
            capabilities, "",
            type, inputComponentChanges, outputComponentChanges, durabilityDeltas, reusableInputs);
    }

    public static ECOFastPathResult negative(long tick) {
        return negative(tick, "VERIFICATION_REJECTED");
    }

    public static ECOFastPathResult negative(long tick, String rejectReason) {
        return new ECOFastPathResult(true, List.of(), List.of(), List.of(), tick, null, Set.of(), rejectReason,
            ECORecipeClassifier.Type.NORMAL, List.of(), List.of(), List.of(), List.of());
    }

    public boolean isNegative() {
        return negative;
    }

    public List<GenericStack> outputEntries() {
        return outputEntries;
    }

    public List<GenericStack> remainingEntries() {
        return remainingEntries;
    }

    public List<GenericStack> inputEntries() {
        return inputEntries;
    }

    @Nullable
    public ECOReusableStateModel reusableStateModel() { return reusableStateModel; }

    @Nullable
    public ECODurabilityBatchModel durabilityModel() {
        return reusableStateModel instanceof ECODurabilityBatchModel durability ? durability : null;
    }

    public Set<FastPathCapability> capabilities() { return capabilities; }

    public boolean hasCapability(FastPathCapability capability) { return capabilities.contains(capability); }

    public String rejectReason() { return rejectReason; }

    public ECORecipeClassifier.Type type() { return type; }

    public List<ECOFastPathComponentChange> inputComponentChanges() { return inputComponentChanges; }

    public List<ECOFastPathComponentChange> outputComponentChanges() { return outputComponentChanges; }

    public List<ECOFastPathDurabilityDelta> durabilityDeltas() { return durabilityDeltas; }

    public List<GenericStack> reusableInputs() { return reusableInputs; }

    /** Component changes between two slot snapshots, excluding empty slots and different item identities. */
    public static List<ECOFastPathComponentChange> componentChanges(List<ItemStack> before, List<ItemStack> after) {
        List<ECOFastPathComponentChange> result = new ArrayList<>();
        int size = Math.min(before.size(), after.size());
        for (int i = 0; i < size; i++) {
            ItemStack left = before.get(i);
            ItemStack right = after.get(i);
            if (left == null || right == null || left.isEmpty() || right.isEmpty()
                    || !ItemStack.isSameItem(left, right)
                    || ItemStack.isSameItemSameComponents(left, right)) continue;
            result.add(new ECOFastPathComponentChange(left, right));
        }
        return List.copyOf(result);
    }

    /** Durability deltas observed in matching slots. */
    public static List<ECOFastPathDurabilityDelta> durabilityDeltas(List<ItemStack> before, List<ItemStack> after) {
        List<ECOFastPathDurabilityDelta> result = new ArrayList<>();
        int size = Math.min(before.size(), after.size());
        for (int i = 0; i < size; i++) {
            ItemStack left = before.get(i);
            ItemStack right = after.get(i);
            if (left == null || left.isEmpty() || !left.isDamageableItem()) continue;
            int delta = right == null || right.isEmpty() ? -1 : right.getDamageValue() - left.getDamageValue();
            if (delta != 0 || (right != null && !right.isEmpty() && right.isDamageableItem())) {
                result.add(new ECOFastPathDurabilityDelta(left, right, delta));
            }
        }
        return List.copyOf(result);
    }

    /** Inputs that survived the simulated craft as the same item, including component/damage transitions. */
    public static List<GenericStack> reusableInputs(List<ItemStack> before, List<ItemStack> after) {
        List<GenericStack> result = new ArrayList<>();
        int size = Math.min(before.size(), after.size());
        for (int i = 0; i < size; i++) {
            ItemStack left = before.get(i);
            ItemStack right = after.get(i);
            if (left == null || right == null || left.isEmpty() || right.isEmpty()
                    || !ItemStack.isSameItem(left, right)) continue;
            GenericStack generic = GenericStack.fromItemStack(left.copyWithCount(1));
            if (generic != null) result.add(generic);
        }
        return List.copyOf(result);
    }

    /**
     * Full value comparison against a dispatch's expected data. Called exactly once per dispatch, from
     * {@link ECOCraftingFastPathCache#lookup}; downstream stages carry the resulting
     * {@link ECOVerifiedFastPathRecipe} instead of repeating it.
     */
    public boolean matchesExecution(ECOExtractedPatternExecution execution) {
        return !negative
            && outputEntries.equals(execution.expectedOutputs())
            && remainingEntries.equals(execution.expectedContainerItems())
            && inputEntries.equals(execution.inputItems());
    }

    public long getCreatedTick() {
        return createdTick;
    }
}
