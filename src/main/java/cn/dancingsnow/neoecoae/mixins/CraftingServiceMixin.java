package cn.dancingsnow.neoecoae.mixins;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOBatchFairSchedulingControl;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingServiceBridge;
import java.util.HashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, priority = 900, remap = false)
public abstract class CraftingServiceMixin
        implements ECOBatchFairSchedulingControl,
                cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings,
                cn.dancingsnow.neoecoae.api.me.ECOCraftingOutputRouter,
                cn.dancingsnow.neoecoae.api.me.ECOCraftingProviderRevision,
                appeng.api.networking.IGridServiceProvider {
    @org.spongepowered.asm.mixin.Unique private long neoecoae$providerRevision;

    @Override
    public long neoecoae$getProviderRevision() {
        return neoecoae$providerRevision;
    }

    @Inject(
            method = {"addNode", "removeNode", "refreshNodeCraftingProvider"},
            at = @At("HEAD"))
    private void neoecoae$invalidateProviderCursors(CallbackInfo ci) {
        neoecoae$providerRevision++;
    }

    @Override
    public long neoecoae$insertIntoCpuForJob(java.util.UUID craftingJobId, AEKey what, long amount, Actionable type) {
        if (craftingJobId == null || what == null || amount <= 0L) return 0L;
        for (var cluster : NeoECOCraftingServiceBridge.getComputationClusters(grid)) {
            for (var cpu : cluster.getActiveCPUs(grid)) {
                if (cpu.getLogic().hasCraftingJob(craftingJobId)) {
                    return cpu.getLogic().insertForJob(craftingJobId, what, amount, type);
                }
            }
        }
        return 0L;
    }

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    @Final
    private IEnergyService energyGrid;

    @Shadow
    @Final
    private Set<AEKey> currentlyCrafting;

    @Shadow
    private long lastProcessedCraftingLogicChangeTick;

    @Shadow
    private boolean updateList;

    @Shadow
    @Final
    private appeng.me.service.helpers.NetworkCraftingProviders craftingProviders;

    @Shadow
    public abstract void addLink(CraftingLink link);

    /** ECO CPUs are ticked before AE2's own crafting bookkeeping runs. */
    private Set<AEKey> neoecoae$pendingComputationCrafting = Set.of();

    private Set<AEKey> neoecoae$lastComputationCrafting = Set.of();

    @Override
    public boolean isBatchFairSchedulingEnabled() {
        boolean foundNetwork = false;
        for (ECOCraftingSystemBlockEntity controller : this.grid.getMachines(ECOCraftingSystemBlockEntity.class)) {
            if (controller.getCluster() == null || controller.getCluster().getNetworkCluster() == null) {
                continue;
            }
            foundNetwork = true;
            if (!controller.getCluster().getNetworkCluster().isBatchFairSchedulingEnabled()) {
                return false;
            }
        }
        return foundNetwork;
    }

    @Override
    public void setBatchFairSchedulingEnabled(boolean enabled) {
        Set<Object> networks = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ECOCraftingSystemBlockEntity controller : this.grid.getMachines(ECOCraftingSystemBlockEntity.class)) {
            if (controller.getCluster() != null
                    && controller.getCluster().getNetworkCluster() != null
                    && networks.add(controller.getCluster().getNetworkCluster())) {
                controller.getCluster().getNetworkCluster().setBatchFairSchedulingEnabled(enabled);
            }
        }
    }

    private boolean neoecoae$ignorePatternSubstitutions;
    private boolean neoecoae$fastPlannerEnabled = true;
    private boolean neoecoae$cyclePlanningEnabled = true;
    private boolean neoecoae$settingsLoaded;
    private long neoecoae$substitutionCountRevision = Long.MIN_VALUE;
    private int neoecoae$substitutionCount;

    private void neoecoae$markSettingsDirty() {
        neoecoae$settingsLoaded = true;
        for (var host : grid.getMachines(
                cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity.class)) {
            host.setChanged();
            host.markComputationStatsDirty();
        }
    }

    @Override
    public void saveNodeData(IGridNode node, net.minecraft.nbt.CompoundTag data) {
        data.putBoolean("neoecoaeIgnorePatternSubstitutions", neoecoae$ignorePatternSubstitutions);
        data.putBoolean("neoecoaeFastPlannerEnabled", neoecoae$fastPlannerEnabled);
        data.putBoolean("neoecoaeCyclePlanningEnabled", neoecoae$cyclePlanningEnabled);
    }

    @Inject(method = "addNode", at = @At("TAIL"))
    private void neoecoae$onAddNode(IGridNode gridNode, net.minecraft.nbt.CompoundTag savedData, CallbackInfo ci) {
        if (!neoecoae$settingsLoaded && savedData != null && savedData.contains("neoecoaeFastPlannerEnabled")) {
            neoecoae$ignorePatternSubstitutions = savedData.getBoolean("neoecoaeIgnorePatternSubstitutions");
            neoecoae$fastPlannerEnabled = savedData.getBoolean("neoecoaeFastPlannerEnabled");
            neoecoae$cyclePlanningEnabled = !savedData.contains("neoecoaeCyclePlanningEnabled")
                    || savedData.getBoolean("neoecoaeCyclePlanningEnabled");
            neoecoae$settingsLoaded = true;
        }
        if (NeoECOCraftingServiceBridge.isComputationClusterNode(gridNode)) {
            this.updateList = true;
        }
    }

    @Inject(method = "removeNode", at = @At("TAIL"))
    private void neoecoae$onRemoveNode(IGridNode gridNode, CallbackInfo ci) {
        if (NeoECOCraftingServiceBridge.isComputationClusterNode(gridNode)) {
            this.updateList = true;
        }
    }

    @Inject(method = "updateCPUClusters", at = @At("TAIL"))
    private void neoecoae$onUpdateCPUClusters(CallbackInfo ci) {
        NeoECOCraftingServiceBridge.addRestoredLinks((CraftingService) (Object) this, this.grid);
    }

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void neoecoae$tickComputationCpus(CallbackInfo ci) {
        Set<AEKey> computationCrafting = new HashSet<>();
        if (NeoECOCraftingServiceBridge.tickComputationCpus(
                (CraftingService) (Object) this, this.grid, this.energyGrid, computationCrafting)) {
            this.updateList = true;
        }
        this.neoecoae$pendingComputationCrafting = Set.copyOf(computationCrafting);
        if (!this.neoecoae$lastComputationCrafting.equals(computationCrafting)) {
            // Make AE2 rebuild currentlyCrafting and notify its CraftingWatcher instances.
            this.lastProcessedCraftingLogicChangeTick = Long.MIN_VALUE;
        }
        this.neoecoae$lastComputationCrafting = this.neoecoae$pendingComputationCrafting;
    }

    @Inject(
            method = "onServerEndTick",
            at = @At(value = "INVOKE", target = "java/util/Set.clear()V", ordinal = 0, shift = At.Shift.AFTER))
    private void neoecoae$mergeComputationCrafting(CallbackInfo ci) {
        // This is immediately after AE2 clears currentlyCrafting and before it rebuilds the vanilla CPU entries.
        this.currentlyCrafting.addAll(this.neoecoae$pendingComputationCrafting);
    }

    @Inject(
            method = "submitJob(Lappeng/api/networking/crafting/ICraftingPlan;"
                    + "Lappeng/api/networking/crafting/ICraftingRequester;"
                    + "Lappeng/api/networking/crafting/ICraftingCPU;"
                    + "Z"
                    + "Lappeng/api/networking/security/IActionSource;)"
                    + "Lappeng/api/networking/crafting/ICraftingSubmitResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void neoecoae$submitJob(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        this.neoecoae$handleSubmitJob(job, requestingMachine, target, src, cir);
    }

    private void neoecoae$handleSubmitJob(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        ICraftingSubmitResult result =
                NeoECOCraftingServiceBridge.submitJob(this.grid, job, requestingMachine, target, src);
        if (result != null) {
            if (result.successful()) {
                this.updateList = true;
            }
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "insertIntoCpus", at = @At("RETURN"), cancellable = true)
    private void neoecoae$insertIntoCpus(AEKey what, long amount, Actionable type, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(
                NeoECOCraftingServiceBridge.insertIntoCpus(this.grid, what, amount, type, cir.getReturnValue()));
    }

    @Inject(method = "getRequestedAmount", at = @At("RETURN"), cancellable = true)
    private void neoecoae$getRequestedAmount(AEKey what, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(NeoECOCraftingServiceBridge.getRequestedAmount(this.grid, what, cir.getReturnValue()));
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void neoecoae$hasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (NeoECOCraftingServiceBridge.hasCpu(this.grid, cpu)) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean neoecoae$isIgnoringPatternSubstitutions() {
        return neoecoae$ignorePatternSubstitutions;
    }

    @Override
    public void neoecoae$setIgnoringPatternSubstitutions(boolean value) {
        neoecoae$ignorePatternSubstitutions = value;
        neoecoae$markSettingsDirty();
    }

    @Override
    public int neoecoae$getSubstitutionPatternCount() {
        if (neoecoae$substitutionCountRevision == neoecoae$providerRevision) return neoecoae$substitutionCount;
        Set<appeng.api.crafting.IPatternDetails> patterns = new HashSet<>();
        for (AEKey key : craftingProviders.getCraftableKeys()) patterns.addAll(craftingProviders.getCraftingFor(key));
        int count = 0;
        for (var pattern : patterns) {
            boolean substitutions = pattern instanceof appeng.crafting.pattern.AECraftingPattern crafting
                    && (crafting.canSubstitute() || crafting.canSubstituteFluids());
            for (var input : pattern.getInputs()) {
                if (input.getPossibleInputs().length > 1) substitutions = true;
            }
            if (substitutions) count++;
        }
        neoecoae$substitutionCountRevision = neoecoae$providerRevision;
        neoecoae$substitutionCount = count;
        return count;
    }

    @Override
    public boolean neoecoae$isFastPlannerEnabled() {
        return neoecoae$fastPlannerEnabled;
    }

    @Override
    public void neoecoae$setFastPlannerEnabled(boolean value) {
        neoecoae$fastPlannerEnabled = value;
        neoecoae$markSettingsDirty();
    }

    @Override
    public boolean neoecoae$isCyclePlanningEnabled() {
        return neoecoae$cyclePlanningEnabled;
    }

    @Override
    public void neoecoae$setCyclePlanningEnabled(boolean value) {
        neoecoae$cyclePlanningEnabled = value;
        neoecoae$markSettingsDirty();
    }

    @Override
    public boolean neoecoae$hasComputationHost() {
        return !cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingServiceBridge.getComputationClusters(this.grid)
                .isEmpty();
    }
}
