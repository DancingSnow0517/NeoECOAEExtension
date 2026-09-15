package cn.dancingsnow.neoecoae.mixins;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOBatchFairSchedulingControl;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingServiceTicker;
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingServiceBridge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

// GTLCore's priority-1000 submission handler must see automatic jobs before ECO's fallback.
@Mixin(value = CraftingService.class, priority = 1100, remap = false)
public abstract class CraftingServiceMixin
        implements ECOBatchFairSchedulingControl,
                cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings,
                cn.dancingsnow.neoecoae.api.me.ECOCraftingOutputRouter,
                cn.dancingsnow.neoecoae.api.me.ECOCraftingProviderRevision,
                ECOCraftingServiceTicker,
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

    @Override
    public void neoecoae$tickComputationCpusNow() {
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
        if (target == null) {
            return;
        }
        this.neoecoae$handleSubmitJob(job, requestingMachine, target, src, cir);
    }

    @Inject(
            method = "submitJob(Lappeng/api/networking/crafting/ICraftingPlan;"
                    + "Lappeng/api/networking/crafting/ICraftingRequester;"
                    + "Lappeng/api/networking/crafting/ICraftingCPU;"
                    + "Z"
                    + "Lappeng/api/networking/security/IActionSource;)"
                    + "Lappeng/api/networking/crafting/ICraftingSubmitResult;",
            at =
                    @At(
                            value = "INVOKE_ASSIGN",
                            target = "Lappeng/me/service/CraftingService;findSuitableCraftingCPU("
                                    + "Lappeng/api/networking/crafting/ICraftingPlan;"
                                    + "Z"
                                    + "Lappeng/api/networking/security/IActionSource;"
                                    + "Lorg/apache/commons/lang3/mutable/MutableObject;)"
                                    + "Lappeng/me/cluster/implementations/CraftingCPUCluster;"),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void neoecoae$autoSubmitAfterCompatibilityCpus(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir,
            CraftingCPUCluster nativeCpu,
            MutableObject<UnsuitableCpus> unsuitableCpus) {
        if (target == null) {
            this.neoecoae$handleSubmitJob(job, requestingMachine, null, src, cir);
        }
    }

    private void neoecoae$handleSubmitJob(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        ECOPlanningResult planningResult =
                job instanceof ECOCraftingPlanDiagnostics diagnostics ? diagnostics.neoecoae$getPlanningResult() : null;
        if (planningResult == null) {
            planningResult = ECOPlanningResultRegistry.find(job);
        }
        ECOPlanningResult boundResult = planningResult;
        ICraftingSubmitResult result = ECOPlanningResultRegistry.withSubmissionAlias(
                job,
                boundResult,
                () -> NeoECOCraftingServiceBridge.submitJob(this.grid, job, requestingMachine, target, src));
        if (result != null) {
            if (result.successful()) {
                this.updateList = true;
            }
            cir.setReturnValue(result);
        }
    }

    /**
     * Compatibility CPU providers may cancel automatic submission before AE2 reaches
     * {@code findSuitableCraftingCPU}, which bypasses the call-site injection above. Retry with an ECO CPU only when
     * the completed foreign/native path found no usable CPU.
     */
    @WrapMethod(
            method = "submitJob(Lappeng/api/networking/crafting/ICraftingPlan;"
                    + "Lappeng/api/networking/crafting/ICraftingRequester;"
                    + "Lappeng/api/networking/crafting/ICraftingCPU;"
                    + "Z"
                    + "Lappeng/api/networking/security/IActionSource;)"
                    + "Lappeng/api/networking/crafting/ICraftingSubmitResult;")
    private ICraftingSubmitResult neoecoae$submitJobFallback(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            Operation<ICraftingSubmitResult> original) {
        ICraftingSubmitResult result = original.call(job, requestingMachine, target, prioritizePower, src);
        if (target != null || !neoecoae$shouldTryAutomaticEcoFallback(result)) {
            return result;
        }

        ECOPlanningResult planningResult =
                job instanceof ECOCraftingPlanDiagnostics diagnostics ? diagnostics.neoecoae$getPlanningResult() : null;
        if (planningResult == null) {
            planningResult = ECOPlanningResultRegistry.find(job);
        }
        ECOPlanningResult boundResult = planningResult;
        ICraftingSubmitResult ecoResult = ECOPlanningResultRegistry.withSubmissionAlias(
                job,
                boundResult,
                () -> NeoECOCraftingServiceBridge.submitJob(this.grid, job, requestingMachine, null, src));
        if (ecoResult != null && ecoResult.successful()) {
            this.updateList = true;
        }
        return ecoResult != null ? ecoResult : result;
    }

    @org.spongepowered.asm.mixin.Unique private static boolean neoecoae$shouldTryAutomaticEcoFallback(ICraftingSubmitResult result) {
        if (result == null) {
            return true;
        }
        CraftingSubmitErrorCode error = result.errorCode();
        return error == CraftingSubmitErrorCode.NO_CPU_FOUND || error == CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND;
    }

    @WrapMethod(method = "insertIntoCpus")
    private long neoecoae$insertIntoCpus(AEKey what, long amount, Actionable type, Operation<Long> original) {
        return NeoECOCraftingServiceBridge.insertIntoCpus(
                this.grid, what, amount, type, original.call(what, amount, type));
    }

    @WrapMethod(method = "getRequestedAmount")
    private long neoecoae$getRequestedAmount(AEKey what, Operation<Long> original) {
        return NeoECOCraftingServiceBridge.getRequestedAmount(this.grid, what, original.call(what));
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
        for (var host : grid.getMachines(
                cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity.class)) {
            if (host.isFormed() && host.getMainNode().isOnline()) {
                return true;
            }
        }
        return false;
    }
}
