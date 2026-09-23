package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.ECOExtendedAEPlusBlocking;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The large workstation's copy of ExtendedAE's 36-slot pattern-provider logic.
 *
 * <p>The normal provider implementation is reused for pattern storage, return
 * inventory, settings and persistence. Only dispatch is specialized: a
 * workstation pattern is handed to the formed workstation controller instead
 * of being pushed into an adjacent inventory.</p>
 */
public final class LargeWorkstationPatternProvider extends PatternProviderLogic implements ECOParallelCraftingProvider {
    private static final int PATTERN_SLOT_COUNT = 36;

    private final ECOLargeIntegratedWorkingStationInterfaceBlockEntity host;
    private List<IPatternDetails> compatiblePatterns = List.of();
    private boolean compatiblePatternsDirty = true;
    private Object lastRecipes;
    @Nullable
    private ECOLargeIntegratedWorkingStationBlockEntity lastController;

    public LargeWorkstationPatternProvider(ECOLargeIntegratedWorkingStationInterfaceBlockEntity host) {
        super(host.getMainNode(), host, PATTERN_SLOT_COUNT);
        this.host = host;
    }

    @Nullable
    private ECOLargeIntegratedWorkingStationBlockEntity controller() {
        return host.getCluster() instanceof NEIntegratedWorkingStationCluster cluster ? cluster.getController() : null;
    }

    @Override
    public void updateRedstoneState() {
        LockCraftingMode before = getCraftingLockedReason();
        super.updateRedstoneState();
        if (before != getCraftingLockedReason()) {
            requestProviderUpdate();
        }
    }

    @Override
    public void updatePatterns() {
        compatiblePatternsDirty = true;
        // PatternProviderLogic.requestUpdate() refreshes the AE2 provider synchronously. Invalidate the
        // workstation-specific compatibility cache before delegating, otherwise the refresh can remount the
        // previous filtered list and leave the newly decoded patterns invisible until the network is replugged.
        super.updatePatterns();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        var controller = controller();
        if (host.getLevel() == null || controller == null) {
            lastController = null;
            compatiblePatterns = List.of();
            return List.of();
        }
        Object recipes = LargeWorkstationRecipes.getAll(host.getLevel());
        if (!compatiblePatternsDirty && lastController == controller && lastRecipes == recipes) return compatiblePatterns;
        List<IPatternDetails> result = new ArrayList<>();
        for (var pattern : super.getAvailablePatterns()) {
            if (controller.acceptPattern(pattern, null, false)) result.add(pattern);
        }
        compatiblePatterns = List.copyOf(result);
        compatiblePatternsDirty = false;
        lastController = controller;
        lastRecipes = recipes;
        return compatiblePatterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        var controller = controller();
        if (!canDispatch(controller, pattern)) {
            return false;
        }
        boolean accepted = controller.acceptPattern(pattern, inputs, true);
        if (accepted) {
            onPatternAccepted(pattern);
        }
        return accepted;
    }

    /** One ordinary dispatch transfers one batch up to the current coolant-supported limit. */
    @Override
    public int eco$getAvailableParallelSlots() {
        var controller = controller();
        return controller == null || !host.getMainNode().isActive() || !controller.canAcceptPattern()
                || isCraftingLocked()
                || getAvailablePatterns().isEmpty()
            ? 0 : controller.getMaxBatchParallelism();
    }

    @Override
    public boolean eco$pushPatternBatch(
        IPatternDetails pattern,
        KeyCounter[] inputTotal,
        long craftCount,
        @Nullable UUID craftingJobId
    ) {
        var controller = controller();
        if (!canDispatch(controller, pattern)) {
            return false;
        }
        boolean accepted = controller.acceptPatternBatch(pattern, inputTotal, craftCount, craftingJobId);
        if (accepted) {
            onPatternAccepted(pattern);
        }
        return accepted;
    }

    @Override
    public boolean isBusy() {
        var controller = controller();
        return controller == null || !controller.canAcceptPattern() || isCraftingLocked();
    }

    /** Called by the controller after this provider's primary result reached its final destination. */
    public void onPatternResult(@Nullable GenericStack result) {
        if (result == null) {
            return;
        }
        GenericStack unlockStack = getUnlockStack();
        if (unlockStack == null || !unlockStack.what().equals(result.what())
                || result.amount() < unlockStack.amount()) {
            return;
        }
        resetCraftingLock();
        requestProviderUpdate();
    }

    /** Releases a result lock when the owning CPU job was cancelled before producing a result. */
    public void onPatternAborted(@Nullable GenericStack expectedResult) {
        if (expectedResult == null) {
            return;
        }
        GenericStack unlockStack = getUnlockStack();
        if (unlockStack != null && unlockStack.what().equals(expectedResult.what())
                && unlockStack.amount() == expectedResult.amount()) {
            resetCraftingLock();
            requestProviderUpdate();
        }
    }

    private boolean canDispatch(
        @Nullable ECOLargeIntegratedWorkingStationBlockEntity controller,
        IPatternDetails pattern
    ) {
        if (controller == null || !host.getMainNode().isActive() || !getAvailablePatterns().contains(pattern)
                || isCraftingLocked()) {
            return false;
        }
        if (!isBlocking()) {
            return true;
        }
        // Dispatch bypasses EAEP's injection into the base pushPattern method. Apply the same
        // input-presence rule to the controller's pending ledger for both single and batch pushes.
        if (ECOExtendedAEPlusBlocking.isEnabled(getConfigManager())
                && ECOExtendedAEPlusBlocking.matchesPendingInputs(pattern, controller::containsPendingPatternInput)) {
            return true;
        }
        return !controller.containsPendingPatternInput(patternInputKeys(pattern));
    }

    private boolean isCraftingLocked() {
        return getCraftingLockedReason() != LockCraftingMode.NONE;
    }

    private static Set<AEKey> patternInputKeys(IPatternDetails pattern) {
        Set<AEKey> result = new HashSet<>();
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null) {
            return result;
        }
        for (IPatternDetails.IInput input : inputs) {
            if (input == null || input.getPossibleInputs() == null) {
                continue;
            }
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (candidate != null && candidate.what() != null) {
                    result.add(candidate.what().dropSecondary());
                }
            }
        }
        return result;
    }

    private void onPatternAccepted(IPatternDetails pattern) {
        // The specialized provider bypasses PatternProviderLogic.pushPattern, so explicitly retain AE2's
        // lock-until-pulse/result state transition after the controller accepted the batch.
        ((PatternProviderLogicAccessor) (Object) this).neoecoae$onPushPatternSuccess(pattern);
        requestProviderUpdate();
    }

    private void requestProviderUpdate() {
        ICraftingProvider.requestUpdate(host.getMainNode());
    }
}
