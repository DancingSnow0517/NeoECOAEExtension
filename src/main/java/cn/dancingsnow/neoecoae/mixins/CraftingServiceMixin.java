package cn.dancingsnow.neoecoae.mixins;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingLink;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.apache.commons.lang3.mutable.MutableObject;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin implements ECOCraftingNetworkSettings {
    @Unique
    private static final String NEOECOAE_IGNORE_PATTERN_SUBSTITUTIONS_KEY =
        "neoecoaeIgnorePatternSubstitutions";
    @Unique
    private static final Comparator<NEComputationCluster> NE_FAST_FIRST_COMPARATOR = Comparator.comparingInt(
            NEComputationCluster::getPooledParallelism)
        .reversed()
        .thenComparingLong(NEComputationCluster::getAvailableStorage);

    @Shadow
    private boolean updateList;

    @Shadow
    @Final
    private IGrid grid;
    @Shadow
    private long lastProcessedCraftingLogicChangeTick;
    @Shadow
    @Final
    private IEnergyService energyGrid;
    @Shadow
    @Final
    private Set<AEKey> currentlyCrafting;
    @Shadow
    @Final
    private NetworkCraftingProviders craftingProviders;

    @Shadow
    public abstract void addLink(CraftingLink link);

    @Unique
    private final Set<NEComputationCluster> neoecoae$computationClusters = new HashSet<>();
    @Unique
    private boolean neoecoae$ignorePatternSubstitutions;
    @Unique
    private boolean neoecoae$planningModeInitialized;
    @Unique
    private long neoecoae$substitutionPatternCountVersion = Long.MIN_VALUE;
    @Unique
    private int neoecoae$substitutionPatternCount;

    @Inject(
        method = "onServerEndTick",
        at = @At(
            value = "FIELD",
            target = "Lappeng/me/service/CraftingService;lastProcessedCraftingLogicChangeTick:J",
            opcode = Opcodes.GETFIELD,
            ordinal = 0
        )
    )
    private void tickClusters1(CallbackInfo ci, @Local(name = "latestChange") long latestChange) {
        long latestChangeLocal = 0L;

        for (NEComputationCluster cluster : this.neoecoae$computationClusters) {
            if (cluster != null) {
                for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                    cpu.getLogic().tickCraftingLogic(this.energyGrid, (CraftingService) (Object) this);
                    latestChangeLocal = Math.max(latestChangeLocal, cpu.getLogic().getLastModifiedOnTick());
                }
                cluster.pruneInactiveCPUs();
            }
        }

        if (latestChangeLocal > latestChange) {
            this.lastProcessedCraftingLogicChangeTick = -1L;
        }

    }

    @Inject(
        method = "onServerEndTick",
        at = @At(
            value = "FIELD",
            target = "Lappeng/me/service/CraftingService;interests:Lcom/google/common/collect/Multimap;",
            opcode = Opcodes.GETFIELD,
            ordinal = 0
        )
    )
    private void tickClusters2(CallbackInfo ci) {
        for (NEComputationCluster cluster : this.neoecoae$computationClusters) {
            if (cluster != null) {
                for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                    cpu.getLogic().getAllWaitingFor(this.currentlyCrafting);
                }
            }
        }

    }

    @Inject(
        method = {"removeNode"},
        at = {@At("TAIL")}
    )
    private void onRemoveNode(IGridNode gridNode, CallbackInfo ci) {
        if (gridNode.getOwner() instanceof NEBlockEntity<?, ?> blockEntity
            && blockEntity.getCluster() instanceof NEComputationCluster
        ) {
            this.updateList = true;
        }
    }

    @Inject(
        method = {"addNode"},
        at = {@At("TAIL")}
    )
    private void onAddNode(IGridNode gridNode, CompoundTag savedData, CallbackInfo ci) {
        if (!neoecoae$planningModeInitialized
            && savedData != null
            && savedData.contains(NEOECOAE_IGNORE_PATTERN_SUBSTITUTIONS_KEY, Tag.TAG_BYTE)) {
            neoecoae$ignorePatternSubstitutions = savedData.getBoolean(
                NEOECOAE_IGNORE_PATTERN_SUBSTITUTIONS_KEY);
            neoecoae$planningModeInitialized = true;
        }
        if (gridNode.getOwner() instanceof NEBlockEntity<?, ?> blockEntity
            && blockEntity.getCluster() instanceof NEComputationCluster
        ) {
            this.updateList = true;
        }

        Object owner = gridNode.getOwner();
        if (owner instanceof ECOCraftingSystemBlockEntity craftingHost) {
            neoecoae$initializeOrSyncPlanningMode(
                craftingHost.isLocallyIgnoringPatternSubstitutions(),
                craftingHost::applyNetworkIgnoringPatternSubstitutions);
        } else if (owner instanceof ECOComputationSystemBlockEntity computationHost) {
            neoecoae$initializeOrSyncPlanningMode(
                computationHost.isLocallyIgnoringPatternSubstitutions(),
                computationHost::applyNetworkIgnoringPatternSubstitutions);
        }
    }

    public void saveNodeData(IGridNode gridNode, CompoundTag savedData) {
        savedData.putBoolean(
            NEOECOAE_IGNORE_PATTERN_SUBSTITUTIONS_KEY,
            neoecoae$ignorePatternSubstitutions);
    }

    @Unique
    private void neoecoae$initializeOrSyncPlanningMode(boolean persistedValue, java.util.function.Consumer<Boolean> apply) {
        if (!neoecoae$planningModeInitialized) {
            neoecoae$ignorePatternSubstitutions = persistedValue;
            neoecoae$planningModeInitialized = true;
        } else {
            apply.accept(neoecoae$ignorePatternSubstitutions);
        }
    }

    @Override
    public boolean neoecoae$isIgnoringPatternSubstitutions() {
        return neoecoae$ignorePatternSubstitutions;
    }

    @Override
    public void neoecoae$setIgnoringPatternSubstitutions(boolean ignoringPatternSubstitutions) {
        neoecoae$planningModeInitialized = true;
        neoecoae$ignorePatternSubstitutions = ignoringPatternSubstitutions;
        for (ECOCraftingSystemBlockEntity host : grid.getMachines(ECOCraftingSystemBlockEntity.class)) {
            host.applyNetworkIgnoringPatternSubstitutions(ignoringPatternSubstitutions);
        }
        for (ECOComputationSystemBlockEntity host : grid.getMachines(ECOComputationSystemBlockEntity.class)) {
            host.applyNetworkIgnoringPatternSubstitutions(ignoringPatternSubstitutions);
        }
    }

    @Override
    public int neoecoae$getSubstitutionPatternCount() {
        long version = craftingProviders.getLastModifiedOnTick();
        if (version == neoecoae$substitutionPatternCountVersion) {
            return neoecoae$substitutionPatternCount;
        }

        Set<IPatternDetails> patterns = new HashSet<>();
        for (AEKey craftable : craftingProviders.getCraftableKeys()) {
            patterns.addAll(craftingProviders.getCraftingFor(craftable));
        }
        neoecoae$substitutionPatternCount = (int) Math.min(Integer.MAX_VALUE, patterns.stream()
            .filter(CraftingServiceMixin::neoecoae$hasSubstitutions)
            .count());
        neoecoae$substitutionPatternCountVersion = version;
        return neoecoae$substitutionPatternCount;
    }

    @Unique
    private static boolean neoecoae$hasSubstitutions(IPatternDetails pattern) {
        if (pattern instanceof AECraftingPattern craftingPattern
            && (craftingPattern.canSubstitute() || craftingPattern.canSubstituteFluids())) {
            return true;
        }
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            if (input.getPossibleInputs().length > 1) {
                return true;
            }
        }
        return false;
    }

    @Inject(
        method = {"updateCPUClusters"},
        at = {@At("TAIL")}
    )
    private void onUpdateCPUClusters(CallbackInfo ci) {
        this.neoecoae$computationClusters.clear();

        for (ECOComputationSystemBlockEntity blockEntity : this.grid.getMachines(ECOComputationSystemBlockEntity.class)) {
            NEComputationCluster cluster = blockEntity.getCluster();
            if (cluster != null) {
                this.neoecoae$computationClusters.add(cluster);
                for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                    ICraftingLink maybeLink = cpu.getLogic().getLastLink();
                    if (maybeLink != null) {
                        this.addLink((CraftingLink) maybeLink);
                    }
                }
            }
        }

    }

    @Inject(
        method = "insertIntoCpus",
        at = @At(value = "RETURN", shift = At.Shift.BY, by = -1),
        order = 500
    )
    private void onInsertIntoCpus(
        AEKey what,
        long amount,
        Actionable type,
        CallbackInfoReturnable<Long> cir,
        @Local(name = "inserted") LocalLongRef inserted
    ) {
        for (NEComputationCluster cluster : this.neoecoae$computationClusters) {
            if (cluster != null) {
                for (var cpu : cluster.getActiveCPUs()) {
                    inserted.set(inserted.get() + cpu.getLogic().insert(what, amount - inserted.get(), type));
                }
            }
        }
    }

    @Definition(id = "findSuitableCraftingCPU", method = "appeng/me/service/CraftingService.findSuitableCraftingCPU(Lappeng/api/networking/crafting/ICraftingPlan;ZLappeng/api/networking/security/IActionSource;Lorg/apache/commons/lang3/mutable/MutableObject;)Lappeng/me/cluster/implementations/CraftingCPUCluster;")
    @Expression("? = ?.findSuitableCraftingCPU(?, ?, ?, ?)")
    @Inject(
        method = "submitJob",
        at =
        @At(value = "MIXINEXTRAS:EXPRESSION", shift = At.Shift.AFTER),
        cancellable = true,
        order = 500
    )
    private void onSubmitJob(
        ICraftingPlan job,
        ICraftingRequester requestingMachine,
        ICraftingCPU target,
        boolean prioritizePower,
        IActionSource src,
        CallbackInfoReturnable<ICraftingSubmitResult> cir,
        @Local(name = "cpuCluster") CraftingCPUCluster cpuCluster,
        @Local(name = "unsuitableCpusResult") MutableObject<UnsuitableCpus> unsuitableCpusResult
    ) {
        if (target instanceof ECOCraftingCPU ecoCpu) {
            cir.setReturnValue(ecoCpu.getCluster().submitJob(this.grid, job, src, requestingMachine));
        } else if (target == null) {
            var cluster = neoecoae$findSuitableAdvCraftingCPU(job, src, unsuitableCpusResult);
            if (cluster != null) {
                updateList = true;
                cir.setReturnValue(cluster.submitJob(this.grid, job, src, requestingMachine));
            } else if (cpuCluster == null) {
                // If no CPUs were unsuitable, but we couldn't find one, that means there aren't any
                UnsuitableCpus unsuitableCpus = unsuitableCpusResult.getValue();
                if (unsuitableCpus == null) {
                    cir.setReturnValue(CraftingSubmitResult.NO_CPU_FOUND);
                } else {
                    cir.setReturnValue(CraftingSubmitResult.noSuitableCpu(unsuitableCpus));
                }
            }
        }
    }

    @Unique
    private NEComputationCluster neoecoae$findSuitableAdvCraftingCPU(
        ICraftingPlan job,
        IActionSource src,
        MutableObject<UnsuitableCpus> unsuitableCpusResult
    ) {
        var validCpusClusters = new ArrayList<NEComputationCluster>(this.neoecoae$computationClusters.size());
        int offline = 0;
        int tooSmall = 0;
        int excluded = 0;

        for (var cluster : this.neoecoae$computationClusters) {
            // A network group is a single pooled unit: only its representative member is considered
            // as a candidate, since submitJob() on any member routes to the same shared network cluster.
            if (!cluster.isNetworkRepresentative()) {
                continue;
            }
            if (!cluster.isActive()) {
                offline++;
                continue;
            }
            if (cluster.getAvailableStorage() < job.bytes()) {
                tooSmall++;
                continue;
            }
            if (!cluster.canBeAutoSelectedFor(src)) {
                excluded++;
                continue;
            }
            validCpusClusters.add(cluster);
        }

        if (validCpusClusters.isEmpty()) {
            if (offline > 0 || tooSmall > 0 || excluded > 0) {
                unsuitableCpusResult.setValue(new UnsuitableCpus(offline, 0, tooSmall, excluded));
            }
            return null;
        }

        validCpusClusters.sort((a, b) -> {
            // Prioritize sorting by selected mode
            var firstPreferred = a.canBeAutoSelectedFor(src);
            var secondPreferred = b.canBeAutoSelectedFor(src);
            if (firstPreferred != secondPreferred) {
                // Sort such that preferred comes first, not preferred second
                return Boolean.compare(secondPreferred, firstPreferred);
            }

            return NE_FAST_FIRST_COMPARATOR.compare(a, b);
        });

        return validCpusClusters.getFirst();
    }

    @Inject(
        method = "getCpus",
        at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet$Builder;build()Lcom/google/common/collect/ImmutableSet;"),
        order = 500
    )
    private void onGetCpus(
        CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir,
        @Local(name = "cpus") ImmutableSet.Builder<ICraftingCPU> cpus
    ) {
        for (var cluster : this.neoecoae$computationClusters) {
            List<ECOCraftingCPU> ecoCpus = cluster.getActiveCPUs();
            for (var cpu : ecoCpus) {
                cpus.add(cpu);
            }
            // Every member of a network group reports the same pooled numbers via its own fake CPU, so
            // only the representative advertises one to avoid inflating the reported free capacity.
            if (cluster.isNetworkRepresentative() && cluster.getActiveCPUCount() < cluster.getMaxThreads()) {
                cpus.add(cluster.getFakeCPU());
            }
        }
    }

    @Inject(
        method = "getRequestedAmount",
        at = @At("RETURN"),
        order = 500
    )
    private void onGetRequestedAmount(
        AEKey what,
        CallbackInfoReturnable<Long> cir,
        @Local(name = "requested") LocalLongRef requested
    ) {
        for (var cluster : this.neoecoae$computationClusters) {
            for (var cpu : cluster.getActiveCPUs()) {
                requested.set(requested.get() + cpu.getLogic().getWaitingFor(what));
            }
        }
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void onHasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        for (var cluster : this.neoecoae$computationClusters) {
            for (var activeCpu : cluster.getActiveCPUs()) {
                if (activeCpu == cpu) {
                    cir.setReturnValue(true);
                    return;
                }
            }
            // getCpus() also advertises the placeholder CPU of a cluster with a free thread, so hasCpu
            // has to recognise it or that entry can never be selected as a crafting target.
            if (cluster.isNetworkRepresentative()
                && cluster.getActiveCPUCount() < cluster.getMaxThreads()
                && cluster.getFakeCPU() == cpu) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
