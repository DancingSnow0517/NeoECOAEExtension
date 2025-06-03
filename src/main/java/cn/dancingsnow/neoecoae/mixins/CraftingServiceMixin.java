package cn.dancingsnow.neoecoae.mixins;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.blocks.entity.computation.AbstractComputationBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import net.minecraft.nbt.CompoundTag;
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
import java.util.Set;

@Debug(export = true)
@Mixin(CraftingService.class)
public class CraftingServiceMixin {
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
    @Unique
    private final Set<NEComputationCluster> computationClusters = new HashSet<>();

    @Inject(
        method = "onServerEndTick",
        at = @At(
            value = "FIELD",
            target = "Lappeng/me/service/CraftingService;lastProcessedCraftingLogicChangeTick:J",
            opcode = Opcodes.GETFIELD,
            ordinal = 0
        )
    )
    private void tickClusters1(CallbackInfo ci, @Local long latestChange) {
        long latestChangeLocal = 0L;

        for (NEComputationCluster cluster : this.computationClusters) {
            if (cluster != null) {
//                for(AdvCraftingCPU cpu : cluster.getActiveCPUs()) {
//                    cpu.craftingLogic.tickCraftingLogic(this.energyGrid, (CraftingService)this);
//                    latestChangeLocal = Math.max(latestChangeLocal, cpu.craftingLogic.getLastModifiedOnTick());
//                }
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
        for (NEComputationCluster cluster : this.computationClusters) {
            if (cluster != null) {
//                for(AdvCraftingCPU cpu : cluster.getActiveCPUs()) {
//                    cpu.craftingLogic.getAllWaitingFor(this.currentlyCrafting);
//                }
            }
        }

    }

    @Inject(
        method = {"removeNode"},
        at = {@At("TAIL")}
    )
    private void onRemoveNode(IGridNode gridNode, CallbackInfo ci) {
        if (gridNode.getOwner() instanceof AbstractComputationBlockEntity<?>) {
            this.updateList = true;
        }
    }

    @Inject(
        method = {"addNode"},
        at = {@At("TAIL")}
    )
    private void onAddNode(IGridNode gridNode, CompoundTag savedData, CallbackInfo ci) {
        if (gridNode.getOwner() instanceof AbstractComputationBlockEntity<?>) {
            this.updateList = true;
        }
    }

    @Inject(
        method = {"updateCPUClusters"},
        at = {@At("TAIL")}
    )
    private void onUpdateCPUClusters(CallbackInfo ci) {
        this.computationClusters.clear();

        for (AbstractComputationBlockEntity<?> blockEntity : this.grid.getMachines(AbstractComputationBlockEntity.class)) {
            NEComputationCluster cluster = blockEntity.getCluster();
            if (cluster != null) {
                this.computationClusters.add(cluster);

//                for(AdvCraftingCPU cpu : cluster.getActiveCPUs()) {
//                    ICraftingLink maybeLink = cpu.craftingLogic.getLastLink();
//                    if (maybeLink != null) {
//                        this.addLink((CraftingLink)maybeLink);
//                    }
//                }
            }
        }

    }

    @Inject(
        method = "insertIntoCpus",
        at = @At("RETURN"),
        order = 500
    )
    private void onInsertIntoCpus(
        AEKey what,
        long amount,
        Actionable type,
        CallbackInfoReturnable<Long> cir,
        @Local(ordinal = 1) LocalLongRef inserted
    ) {
        for (NEComputationCluster cluster : this.computationClusters) {
            if (cluster != null) {
//                for (var cpu : cluster.getActiveCPUs()) {
//                    inserted.set(inserted.get() + cpu.craftingLogic.insert(what, amount - inserted.get(), type));
//                }
            }
        }
        cir.setReturnValue(inserted.get());
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
        @Local MutableObject<UnsuitableCpus> unsuitableCpusResult
    ) {
        if (target instanceof NEComputationCluster advCpuCluster) {
            cir.setReturnValue(advCpuCluster.submitJob(this.grid, job, src, requestingMachine));
        } else {
            var cluster = neoecoae$findSuitableAdvCraftingCPU(job, src, unsuitableCpusResult);
            if (cluster != null) {
                updateList = true;
                cir.setReturnValue(cluster.submitJob(this.grid, job, src, requestingMachine));
            }
        }
    }

    @Unique
    private NEComputationCluster neoecoae$findSuitableAdvCraftingCPU(
        ICraftingPlan job,
        IActionSource src,
        MutableObject<UnsuitableCpus> unsuitableCpusResult
    ) {
        var validCpusClusters = new ArrayList<NEComputationCluster>(this.computationClusters.size());
        int offline = 0;
        int tooSmall = 0;
        int excluded = 0;

        for (var cluster : this.computationClusters) {
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

    @Inject(method = "getCpus", at = @At("RETURN"), cancellable = true)
    private void onGetCpus(
        CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir,
        @Local ImmutableSet.Builder<ICraftingCPU> cpus
    ) {
        for (var cluster : this.computationClusters) {
//            for (var cpu : cluster.getActiveCPUs()) {
//                cpus.add(cpu);
//            }
//            cpus.add(cluster.getFakeCPU());
        }
        cir.setReturnValue(cpus.build());
    }

    @Inject(method = "getRequestedAmount", at = @At("RETURN"), cancellable = true)
    private void onGetRequestedAmount(AEKey what, CallbackInfoReturnable<Long> cir, @Local long requested) {
        for (var cluster : this.computationClusters) {
//            for (var cpu : cluster.getActiveCPUs()) {
//                requested += cpu.craftingLogic.getWaitingFor(what);
//            }
        }

        cir.setReturnValue(requested);
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void onHasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        for (var cluster : this.computationClusters) {
//            for (var activeCpu : cluster.getActiveCPUs()) {
//                if (activeCpu == cpu) {
//                    cir.setReturnValue(true);
//                    return;
//                }
//            }
        }
    }
}
