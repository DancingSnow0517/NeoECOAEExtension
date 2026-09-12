package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import java.util.ArrayList;
import java.util.List;
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

    private final ECOMachineInterfaceBlockEntity<?> host;
    private List<IPatternDetails> compatiblePatterns = List.of();
    private boolean compatiblePatternsDirty = true;
    @Nullable
    private ECOLargeIntegratedWorkingStationBlockEntity lastController;

    public LargeWorkstationPatternProvider(ECOMachineInterfaceBlockEntity<?> host) {
        super(host.getMainNode(), host, PATTERN_SLOT_COUNT);
        this.host = host;
    }

    @Nullable
    private ECOLargeIntegratedWorkingStationBlockEntity controller() {
        return host.getCluster() instanceof NEIntegratedWorkingStationCluster cluster ? cluster.getController() : null;
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
        if (!compatiblePatternsDirty && lastController == controller) return compatiblePatterns;
        List<IPatternDetails> result = new ArrayList<>();
        for (var pattern : super.getAvailablePatterns()) {
            if (controller.acceptPattern(pattern, null, false)) result.add(pattern);
        }
        compatiblePatterns = List.copyOf(result);
        compatiblePatternsDirty = false;
        lastController = controller;
        return compatiblePatterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        var controller = controller();
        if (controller == null || !host.getMainNode().isActive() || !getAvailablePatterns().contains(pattern)) {
            return false;
        }
        return controller.acceptPattern(pattern, inputs, true);
    }

    /**
     * One ordinary dispatch transfers one complete 1024-craft input batch. The queue itself is intentionally
     * unbounded; this number is the per-dispatch parallel width, not a limit on how many requests the interface
     * may retain.
     */
    @Override
    public int eco$getAvailableParallelSlots() {
        var controller = controller();
        return controller == null || !host.getMainNode().isActive() || getAvailablePatterns().isEmpty()
            ? 0 : ECOLargeIntegratedWorkingStationBlockEntity.PARALLELISM;
    }

    @Override
    public boolean eco$pushPatternBatch(
        IPatternDetails pattern,
        KeyCounter[] inputTotal,
        long craftCount,
        @Nullable UUID craftingJobId
    ) {
        var controller = controller();
        if (controller == null || !host.getMainNode().isActive() || !getAvailablePatterns().contains(pattern)) {
            return false;
        }
        return controller.acceptPatternBatch(pattern, inputTotal, craftCount, craftingJobId);
    }

    @Override
    public boolean isBusy() {
        var controller = controller();
        return controller == null || !controller.canAcceptPattern();
    }
}
