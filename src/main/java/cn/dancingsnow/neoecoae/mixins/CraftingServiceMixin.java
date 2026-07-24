package cn.dancingsnow.neoecoae.mixins;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOAE2SnapshotFactory;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningHostLease;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningService;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableObject;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Debug;
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
import java.util.concurrent.Future;

@Debug(export = true)
@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin {
    @Unique
    private static final Comparator<NEComputationCluster> NE_FAST_FIRST_COMPARATOR = Comparator.comparingInt(
            NEComputationCluster::getCPUAccelerators)
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
    private long lastProcessedCraftableChangeTick;
    @Shadow
    @Final
    private IEnergyService energyGrid;
    @Shadow
    @Final
    private Set<AEKey> currentlyCrafting;

    @Shadow
    public abstract void addLink(CraftingLink link);

    @Unique
    private final Set<NEComputationCluster> neoecoae$computationClusters = new HashSet<>();

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"), cancellable = true, order = 500)
    private void neoecoae$beginPlanningOnECOHost(
        Level level,
        ICraftingSimulationRequester simRequester,
        AEKey what,
        long amount,
        CalculationStrategy strategy,
        CallbackInfoReturnable<Future<ICraftingPlan>> cir
    ) {
        if (level == null || simRequester == null) {
            return;
        }
        var lease = ECOPlanningHostLease.tryAcquire(this.neoecoae$computationClusters);
        if (lease.isEmpty()) {
            return;
        }
        var snapshot = ECOAE2SnapshotFactory.capture(
            this.grid,
            simRequester,
            what,
            amount,
            strategy,
            this.lastProcessedCraftableChangeTick
        );
        if (snapshot.isEmpty()) {
            lease.get().close();
            return;
        }

        // AE2 constructs its calculation on the server thread because the constructor
        // snapshots mutable network state. Keep that object ready before dispatching
        // ECO's independent planning worker.
        CraftingCalculation fallback;
        try {
            fallback = new CraftingCalculation(
                level,
                this.grid,
                simRequester,
                new GenericStack(what, amount),
                strategy
            );
        } catch (RuntimeException | LinkageError failure) {
            lease.get().close();
            return;
        }
        cir.setReturnValue(ECOPlanningService.submit(
            snapshot.get(),
            strategy,
            lease.get(),
            fallback::run
        ));
    }

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
        if (gridNode.getOwner() instanceof NEBlockEntity<?, ?> blockEntity
            && blockEntity.getCluster() instanceof NEComputationCluster
        ) {
            this.updateList = true;
        }
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
        @Local(ordinal = 1) LocalLongRef inserted
    ) {
        for (NEComputationCluster cluster : this.neoecoae$computationClusters) {
            if (cluster != null) {
                for (var cpu : cluster.getActiveCPUs()) {
                    inserted.set(inserted.get() + cpu.getLogic().insert(what, amount - inserted.get(), type));
                }
            }
        }
    }

    @Inject(
        method = "submitJob",
        at =
        @At(
            value = "INVOKE",
            target = "appeng/me/service/CraftingService.findSuitableCraftingCPU "
                + "(Lappeng/api/networking/crafting/ICraftingPlan;ZLappeng/api/networking/security/IActionSource;"
                + "Lorg/apache/commons/lang3/mutable/MutableObject;)"
                + "Lappeng/me/cluster/implementations/CraftingCPUCluster;"
        ),
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
        @Local(name = "unsuitableCpusResult") MutableObject<UnsuitableCpus> unsuitableCpusResult
    ) {
        if (target instanceof ECOCraftingCPU ecoCpu) {
            cir.setReturnValue(ecoCpu.getCluster().submitJob(this.grid, job, src, requestingMachine));
        } else if (target == null) {
            var cluster = neoecoae$findSuitableAdvCraftingCPU(job, src, unsuitableCpusResult);
            if (cluster != null) {
                updateList = true;
                ICraftingSubmitResult result = cluster.submitJob(this.grid, job, src, requestingMachine);
                if (result.successful()) {
                    cir.setReturnValue(result);
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
            if (ecoCpus.size() < cluster.getMaxThreads()) {
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
        }
    }
}
