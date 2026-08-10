package cn.dancingsnow.neoecoae.impl.crafting.fastpath.external;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.integration.ae2lt.AE2LTBatchCraftingBridge;
import cn.dancingsnow.neoecoae.integration.megacells.MEGACellsBatchCraftingBridge;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchEnergyReservation;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOReusableCraftingPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.Level;

/** Makes AE2-compatible CPUs transactional clients of NeoECO's verified worker FastPath. */
public final class ECOExternalCpuFastPathExecutor {
    private ECOExternalCpuFastPathExecutor() {
    }

    public static boolean dispatchOne(
            CraftingService craftingService,
            IEnergyService energyService,
            Level level,
            ECOExternalCpuJobView job) {
        var ae2ltBatchBridge = new AE2LTBatchCraftingBridge();
        var megacellsBatchBridge = new MEGACellsBatchCraftingBridge();
        var taskIterator = job.tasks();
        while (taskIterator.hasNext()) {
            var task = taskIterator.next();
            long taskRemaining = task.remaining();
            if (taskRemaining <= 0L) {
                taskIterator.remove();
                continue;
            }

            IPatternDetails details = task.details();
            List<ICraftingProvider> providers = new ArrayList<>();
            craftingService.getProviders(details).forEach(providers::add);
            List<ECOCraftingPatternBusBlockEntity> patternBuses = findPatternBuses(providers);
            if (taskRemaining < 2L) {
                continue;
            }

            KeyCounter expectedOutputs = new KeyCounter();
            KeyCounter expectedContainerItems = new KeyCounter();
            KeyCounter[] firstInputs = CraftingCpuHelper.extractPatternInputs(
                    details, job.inventory(), level, expectedOutputs, expectedContainerItems);
            if (firstInputs == null) {
                continue;
            }

            boolean firstInputsOwned = true;
            List<GenericStack> extraInputs = List.of();
            boolean extraInputsOwned = false;
            boolean providerAccepted = false;
            ECOBatchEnergyReservation energyReservation = null;
            try {
                var execution = ECOExtractedPatternExecution.create(
                        details, firstInputs, expectedOutputs, expectedContainerItems, level, true);
                if (!execution.fastPathEligible() || execution.key() == null) {
                    continue;
                }

                var selection = selectOffer(patternBuses, execution, taskRemaining, job.craftingId());
                if (selection == null) {
                    double powerPerCraft = CraftingCpuHelper.calculatePatternPower(firstInputs);
                    int externalBatchResult = ae2ltBatchBridge.tryPushBatch(
                            providers, details, firstInputs, job.inventory(), energyService,
                            powerPerCraft, taskRemaining);
                    if (externalBatchResult <= 0) {
                        externalBatchResult = megacellsBatchBridge.tryPushBatch(
                                providers, details, firstInputs, job.inventory(), energyService,
                                powerPerCraft, taskRemaining);
                    }
                    if (externalBatchResult > 0) {
                        energyService.extractAEPower(
                                powerPerCraft * externalBatchResult,
                                Actionable.MODULATE, PowerMultiplier.CONFIG);
                        var reusablePlan = ECOReusableCraftingPlan.of(
                                execution.inputItems(), execution.expectedContainerItems());
                        providerAccepted = true;
                        firstInputsOwned = false;
                        task.remaining(taskRemaining - externalBatchResult);
                        if (task.remaining() <= 0L) {
                            taskIterator.remove();
                        }
                        ECOBatchCraftingHelper.insertAll(
                                job.inventory(), reusablePlan.reusableInputs());
                        recordExpectedOutputs(job, execution, reusablePlan, externalBatchResult);
                        job.markDirty();
                        return true;
                    }

                    double bootstrapPower = CraftingCpuHelper.calculatePatternPower(firstInputs);
                    energyReservation = ECOBatchEnergyReservation.tryReserve(
                            energyService, bootstrapPower, false);
                    if (energyReservation == null) {
                        continue;
                    }

                    boolean accepted = false;
                    for (var patternBus : patternBuses) {
                        if (patternBus.pushPattern(execution, job.craftingId())) {
                            accepted = true;
                            break;
                        }
                    }
                    if (!accepted) {
                        continue;
                    }

                    providerAccepted = true;
                    firstInputsOwned = false;
                    energyReservation.commit();
                    energyReservation = null;

                    long remaining = taskRemaining - 1L;
                    task.remaining(remaining);
                    if (remaining <= 0L) {
                        taskIterator.remove();
                    }
                    try {
                        recordSingleExpectedOutputs(job, execution);
                    } catch (RuntimeException accountingFailure) {
                        AELog.warn("NeoECO external CPU accepted a FastPath cache-warmup craft but output "
                                + "accounting failed. %s", accountingFailure);
                    } finally {
                        job.markDirty();
                    }
                    return true;
                }

                var reusablePlan = ECOReusableCraftingPlan.of(
                        execution.inputItems(), execution.expectedContainerItems());
                long batchSize = Math.min(taskRemaining, selection.offer().maxBatchSize());
                batchSize = ECOBatchCraftingHelper.maxSafeBatchSize(
                        reusablePlan.consumedInputsPerCraft(), execution.expectedOutputs(),
                        reusablePlan.ordinaryRemainingPerCraft(), batchSize);
                batchSize = Math.min(batchSize, Integer.MAX_VALUE);
                if (batchSize < 2L) {
                    continue;
                }

                long availableExtra = ECOBatchCraftingHelper.maxCraftsFromInventory(
                        job.inventory(), reusablePlan.consumedInputsPerCraft(), batchSize - 1L);
                batchSize = Math.min(batchSize, availableExtra + 1L);
                if (batchSize < 2L) {
                    continue;
                }

                boolean virtualCrafting = selection.offer().worker().getCluster() != null
                        && selection.offer().worker().getCluster().getController() != null
                        && selection.offer().worker().getCluster().getController().isVirtualCraftingMode();
                double powerPerCraft = CraftingCpuHelper.calculatePatternPower(firstInputs);
                if (!virtualCrafting) {
                    int affordable = ECOBatchCraftingHelper.maxAffordableCrafts(
                            powerPerCraft,
                            (int) batchSize,
                            amount -> energyService.extractAEPower(
                                    amount, Actionable.SIMULATE,
                                    appeng.api.config.PowerMultiplier.CONFIG));
                    batchSize = Math.min(batchSize, affordable);
                    if (batchSize < 2L) {
                        continue;
                    }
                }

                double totalPower = virtualCrafting ? 0.0D : powerPerCraft * batchSize;
                energyReservation = ECOBatchEnergyReservation.tryReserve(
                        energyService, totalPower, virtualCrafting);
                if (energyReservation == null) {
                    continue;
                }

                extraInputs = reusablePlan.extraInputs(batchSize - 1L);
                ECOBatchCraftingHelper.extractExact(job.inventory(), extraInputs);
                extraInputsOwned = true;

                var request = new ECOBatchCraftingRequest(
                        details,
                        execution.key(),
                        batchSize,
                        execution.inputItems(),
                        execution.expectedOutputs(),
                        execution.expectedContainerItems(),
                        job.craftingId());
                if (!selection.patternBus().pushBatch(request, selection.offer())) {
                    continue;
                }

                providerAccepted = true;
                firstInputsOwned = false;
                extraInputsOwned = false;
                energyReservation.commit();
                energyReservation = null;

                long remaining = taskRemaining - batchSize;
                task.remaining(remaining);
                if (remaining <= 0L) {
                    taskIterator.remove();
                }
                ECOBatchCraftingHelper.insertAll(job.inventory(), reusablePlan.reusableInputs());
                try {
                    recordExpectedOutputs(job, execution, reusablePlan, batchSize);
                } catch (RuntimeException accountingFailure) {
                    // The worker already owns the inputs. The task count must stay consumed so this
                    // physical batch cannot be scheduled twice, even if UI/output accounting fails.
                    AELog.warn("NeoECO external CPU accepted a FastPath batch but output accounting failed. %s",
                            accountingFailure);
                } finally {
                    job.markDirty();
                }
                return true;
            } catch (RuntimeException e) {
                if (providerAccepted) {
                    // Ownership has crossed the provider boundary. Falling through to the CPU's
                    // ordinary path would duplicate the accepted physical work.
                    AELog.warn("NeoECO external CPU FastPath failed after provider acceptance; "
                            + "the accepted task remains consumed. %s", e);
                    job.markDirty();
                    return true;
                }
                AELog.warn("NeoECO external CPU FastPath dispatch failed; CPU-owned resources will be restored. %s", e);
            } finally {
                if (energyReservation != null) {
                    RuntimeException refundFailure = energyReservation.refundSafely();
                    if (refundFailure != null) {
                        AELog.warn("NeoECO external CPU FastPath energy refund failed. %s", refundFailure);
                    }
                }
                if (extraInputsOwned) {
                    ECOBatchCraftingHelper.insertAll(job.inventory(), extraInputs);
                }
                if (firstInputsOwned) {
                    CraftingCpuHelper.reinjectPatternInputs(job.inventory(), firstInputs);
                }
            }
        }
        return false;
    }

