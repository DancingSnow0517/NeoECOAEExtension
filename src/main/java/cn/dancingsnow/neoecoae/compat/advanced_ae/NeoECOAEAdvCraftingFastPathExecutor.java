package cn.dancingsnow.neoecoae.compat.advanced_ae;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor.AdvancedAeCraftingJobAccessor;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor.AdvancedAeElapsedTimeTrackerInvoker;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor.AdvancedAeTaskProgressAccessor;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Batch bridge for AdvancedAE's dynamically-created quantum-computer CPUs. */
public final class NeoECOAEAdvCraftingFastPathExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    private NeoECOAEAdvCraftingFastPathExecutor() {}

    public static int execute(
            AdvCraftingCPU cpu,
            @Nullable ExecutingCraftingJob job,
            ListCraftingInventory inventory,
            int maxPatterns,
            appeng.me.service.CraftingService craftingService,
            IEnergyService energyService,
            @Nullable Level level) {
        if (job == null
                || level == null
                || maxPatterns <= 0
                || !NEConfig.ecoAe2FastPathEnabled
                || NEConfig.postCraftingEvent) {
            return 0;
        }

        int pushedOperations = 0;
        if (!(job instanceof AdvancedAeCraftingJobAccessor jobAccess)) {
            return 0;
        }
        Iterator<? extends java.util.Map.Entry<IPatternDetails, ?>> tasks =
                jobAccess.neoecoae$getTasks().entrySet().iterator();
        while (tasks.hasNext() && pushedOperations < maxPatterns) {
            var taskEntry = tasks.next();
            AdvancedAeTaskProgressAccessor task =
                    (AdvancedAeTaskProgressAccessor) taskEntry.getValue();
            if (task.neoecoae$getValue() <= 0L) {
                tasks.remove();
                continue;
            }
            // A one-craft task is left to AdvancedAE's native path. FastPath is a batch optimization only.
            if (task.neoecoae$getValue() <= 1L) {
                continue;
            }

            IPatternDetails details = taskEntry.getKey();
            KeyCounter expectedOutputs = new KeyCounter();
            KeyCounter expectedContainerItems = new KeyCounter();
            KeyCounter[] craftingContainer = CraftingCpuHelper.extractPatternInputs(
                    details, inventory, level, expectedOutputs, expectedContainerItems);
            if (craftingContainer == null) {
                continue;
            }

            boolean ownershipTransferred = false;
            boolean additionalInputsExtracted = false;
            int acceptedBatchSize = 0;
            EnergyReservation energyReservation = null;
            List<GenericStack> additionalInputs = List.of();
            try {
                ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.create(
                        details, craftingContainer, expectedOutputs, expectedContainerItems, level);
                if (!execution.canUseFastPath() || execution.arithmeticBatchLimit() <= 1L) {
                    reinject(inventory, craftingContainer);
                    continue;
                }

                VirtualSelectedOffer virtualSelected = findVirtualOffer(craftingService, execution);
                int virtualResult = virtualSelected == null ? 0 : executeVirtualBatch(
                                cpu,
                                jobAccess,
                                task,
                                inventory,
                                craftingContainer,
                                execution,
                                virtualSelected);
                if (virtualResult > 0) {
                    pushedOperations++;
                    continue;
                }

                int requested = (int) Math.min(
                        Math.min(task.neoecoae$getValue(), execution.arithmeticBatchLimit()), Integer.MAX_VALUE);
                if (requested <= 1) {
                    reinject(inventory, craftingContainer);
                    continue;
                }

                SelectedOffer selected = findOffer(craftingService, execution, requested);
                if (selected == null) {
                    reinject(inventory, craftingContainer);
                    continue;
                }

                ECOCraftingSystemBlockEntity controller = selected.bus().getCraftingController();
                ECOVerifiedFastPathRecipe recipe = selected.offer().recipe();
                if (controller == null || recipe == null || !recipe.isVerifiedFor(execution)) {
                    reinject(inventory, craftingContainer);
                    continue;
                }

                int batchSize = Math.min(requested, selected.offer().maxBatchSize());
                batchSize = (int) Math.min(batchSize, recipe.arithmeticBatchLimit());
                boolean flatRatePower = controller.isFullVirtualCraftingMode();
                double patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
                if (!flatRatePower) {
                    batchSize = Math.min(
                            batchSize,
                            ECOBatchCraftingHelper.maxAffordableCrafts(
                                    patternPower,
                                    batchSize,
                                    totalPower -> energyService.extractAEPower(
                                            totalPower,
                                            Actionable.SIMULATE,
                                            PowerMultiplier.CONFIG)));
                }
                batchSize = Math.min(
                        batchSize,
                        Math.max(0, controller.getCraftingCoolantCraftLimit(
                                5, controller.getEffectiveOverclockTimes(), batchSize)));

                if (recipe.reusableStateModel() == null) {
                    int availableAdditional = ECOBatchCraftingHelper.maxCraftsFromInventory(
                            inventory, execution.inputItems(), Math.max(0, batchSize - 1));
                    batchSize = Math.min(batchSize, availableAdditional + 1);
                } else {
                    batchSize = (int) Math.min(
                            (long) batchSize,
                            ECOBatchCraftingHelper.maxBatchSizeFromAdditionalInputs(
                                    inventory, (long) batchSize, recipe::additionalInputs));
                }
                if (batchSize <= 1) {
                    reinject(inventory, craftingContainer);
                    continue;
                }

                if (!flatRatePower) {
                    energyReservation = reserveEnergy(energyService, patternPower * batchSize);
                    if (energyReservation == null) {
                        reinject(inventory, craftingContainer);
                        continue;
                    }
                }

                // Materialize all worker-owned totals before the provider takes ownership. The later accounting
                // step then cannot fail while the batch is already in flight.
                List<GenericStack> outputTotal = ECOBatchCraftingHelper.multiply(
                        recipe.outputsPerCraft(), batchSize);
                List<GenericStack> remainderTotal = recipe.batchRemainders(batchSize);
                additionalInputs = recipe.additionalInputs(batchSize);
                if (!ECOBatchCraftingHelper.extractExact(inventory, additionalInputs)) {
                    refund(energyReservation);
                    reinject(inventory, craftingContainer);
                    continue;
                }
                additionalInputsExtracted = true;

                ECOVerifiedFastPathExecution verified = recipe.withBatch(
                        batchSize, jobAccess.neoecoae$getLink().getCraftingID());
                if (verified == null || !selected.bus().acceptVerifiedBatch(verified, selected.offer())) {
                    rollback(inventory, craftingContainer, additionalInputs, additionalInputsExtracted);
                    refund(energyReservation);
                    continue;
                }
                ownershipTransferred = true;
                acceptedBatchSize = batchSize;
                if (energyReservation != null) energyReservation.commit();
                recordAcceptedBatch(cpu, jobAccess, task, outputTotal, remainderTotal, batchSize);
                pushedOperations++;
            } catch (RuntimeException failure) {
                if (ownershipTransferred) {
                    // The worker owns the physical inputs. Keep the task from being dispatched twice and leave the
                    // job visible for the native CPU's normal completion handling.
                    task.neoecoae$setValue(
                            Math.max(0L, task.neoecoae$getValue() - Math.max(1, acceptedBatchSize)));
                    pushedOperations++;
                    LOGGER.error("AdvancedAE FastPath batch was accepted but post-acceptance accounting failed", failure);
                } else {
                    rollback(inventory, craftingContainer, additionalInputs, additionalInputsExtracted);
                    refund(energyReservation);
                    LOGGER.debug("AdvancedAE FastPath batch was rejected; using native crafting on the next pass", failure);
                }
            }
        }
        return pushedOperations;
    }

    @Nullable
    private static VirtualSelectedOffer findVirtualOffer(
            appeng.me.service.CraftingService craftingService,
            ECOExtractedPatternExecution execution) {
        for (ICraftingProvider provider : craftingService.getProviders(execution.details())) {
            if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus) || provider.isBusy()) {
                continue;
            }
            var offer = patternBus.findVirtualFastPathOffer(execution);
            if (offer != null && offer.recipe() != null) {
                return new VirtualSelectedOffer(patternBus, offer);
            }
        }
        return null;
    }

    private static int executeVirtualBatch(
            AdvCraftingCPU cpu,
            AdvancedAeCraftingJobAccessor job,
            AdvancedAeTaskProgressAccessor task,
            ListCraftingInventory inventory,
            KeyCounter[] craftingContainer,
            ECOExtractedPatternExecution execution,
            VirtualSelectedOffer selected) {
        ECOVerifiedFastPathRecipe recipe = selected.offer().recipe();
        if (!recipe.isVerifiedFor(execution)) {
            return 0;
        }

        boolean ownershipTransferred = false;
        boolean additionalInputsExtracted = false;
        long acceptedCraftCount = 0L;
        List<GenericStack> additionalInputs = List.of();
        try {
            long requested = Math.min(task.neoecoae$getValue(), recipe.arithmeticBatchLimit());
            if (requested <= 1L) {
                return 0;
            }

            long craftCount;
            if (recipe.reusableStateModel() == null) {
                long availableAdditional = ECOBatchCraftingHelper.maxCraftsFromInventory(
                        inventory, execution.inputItems(), requested - 1L);
                craftCount = Math.min(requested, availableAdditional + 1L);
            } else {
                craftCount = ECOBatchCraftingHelper.maxBatchSizeFromAdditionalInputs(
                        inventory,
                        requested,
                        recipe::additionalInputs);
            }
            if (craftCount <= 1L) {
                return 0;
            }

            List<GenericStack> outputTotal = ECOBatchCraftingHelper.multiply(
                    recipe.outputsPerCraft(), craftCount);
            List<GenericStack> remainderTotal = recipe.batchRemainders(craftCount);
            additionalInputs = recipe.additionalInputs(craftCount);
            if (!ECOBatchCraftingHelper.extractExact(inventory, additionalInputs)) {
                return 0;
            }
            additionalInputsExtracted = true;

            ECOVerifiedVirtualExecution verified = recipe.withVirtualBatch(
                    craftCount,
                    job.neoecoae$getLink().getCraftingID());
            if (verified == null || !selected.bus().pushVirtualBatch(verified, selected.offer())) {
                restoreAdditionalInputs(inventory, additionalInputs, additionalInputsExtracted);
                return 0;
            }
            ownershipTransferred = true;
            acceptedCraftCount = craftCount;
            recordAcceptedBatch(cpu, job, task, outputTotal, remainderTotal, craftCount);
            return 1;
        } catch (RuntimeException failure) {
            if (ownershipTransferred) {
                // The virtual worker owns the complete long batch. Prevent a second dispatch if accounting fails.
                task.neoecoae$setValue(
                        Math.max(0L, task.neoecoae$getValue() - Math.max(1L, acceptedCraftCount)));
                LOGGER.error(
                        "AdvancedAE virtual FastPath batch was accepted but post-acceptance accounting failed",
                        failure);
                return 1;
            } else {
                restoreAdditionalInputs(inventory, additionalInputs, additionalInputsExtracted);
                LOGGER.debug(
                        "AdvancedAE virtual FastPath batch was rejected; using native crafting on the next pass",
                        failure);
            }
            return 0;
        }
    }

    @Nullable
    private static SelectedOffer findOffer(
            appeng.me.service.CraftingService craftingService,
            ECOExtractedPatternExecution execution,
            int requested) {
        ECOCraftingPatternBusBlockEntity selectedBus = null;
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer selectedOffer = null;
        for (ICraftingProvider provider : craftingService.getProviders(execution.details())) {
            if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus) || provider.isBusy()) {
                continue;
            }
            var controller = patternBus.getCraftingController();
            if (controller == null) {
                continue;
            }
            var offer = patternBus.findBatchFastPathOffer(execution, requested);
            if (offer != null
                    && offer.maxBatchSize() > 1
                    && (selectedOffer == null || offer.maxBatchSize() > selectedOffer.maxBatchSize())) {
                selectedBus = patternBus;
                selectedOffer = offer;
                if (offer.maxBatchSize() >= requested) {
                    break;
                }
            }
        }
        return selectedBus == null || selectedOffer == null ? null : new SelectedOffer(selectedBus, selectedOffer);
    }

    private static void recordAcceptedBatch(
            AdvCraftingCPU cpu,
            AdvancedAeCraftingJobAccessor job,
            AdvancedAeTaskProgressAccessor task,
            List<GenericStack> outputs,
            List<GenericStack> remainders,
            long batchSize) {
        for (GenericStack output : outputs) {
            job.neoecoae$getWaitingFor().insert(output.what(), output.amount(), Actionable.MODULATE);
        }
        for (GenericStack remainder : remainders) {
            job.neoecoae$getWaitingFor().insert(remainder.what(), remainder.amount(), Actionable.MODULATE);
            ((AdvancedAeElapsedTimeTrackerInvoker) job.neoecoae$getTimeTracker())
                    .neoecoae$addMaxItems(remainder.amount(), remainder.what().getType());
        }
        task.neoecoae$setValue(task.neoecoae$getValue() - batchSize);
        cpu.markDirty();
    }

    @Nullable
    private static EnergyReservation reserveEnergy(IEnergyService energyService, double requiredPower) {
        if (requiredPower == 0.0D) return new EnergyReservation(energyService, 0.0D);
        if (!Double.isFinite(requiredPower) || requiredPower < 0.0D) return null;
        try {
            double charged = energyService.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (!Double.isFinite(charged)
                    || charged < requiredPower - 0.01D
                    || charged > requiredPower + 0.01D) {
                if (Double.isFinite(charged) && charged > 0.0D) {
                    refundEnergy(energyService, charged);
                }
                LOGGER.error("AdvancedAE FastPath energy reservation failed: charged {} of {}", charged, requiredPower);
                return null;
            }
            return new EnergyReservation(energyService, charged);
        } catch (RuntimeException failure) {
            LOGGER.error("AdvancedAE FastPath energy reservation failed", failure);
            return null;
        }
    }

    private static void refund(@Nullable EnergyReservation reservation) {
        if (reservation != null) reservation.refund();
    }

    private static void refundEnergy(IEnergyService energyService, double amount) {
        if (amount <= 0.0D) return;
        try {
            double overflow = energyService.injectPower(amount, Actionable.MODULATE);
            if (!Double.isFinite(overflow) || overflow > 0.01D) {
                LOGGER.error("AdvancedAE FastPath energy refund was incomplete: overflow {} of {}", overflow, amount);
            }
        } catch (RuntimeException failure) {
            LOGGER.error("AdvancedAE FastPath energy refund failed for {} energy", amount, failure);
        }
    }

    private static final class EnergyReservation {
        private final IEnergyService energyService;
        private final double amount;
        private boolean settled;

        private EnergyReservation(IEnergyService energyService, double amount) {
            this.energyService = energyService;
            this.amount = amount;
        }

        private void commit() {
            settled = true;
        }

        private void refund() {
            if (!settled) {
                settled = true;
                refundEnergy(energyService, amount);
            }
        }
    }

    private static void reinject(ListCraftingInventory inventory, KeyCounter[] craftingContainer) {
        if (craftingContainer != null) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
        }
    }

    private static void rollback(
            ListCraftingInventory inventory,
            KeyCounter[] craftingContainer,
            List<GenericStack> additionalInputs,
            boolean additionalInputsExtracted) {
        reinject(inventory, craftingContainer);
        if (additionalInputsExtracted) {
            ECOBatchCraftingHelper.insertAll(inventory, additionalInputs);
        }
    }

    private static void restoreAdditionalInputs(
            ListCraftingInventory inventory,
            List<GenericStack> additionalInputs,
            boolean additionalInputsExtracted) {
        if (additionalInputsExtracted) {
            ECOBatchCraftingHelper.insertAll(inventory, additionalInputs);
        }
    }

    private record SelectedOffer(
            ECOCraftingPatternBusBlockEntity bus,
            ECOCraftingPatternBusBlockEntity.BatchFastPathOffer offer) {}

    private record VirtualSelectedOffer(
            ECOCraftingPatternBusBlockEntity bus,
            ECOCraftingPatternBusBlockEntity.VirtualFastPathOffer offer) {}
}
