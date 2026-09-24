package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.mixins.ae2.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** AE2 pattern storage and dispatch for the formed large workstation. */
public final class LargeWorkstationPatternProvider extends PatternProviderLogic {
    private final ECOLargeIntegratedWorkingStationInterfaceBlockEntity host;
    private List<IPatternDetails> compatiblePatterns = List.of();
    private boolean compatiblePatternsDirty = true;
    private Object lastRecipes;

    @Nullable private ECOLargeIntegratedWorkingStationBlockEntity lastController;

    public LargeWorkstationPatternProvider(ECOLargeIntegratedWorkingStationInterfaceBlockEntity host) {
        super(host.getMainNode(), host, 36);
        this.host = host;
    }

    @Nullable private ECOLargeIntegratedWorkingStationBlockEntity controller() {
        NEIntegratedWorkingStationCluster cluster = host.getCluster();
        return cluster == null ? null : cluster.getController();
    }

    @Override
    public void updatePatterns() {
        compatiblePatternsDirty = true;
        super.updatePatterns();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        var controller = controller();
        if (host.getLevel() == null || controller == null) {
            lastController = null;
            compatiblePatterns = List.of();
            return compatiblePatterns;
        }
        Object recipes = host.getLevel().getRecipeManager();
        if (!compatiblePatternsDirty && lastController == controller && lastRecipes == recipes) {
            return compatiblePatterns;
        }
        List<IPatternDetails> compatible = new ArrayList<>();
        for (IPatternDetails pattern : super.getAvailablePatterns()) {
            if (controller.acceptPattern(pattern, null, false)) compatible.add(pattern);
        }
        compatiblePatterns = List.copyOf(compatible);
        compatiblePatternsDirty = false;
        lastController = controller;
        lastRecipes = recipes;
        return compatiblePatterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        var controller = controller();
        if (controller == null
                || !host.getMainNode().isActive()
                || isBusy()
                || !getAvailablePatterns().contains(pattern)) {
            return false;
        }
        if (isBlocking() && controller.containsPendingPatternInput(patternInputKeys(pattern))) {
            return false;
        }
        if (!controller.acceptPattern(pattern, inputs, true)) return false;
        ((PatternProviderLogicAccessor) (Object) this).neoecoae$onPushPatternSuccess(pattern);
        ICraftingProvider.requestUpdate(host.getMainNode());
        return true;
    }

    @Override
    public boolean isBusy() {
        var controller = controller();
        return controller == null
                || !controller.canAcceptPattern()
                || getCraftingLockedReason() != LockCraftingMode.NONE;
    }

    @Override
    public void updateRedstoneState() {
        LockCraftingMode before = getCraftingLockedReason();
        super.updateRedstoneState();
        if (before != getCraftingLockedReason()) ICraftingProvider.requestUpdate(host.getMainNode());
    }

    public void onPatternResult(@Nullable GenericStack result) {
        if (result == null) return;
        GenericStack unlockStack = getUnlockStack();
        if (unlockStack != null
                && unlockStack.what().equals(result.what())
                && result.amount() >= unlockStack.amount()) {
            resetCraftingLock();
            ICraftingProvider.requestUpdate(host.getMainNode());
        }
    }

    public void onPatternAborted(@Nullable GenericStack expectedResult) {
        if (expectedResult == null) return;
        GenericStack unlockStack = getUnlockStack();
        if (unlockStack != null
                && unlockStack.what().equals(expectedResult.what())
                && unlockStack.amount() == expectedResult.amount()) {
            resetCraftingLock();
            ICraftingProvider.requestUpdate(host.getMainNode());
        }
    }

    private static Set<AEKey> patternInputKeys(IPatternDetails pattern) {
        Set<AEKey> keys = new HashSet<>();
        for (var input : pattern.getInputs()) {
            if (input == null || input.getPossibleInputs() == null) continue;
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (candidate != null && candidate.what() != null)
                    keys.add(candidate.what().dropSecondary());
            }
        }
        return keys;
    }
}
