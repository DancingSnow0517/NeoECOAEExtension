package cn.dancingsnow.neoecoae.api.me;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingCPULogic {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    final ECOCraftingCPU cpu;

    /**
     * 当前合成任务。
     */
    @Getter
    private ExecutingCraftingJob job = null;
    /**
     * 库存。
     */
    @Getter
    private final ListCraftingInventory inventory = new ListCraftingInventory(ECOCraftingCPULogic.this::postChange);
    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    /**
     * 如果 CPU 正在尝试清空库存但无法完成，则为 true。
     */
    @Getter
    private boolean cantStoreItems = false;

    @Getter
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();

    @Getter
    private boolean markedForDeletion = false;

    private boolean batchingStatusChanges = false;
    private final Set<AEKey> batchedStatusChanges = new HashSet<>();
    private boolean batchedAnyStatusChange = false;
    private boolean batchedFullStatusChange = false;

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
    }

    public ICraftingSubmitResult trySubmitJob(
            IGrid grid, ICraftingPlan plan, IActionSource src, @Nullable ICraftingRequester requester) {
        // 已有任务在运行。
        if (this.job != null)
            return CraftingSubmitResult.CPU_BUSY;
        // 检查节点是否活跃。
        if (!cpu.isActive())
            return CraftingSubmitResult.CPU_OFFLINE;
        // 检查存储字节数。
        if (cpu.getAvailableStorage() < plan.bytes())
            return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty())
            AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        // 尝试提取所需物品。
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null)
            return CraftingSubmitResult.missingIngredient(missingIngredient);

        // 设置 CPU 链接与任务。
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);

        // 合成监视器暂不支持
        // cpu.updateOutput(plan.finalOutput());
        cpu.markDirty();

        // TODO: 发送监视器差异？

        notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        // 非独立任务需要为请求者创建另一个链接，两个链接都需要提交到缓存。
        if (requester != null) {
            var linkReq = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, false, true), requester);

            var craftingService = (CraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkReq);

            return CraftingSubmitResult.successful(linkReq);
        } else {
            return CraftingSubmitResult.successful(null);
        }
    }

    public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {
        // 未激活时不 tick。
        if (!cpu.isActive())
            return;
        cantStoreItems = false;
        // 无任务时只需尝试清空物品。
        if (this.job == null) {
            this.storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            } else {
                if (markedForDeletion) {
                    cpu.deactivate();
                }
            }
            return;
        }
        // 检查任务是否已被取消。
        if (job.link.isCanceled()) {
            cancel();
            return;
        }

        retryBufferedFinalOutput();
        if (job == null) {
            return;
        }

        // 暂停时不调度更多工作
        if (job.suspended) {
            return;
        }

        var remainingOperations = getOperationLimit();

        if (remainingOperations > 0) {
            do {
                var pushedPatterns = executeCrafting(remainingOperations, cc, eg, cpu.getLevel());

                if (pushedPatterns > 0) {
                    remainingOperations -= pushedPatterns;
                } else {
                    break;
                }
            } while (remainingOperations > 0);
        }
    }

    private void retryBufferedFinalOutput() {
        AEKey key = job.finalOutput.what();
        long buffered = inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
        if (buffered <= 0L) {
            return;
        }
        long accepted = deliverFinalOutput(key, buffered, Actionable.MODULATE);
        if (accepted <= 0L) {
            return;
        }
        inventory.extract(key, accepted, Actionable.MODULATE);
        job.timeTracker.decrementItems(accepted, key.getType());
        job.waitingFor.extract(key, accepted, Actionable.MODULATE);
        job.remainingAmount = Math.max(0L, job.remainingAmount - accepted);
        postChange(key);
        cpu.markDirty();
        if (job.remainingAmount <= 0L) {
            finishJob(true);
        }
    }

    private int getOperationLimit() {
        int baseLimit = Math.max(1, cpu.getCoProcessors() + 1);
        return Math.min(baseLimit, NEConfig.ecoCpuPushTickLimit);
    }

    /**
     * 尝试将 pattern 推送到可用接口中，即执行实际的合成操作。
     *
     * @return 成功推送的 pattern 数量。
     */
    public int executeCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        var job = this.job;
        if (job == null)
            return 0;

        var pushedPatterns = 0;

        beginStatusChangeBatch();
        try {
            var it = job.tasks.entrySet().iterator();
            taskLoop: while (it.hasNext()) {
                var task = it.next();
                if (task.getValue().value <= 0) {
                    postPatternOutputsChange(task.getKey());
                    it.remove();
                    continue;
                }

                var details = task.getKey();
                while (task.getValue().value > 0 && pushedPatterns < maxPatterns) {
                    List<ICraftingProvider> providers = collectAvailableProviders(craftingService, details);
                    if (providers.isEmpty()) {
                        continue taskLoop;
                    }

                    var expectedOutputs = new KeyCounter();
                    var expectedContainerItems = new KeyCounter();
                    @Nullable
                    var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                            details, inventory, level, expectedOutputs, expectedContainerItems);
                    if (craftingContainer == null) {
                        continue taskLoop;
                    }

                    ECOExtractedPatternExecution execution = ECOExtractedPatternExecution.create(
                            details, craftingContainer, expectedOutputs, expectedContainerItems, level);

                    var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
                    int batchResult = tryPushVerifiedFastPathBatch(
                            details,
                            execution,
                            craftingContainer,
                            providers,
                            energyService,
                            patternPower,
                            task.getValue().value,
                            maxPatterns - pushedPatterns);
                    if (batchResult > 0) {
                        pushedPatterns += batchResult;
                        task.getValue().value -= batchResult;
                        postPatternOutputsChange(details);
                        if (task.getValue().value <= 0) {
                            it.remove();
                            continue taskLoop;
                        }
                        if (pushedPatterns == maxPatterns) {
                            break taskLoop;
                        }
                        continue;
                    } else if (batchResult < 0) {
                        continue taskLoop;
                    }

                    boolean pushed = false;
                    for (ICraftingProvider provider : providers) {
                        if (provider.isBusy()) {
                            continue;
                        }

                        if (energyService.extractAEPower(patternPower, Actionable.SIMULATE,
                                PowerMultiplier.CONFIG) < patternPower - 0.01) {
                            break;
                        }

                        pushed = provider instanceof ECOCraftingPatternBusBlockEntity patternBus
                                ? patternBus.pushPattern(execution, job.link.getCraftingID())
                                : provider.pushPattern(details, craftingContainer);

                        if (!pushed) {
                            continue;
                        }

                        energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                        pushedPatterns++;
                        recordPushedPattern(execution, 1);

                        task.getValue().value--;
                        postPatternOutputsChange(details);
                        if (task.getValue().value <= 0) {
                            it.remove();
                            continue taskLoop;
                        }

                        if (pushedPatterns == maxPatterns) {
                            break taskLoop;
                        }

                        break;
                    }

                    if (!pushed) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
                        continue taskLoop;
                    }
                }
            }
        } finally {
            endStatusChangeBatchSafely();
        }

        return pushedPatterns;
    }

    private List<ICraftingProvider> collectAvailableProviders(CraftingService craftingService,
            IPatternDetails details) {
        List<ICraftingProvider> providers = new ArrayList<>();
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            if (!provider.isBusy()) {
                providers.add(provider);
            }
        }
        return providers;
    }

    private int tryPushVerifiedFastPathBatch(
            IPatternDetails details,
            ECOExtractedPatternExecution execution,
            KeyCounter[] firstCraftingContainer,
            List<ICraftingProvider> providers,
            IEnergyService energyService,
            double patternPower,
            long taskRemaining,
            int tickBudgetRemaining) {
        if (!canAttemptBatchFastPath(execution) || taskRemaining <= 1 || tickBudgetRemaining <= 1) {
            return 0;
        }

        int requested = (int) Math.min(
                Math.min(taskRemaining, tickBudgetRemaining),
                Math.min(NEConfig.ecoBatchFastPathLimit, NEConfig.ecoBatchFastPathTickLimit));
        ECOCraftingPatternBusBlockEntity selectedPatternBus = null;
        ECOCraftingPatternBusBlockEntity.BatchFastPathOffer selectedOffer = null;
        Set<ECOCraftingSystemBlockEntity> visitedControllers = new HashSet<>();
        for (ICraftingProvider provider : providers) {
            if (!(provider instanceof ECOCraftingPatternBusBlockEntity patternBus)) {
                continue;
            }
            ECOCraftingSystemBlockEntity controller = patternBus.getCraftingController();
            if (controller == null || !visitedControllers.add(controller)) {
                continue;
            }
            var offer = patternBus.findBatchFastPathOffer(execution, requested);
            if (offer != null && offer.maxBatchSize() > 1
                    && (selectedOffer == null || offer.maxBatchSize() > selectedOffer.maxBatchSize())) {
                selectedPatternBus = patternBus;
                selectedOffer = offer;
                if (offer.maxBatchSize() >= requested) {
                    break;
                }
            }
        }
        if (selectedPatternBus == null || selectedOffer == null) {
            return 0;
        }

        ECOCraftingSystemBlockEntity controller = selectedPatternBus.getCraftingController();
        if (controller == null) {
            return 0;
        }

        int batchSize = Math.min(requested, selectedOffer.maxBatchSize());
        batchSize = Math.min(batchSize, maxBatchSizeFromEnergy(energyService, patternPower, batchSize));
        batchSize = controller.getCraftingCoolantCraftLimit(5, controller.getEffectiveOverclockTimes(), batchSize);
        if (batchSize <= 1) {
            return 0;
        }

        int extraCrafts = batchSize - 1;
        int availableExtraCrafts = ECOBatchCraftingHelper.maxCraftsFromInventory(inventory, execution.inputItems(),
                extraCrafts);
        batchSize = Math.min(batchSize, availableExtraCrafts + 1);
        if (batchSize <= 1) {
            return 0;
        }

        var extraInputs = ECOBatchCraftingHelper.multiply(execution.inputItems(), batchSize - 1);
        boolean extraInputsExtracted = false;
        boolean ownershipTransferred = false;
        try {
            if (!ECOBatchCraftingHelper.canExtractExact(inventory, extraInputs)) {
                return 0;
            }
            double requiredPower = patternPower * batchSize;
            double simulatedPower = energyService.extractAEPower(
                requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG
            );
            if (!Double.isFinite(requiredPower)
                || Double.isNaN(simulatedPower)
                || simulatedPower < requiredPower - 0.01D) {
                return 0;
            }
            ECOBatchCraftingHelper.extractExact(inventory, extraInputs);
            extraInputsExtracted = true;
            var request = new ECOBatchCraftingRequest(
                    details,
                    execution.key(),
                    batchSize,
                    execution.inputItems(),
                    execution.expectedOutputs(),
                    execution.expectedContainerItems(),
                    job.link.getCraftingID());
            if (!selectedPatternBus.pushBatch(request, selectedOffer)) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, true);
                return -1;
            }
            // The worker owns every input from this point onward. Never reinject them into the CPU.
            ownershipTransferred = true;
            try {
                double chargedPower = energyService.extractAEPower(
                    requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG
                );
                if (Double.isNaN(chargedPower) || chargedPower < requiredPower - 0.01D) {
                    selectedOffer.worker().getFastPathCache().recordException();
                    LOGGER.error(
                        "ECO batch was accepted, but only {} of {} crafting energy was charged",
                        chargedPower,
                        requiredPower
                    );
                }
            } catch (RuntimeException e) {
                selectedOffer.worker().getFastPathCache().recordException();
                LOGGER.error("ECO batch was accepted, but its crafting energy could not be charged", e);
            }
            try {
                recordPushedPattern(execution, batchSize);
            } catch (RuntimeException e) {
                selectedOffer.worker().getFastPathCache().recordException();
                LOGGER.error("ECO batch was accepted, but its CPU accounting update failed", e);
            }
            return batchSize;
        } catch (RuntimeException e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (ownershipTransferred) {
                LOGGER.error("ECO batch failed after ownership transfer; accounting it as accepted", e);
                return batchSize;
            }
            rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraInputsExtracted);
            return -1;
        } catch (Error e) {
            selectedOffer.worker().getFastPathCache().recordException();
            if (!ownershipTransferred) {
                rollbackBatchInputs(inventory, firstCraftingContainer, extraInputs, true, extraInputsExtracted);
            }
            throw e;
        }
    }

    private void rollbackBatchInputs(
            ListCraftingInventory inventory,
            KeyCounter[] firstCraftingContainer,
            List<GenericStack> extraInputs,
            boolean firstInputsOwned,
            boolean extraInputsExtracted) {
        if (firstInputsOwned) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, firstCraftingContainer);
        }

        if (extraInputsExtracted) {
            ECOBatchCraftingHelper.insertAll(inventory, extraInputs);
        }
    }

    private boolean canAttemptBatchFastPath(ECOExtractedPatternExecution execution) {
        return execution.key() != null
                && execution.fastPathEligible()
                && NEConfig.ecoAe2FastPathEnabled
                && !NEConfig.postCraftingEvent;
    }

    private int maxBatchSizeFromEnergy(IEnergyService energyService, double patternPower, int requested) {
        return ECOBatchCraftingHelper.maxAffordableCrafts(
            patternPower,
            requested,
            totalPower -> energyService.extractAEPower(
                totalPower, Actionable.SIMULATE, PowerMultiplier.CONFIG
            )
        );
    }

    private void recordPushedPattern(ECOExtractedPatternExecution execution, int craftCount) {
        int multiplier = Math.max(1, craftCount);
        for (var expectedOutput : execution.expectedOutputs()) {
            job.waitingFor.insert(expectedOutput.what(), expectedOutput.amount() * multiplier, Actionable.MODULATE);
        }
        postGenericStackKeysChange(execution.expectedOutputs());

        for (var expectedContainerItem : execution.expectedContainerItems()) {
            job.waitingFor.insert(
                    expectedContainerItem.what(), expectedContainerItem.amount() * multiplier, Actionable.MODULATE);
            job.timeTracker.addMaxItems(
                    expectedContainerItem.amount() * multiplier,
                    expectedContainerItem.what().getType());
        }
        postGenericStackKeysChange(execution.expectedContainerItems());

        cpu.markDirty();
    }

    /**
     * 由 CraftingService 以 Integer.MAX_VALUE 优先级调用，用于注入正在等待的物品。
     *
     * @return 已消耗数量。
     */
    public long insert(AEKey what, long amount, Actionable type) {
        // 任务完成时也停止接收物品，防止在 storeItems 推出物品时重新插入
        if (what == null || job == null)
            return 0;

        // 只接收正在等待的物品。
        var waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0;
        }

        // 确保不接收超出等待数量的物品。
        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (type == Actionable.MODULATE && !what.matches(job.finalOutput)) {
            job.timeTracker.decrementItems(amount, what.getType());
            job.waitingFor.extract(what, amount, Actionable.MODULATE);
            cpu.markDirty();
        }

        long inserted = amount;
        if (what.matches(job.finalOutput)) {
            inserted = deliverFinalOutput(what, amount, type);

            // 注意：我们忽略任何余数（如果没有请求者，余数可能是整个输入），
            // 我们已经将物品标记为已完成，甚至可能完成整个任务。

            // 这意味着即使某些物品实际未被插入，任务也可能被标记为完成。
            // 在某些情况下，最终输出的一小部分反复插入失败可能会阻止某些配方被推送。
            // TODO: 考虑修复此问题，也许可以使用网络监视器检查实际插入量。
            // TODO: 另一种解决方案是等待所有配方被推送后再取消任务。

            if (type == Actionable.MODULATE) {
                // 更新计数和显示的 CPU 堆栈，如果可能则完成任务。
                job.timeTracker.decrementItems(inserted, what.getType());
                job.waitingFor.extract(what, inserted, Actionable.MODULATE);
                long remainder = amount - inserted;
                if (remainder > 0L) {
                    inventory.insert(what, remainder, Actionable.MODULATE);
                }
                postChange(what);
                job.remainingAmount = Math.max(0, job.remainingAmount - inserted);
                cpu.markDirty();

                if (job.remainingAmount <= 0) {
                    finishJob(true);
                }
            }
        } else {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, amount, Actionable.MODULATE);
            }
        }

        return inserted;
    }

    private long deliverFinalOutput(AEKey what, long amount, Actionable mode) {
        if (job == null || amount <= 0L) {
            return 0L;
        }
        if (!job.link.isStandalone()) {
            return job.link.insert(what, amount, mode);
        }
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return 0L;
        }
        return grid.getStorageService().getInventory().insert(what, amount, mode, cpu.getActionSource());
    }

    /**
     * 完成当前合成任务。
     *
     * @param success 任务完成则为 true，取消则为 false。
     */
    private void finishJob(boolean success) {
        if (success) {
            job.link.markDone();
        } else {
            job.link.cancel();
        }

        // TODO: 记录日志

        // 清空等待列表并发送所有相关变更通知。
        job.waitingFor.clear();
        // 通知已打开菜单关于已取消的调度任务。
        for (var entry : job.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(
                job, success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        // 结束任务。
        this.job = null;

        // 存储所有剩余物品。
        this.storeItems();
    }

    /**
     * 取消当前合成任务。
     */
    public void cancel() {
        // 没有可取消的任务 :P
        if (job == null)
            return;

        UUID craftingJobId = job.link.getCraftingID();
        finishJob(false);
        recoverInflightWorkerInputs(craftingJobId);
    }

    private void recoverInflightWorkerInputs(UUID craftingJobId) {
        IGrid grid = cpu.getGrid();
        if (grid == null) {
            return;
        }
        var storage = grid.getStorageService().getInventory();
        for (ECOCraftingPatternBusBlockEntity patternBus : grid.getMachines(ECOCraftingPatternBusBlockEntity.class)) {
            patternBus.recoverJobToNetwork(craftingJobId, storage);
        }
    }

    /**
     * 尝试将所有本地存储的物品转存回存储网络。
     */
    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        // 无事可做则快速返回。
        if (this.inventory.list.isEmpty())
            return;

        var g = cpu.getGrid();
        if (g == null)
            return;

        var storage = g.getStorageService().getInventory();

        for (var entry : this.inventory.list) {
            this.postChange(entry.getKey());
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE,
                    cpu.getActionSource());

            // 网络无法接收全部物品，即存储空间不足或已满
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();

        cpu.markDirty();
    }

    private void postChange(@Nullable AEKey what) {
        if (batchingStatusChanges) {
            batchedAnyStatusChange = true;
            if (what == null) {
                batchedFullStatusChange = true;
            } else {
                batchedStatusChanges.add(what);
            }
            return;
        }

        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (var listener : listeners) {
            listener.accept(what);
        }
    }

    private void beginStatusChangeBatch() {
        batchingStatusChanges = true;
        batchedStatusChanges.clear();
        batchedAnyStatusChange = false;
        batchedFullStatusChange = false;
    }

    private void endStatusChangeBatch() {
        batchingStatusChanges = false;

        if (!batchedAnyStatusChange) {
            return;
        }

        lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        if (batchedFullStatusChange) {
            batchedStatusChanges.clear();
            batchedAnyStatusChange = false;
            batchedFullStatusChange = false;

            for (var listener : listeners) {
                listener.accept(null);
            }
            return;
        }

        var changedKeys = List.copyOf(batchedStatusChanges);
        batchedStatusChanges.clear();
        batchedAnyStatusChange = false;
        batchedFullStatusChange = false;

        for (AEKey key : changedKeys) {
            for (var listener : listeners) {
                listener.accept(key);
            }
        }
    }

    private void endStatusChangeBatchSafely() {
        try {
            endStatusChangeBatch();
        } catch (RuntimeException e) {
            batchingStatusChanges = false;
            batchedStatusChanges.clear();
            batchedAnyStatusChange = false;
            batchedFullStatusChange = false;
            throw e;
        } catch (Error e) {
            batchingStatusChanges = false;
            batchedStatusChanges.clear();
            batchedAnyStatusChange = false;
            batchedFullStatusChange = false;
            throw e;
        }
    }

    private void postPatternOutputsChange(IPatternDetails details) {
        for (var output : details.getOutputs()) {
            postChange(output.what());
        }
    }

    private void postGenericStackKeysChange(List<GenericStack> stacks) {
        for (var stack : stacks) {
            postChange(stack.what());
        }
    }

    public boolean hasJob() {
        return this.job != null;
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
    }

    public long getRemainingJobOutputAmount() {
        return this.job != null ? this.job.remainingAmount : 0L;
    }

    public ElapsedTimeTracker getElapsedTimeTracker() {
        if (this.job != null) {
            return this.job.timeTracker;
        } else {
            return new ElapsedTimeTracker();
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        this.inventory.readFromNBT(data.getList("inventory", 10), registries);
        if (data.contains("job")) {
            this.job = new ExecutingCraftingJob(data.getCompound("job"), registries, this::postChange, this);
            if (this.job.finalOutput == null) {
                finishJob(false);
            }
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT(registries));
        if (this.job != null) {
            data.put("job", this.job.writeToNBT(registries));
        }
    }

    public ICraftingLink getLastLink() {
        if (this.job != null) {
            return this.job.link;
        }
        return null;
    }

    /**
     * 注册一个监听器，当存储物品、等待物品或待处理输出发生变化时接收通知。
     * 仅供菜单使用。务必通过 {@link #removeListener} 来移除。
     */
    public void addListener(Consumer<AEKey> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<AEKey> listener) {
        listeners.remove(listener);
    }

    public long getStored(AEKey template) {
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public long getWaitingFor(AEKey template) {
        if (this.job != null) {
            return this.job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
        }
        return 0;
    }

    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        if (this.job != null) {
            for (var entry : this.job.waitingFor.list) {
                waitingFor.add(entry.getKey());
            }
        }
    }

    public long getPendingOutputs(AEKey template) {
        long count = 0;
        if (this.job != null) {
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    if (template.matches(output)) {
                        count += output.amount() * t.getValue().value;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 供菜单使用，收集所有类型的存储物品。
     */
    public void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job != null) {
            out.addAll(job.waitingFor.list);
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    out.add(output.what(), output.amount() * t.getValue().value);
                }
            }
        }
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        var playerId = job.playerId;
        if (playerId == null) {
            return;
        }

        var server = cpu.getLevel().getServer();
        var connectedPlayer = IPlayerRegistry.getConnected(server, playerId);
        if (connectedPlayer != null) {
            var jobId = job.link.getCraftingID();
            ClientboundPacket message = new CraftingJobStatusPacket(
                    jobId, job.finalOutput.what(), job.finalOutput.amount(), job.remainingAmount, status);
            connectedPlayer.connection.send(message);
        }
    }

    public void markForDeletion() {
        this.markedForDeletion = true;
    }
}