    private static List<ECOCraftingPatternBusBlockEntity> findPatternBuses(
            Iterable<ICraftingProvider> providers) {
        List<ECOCraftingPatternBusBlockEntity> result = new ArrayList<>();
        Set<ECOCraftingPatternBusBlockEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ICraftingProvider provider : providers) {
            if (provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                    && !provider.isBusy()
                    && seen.add(patternBus)) {
                result.add(patternBus);
            }
        }
        return result;
    }

    private static Selection selectOffer(
            List<ECOCraftingPatternBusBlockEntity> patternBuses,
            ECOExtractedPatternExecution execution,
            long requested,
            java.util.UUID craftingJobId) {
        Selection best = null;
        Set<Object> controllers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var patternBus : patternBuses) {
            Object controller = patternBus.getCraftingController();
            if (controller == null || !controllers.add(controller)) {
                continue;
            }
            var offer = patternBus.findBatchFastPathOffer(execution, requested, craftingJobId);
            if (offer != null && offer.maxBatchSize() >= 2L
                    && (best == null || offer.maxBatchSize() > best.offer().maxBatchSize())) {
                best = new Selection(patternBus, offer);
            }
        }
        return best;
    }

    private static void recordExpectedOutputs(
            ECOExternalCpuJobView job,
            ECOExtractedPatternExecution execution,
            ECOReusableCraftingPlan reusablePlan,
            long batchSize) {
        for (GenericStack output : execution.expectedOutputs()) {
            job.waitingFor().insert(
                    output.what(), saturatedMultiply(output.amount(), batchSize), Actionable.MODULATE);
        }
        for (GenericStack remaining : reusablePlan.batchRemaining(batchSize)) {
            job.waitingFor().insert(remaining.what(), remaining.amount(), Actionable.MODULATE);
            job.addContainerMaxItems(remaining.amount(), remaining.what().getType());
        }
    }

    private static void recordSingleExpectedOutputs(
            ECOExternalCpuJobView job,
            ECOExtractedPatternExecution execution) {
        for (GenericStack output : execution.expectedOutputs()) {
            job.waitingFor().insert(output.what(), output.amount(), Actionable.MODULATE);
        }
        for (GenericStack remaining : execution.expectedContainerItems()) {
            job.waitingFor().insert(remaining.what(), remaining.amount(), Actionable.MODULATE);
            job.addContainerMaxItems(remaining.amount(), remaining.what().getType());
        }
    }

    private static long saturatedMultiply(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) {
            return 0L;
        }
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private record Selection(
            ECOCraftingPatternBusBlockEntity patternBus,
            ECOCraftingPatternBusBlockEntity.BatchFastPathOffer offer) {
    }
}
