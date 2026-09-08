package cn.dancingsnow.neoecoae.api.me;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.config.PowerMultiplier;
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
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingExecutor;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessBatchProviderBridge;
import cn.dancingsnow.neoecoae.compat.useless.ECOUselessDynamicOutputBridge;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOSingleCraftingExecutor;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;

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

    private boolean deliveringFinalOutput;
    private final ECOProviderCursor providerCursor = new ECOProviderCursor();
    private final ECOCraftingDispatchStrategy dispatchStrategy = new ECOCraftingDispatchStrategy();
    // Per-call result, consumed by tickCraftingLogic after each executeCrafting invocation.
    private int normalPushProbesThisPass;
    private int lastAcceptedNormalPushes;
    private static final int MIN_NORMAL_PROBES_PER_TICK = 64;
    // Shared across every pass of tickCraftingLogic; -1 denotes a standalone executeCrafting call.
    private int remainingNormalProbes = -1;
    private IPatternDetails resumeDispatchPattern;

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

        var executionPlan = ECOPlanningResultRegistry.resolveExecutionPlan(plan);

        // 尝试提取所需物品。
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null) {
            return CraftingSubmitResult.missingIngredient(missingIngredient);
        }

        // 设置 CPU 链接与任务。
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(plan, executionPlan, this::postChange, linkCpu, playerId);
        providerCursor.clear();
        resumeDispatchPattern = null;
        // A newly submitted job already has pending pattern outputs even when its initial inventory is empty.
        // Publish those keys now; otherwise the status table stays empty until the first machine event, and AE2
        // disables the cancel button because it derives that button from the visible status entries.
        var initialStatusItems = new KeyCounter();
        getAllItems(initialStatusItems);
        for (var entry : initialStatusItems) postChange(entry.getKey());

        // 合成监视器暂不支持
        // cpu.updateOutput(plan.finalOutput());
        markCpuDirty();

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
        if (job != null && ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), job.link.getCraftingID())) {
            cancel();
            return;
        }
        // 未激活时不 tick。
        if (!cpu.isActive()) {
            return;
        }
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

        deliverStoredFinalOutput();
        if (job == null || job.suspended) {
            return;
        }
        Level level = cpu.getLevel();
        if (level == null) {
            return;
        }

        int operationLimit = dispatchStrategy.beginTick(cpu.getCoProcessors(), NEConfig.ecoCpuPushTickLimit);
        int acceptedNormalPushes = 0;
        // Thunderbolt wraps this exact executeCrafting invocation in tickCraftingLogic.
        // FastPath batches are bounded by live worker capacity, materials and power, not the slow-path budget.
        remainingNormalProbes = Math.max(MIN_NORMAL_PROBES_PER_TICK, operationLimit);
        try {
            while (job != null) {
                // Reset pass results only. The remaining tick probe budget is never reset here.
                normalPushProbesThisPass = 0;
                lastAcceptedNormalPushes = 0;
                int pushed = executeCrafting(operationLimit, cc, eg, level);
                remainingNormalProbes = Math.max(0, remainingNormalProbes - normalPushProbesThisPass);
                operationLimit = Math.max(0, operationLimit - lastAcceptedNormalPushes);
                acceptedNormalPushes += lastAcceptedNormalPushes;
                if (pushed == 0) break;
            }
        } finally {
            remainingNormalProbes = -1;
        }
        // Match the rolling three-tick accounting for ordinary pushes. Verified ECO batches are bounded
        // by the live provider capacity and deliberately do not consume this operation window.
        dispatchStrategy.finishTick(acceptedNormalPushes);
    }

    /** Retry delivery from the same physical inventory used for all recipe inputs. */
    private void deliverStoredFinalOutput() {
        var current = job;
        if (current == null) return;
        AEKey key = current.finalOutput.what();
        PlannerAmount reserve = PlannerAmount.ZERO;
        var remainingTasks = current.remainingTaskCounts();
        for (var task : current.tasks.entrySet()) {
            reserve = reserve.add(ECOPhaseScheduler.growingPatternFeedbackReserveExact(
                task.getKey(), task.getValue().value, key));
        }
        if (current.executionRuntime != null) {
            reserve = reserve.max(PlannerAmount.of(current.executionRuntime.reservedInputAmount(key, remainingTasks)));
        }
        // Keep returned feedback available for the next growth wave before delivering any surplus.
        PlannerAmount deliverable = PlannerAmount.of(inventory.list.get(key))
            .subtract(reserve).max(PlannerAmount.ZERO);
        long amount = deliverable.min(PlannerAmount.of(Math.max(0L, current.remainingAmount))).longValueExact();
        if (amount > 0L) {
            long inserted;
            try {
                deliveringFinalOutput = true;
                if (current.link.isStandalone()) {
                    var grid = cpu.getGrid();
                    inserted = grid == null ? 0L : grid.getStorageService().getInventory()
                        .insert(key, amount, Actionable.MODULATE, cpu.getActionSource());
                } else {
                    inserted = current.link.insert(key, amount, Actionable.MODULATE);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Final output delivery failed; items remain in the CPU inventory", e);
                return;
            } finally {
                deliveringFinalOutput = false;
            }
            inventory.extract(key, inserted, Actionable.MODULATE);
            current.remainingAmount -= inserted;
            markCpuDirty();
        }
        if (current.remainingAmount <= 0L && current.waitingFor.list.isEmpty()
                && current.tasks.values().stream().noneMatch(task -> task.value > 0L)
                && (current.executionRuntime == null || current.executionRuntime.isComplete(remainingTasks))) finishJob(true);
    }

    static int calculateOperationLimit(int coProcessors, int configuredLimit) {
        long baseLimit = (long) Math.max(0, coProcessors) + 1L;
        long safeConfiguredLimit = Math.min(
            (long) NEConfig.MAX_ECO_CPU_PUSH_TICK_LIMIT,
            Math.max(0L, configuredLimit)
        );
        return (int) Math.min(Integer.MAX_VALUE, Math.min(baseLimit, safeConfiguredLimit));
    }

    private Iterable<ICraftingProvider> collectAvailableProviders(CraftingService craftingService,
            IPatternDetails details) {
        // Binary Mixin contract: Thunderbolt 1.0.6 wraps this exact invocation in this method.
        // Keep exactly one getProviders call here; a forwarding stub in the CPU is not sufficient.
        return craftingService.getProviders(details);
    }

    /**
     * 尝试将 pattern 推送到可用接口中，即执行实际的合成操作。
     *
     * @param maxPatterns remaining accepted ordinary pushes; verified batches do not consume this budget
     * @return 成功推送的 pattern 数量。
     */
    public int executeCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        normalPushProbesThisPass = 0;
        lastAcceptedNormalPushes = 0;
        var current = job;
        if (current == null) return 0;
        providerCursor.beginPass(craftingService, TickHandler.instance().getCurrentTick());
        int ordinaryLimit = Math.max(0, maxPatterns);
        // Direct callers get a bounded standalone pass. CPU ticks supply the shared remaining budget.
        int probeLimit = remainingNormalProbes >= 0
            ? remainingNormalProbes : Math.max(MIN_NORMAL_PROBES_PER_TICK, ordinaryLimit);
        var remainingTasks = current.remainingTaskCounts();
        var candidates = current.executionRuntime == null
            ? nativeDispatchCandidates(current)
            : current.executionRuntime.candidates(remainingTasks);
        int start = 0;
        if (resumeDispatchPattern != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).pattern().equals(resumeDispatchPattern)) {
                    start = i;
                    break;
                }
            }
        }
        Set<Integer> blockedOrderedPhases = new HashSet<>();
        for (int offset = 0; offset < candidates.size(); offset++) {
            int candidateIndex = (start + offset) % candidates.size();
            var candidate = candidates.get(candidateIndex);
            if (blockedOrderedPhases.contains(candidate.phaseIndex())) continue;
            var progress = current.tasks.get(candidate.pattern());
            if (progress == null || progress.value <= 0L) {
                providerCursor.forget(candidate.pattern());
                continue;
            }
            long allowedCount = Math.min(candidate.maxDispatchCount(), progress.value);
            if (allowedCount <= 0L) continue;
            var pattern = candidate.pattern();
            // The explicit execution runtime owns phase/cycle gating. The growth barrier remains the fallback
            // policy for legacy jobs that have no bound ECO plan.
            if (current.executionRuntime == null && !current.canDispatchAfterGrowth(pattern)) {
                continue;
            }
            // Skip input resolution when no eligible provider is ready for a dispatch.
            var providers = providerCursor.availableProviders(
                pattern, () -> collectAvailableProviders(craftingService, pattern),
                providerCandidate -> {
                    boolean eligible = (ordinaryLimit > 0 && normalPushProbesThisPass < probeLimit)
                    || providerCandidate instanceof ECOBatchCapacityProvider
                    || ECOUselessBatchProviderBridge.supports(providerCandidate);
                    return eligible;
                });
            if (providers.isEmpty()) {
                if (candidate.blocksOrderedPhase()) blockedOrderedPhases.add(candidate.phaseIndex());
                continue;
            }
            var outputs = new KeyCounter();
            var containers = new KeyCounter();
            var inputInventory = current.executionRuntime == null
                ? new ECOCraftingInputPreview(inventory)
                : new ECOCraftingInputPreview(inventory, pattern);
            var inputs = CraftingCpuHelper.extractPatternInputs(
                pattern, inputInventory, level, outputs, containers);
            if (inputs == null) {
                // Missing intermediates do not prevent another ready DAG/dynamic candidate from running, but an
                // ordered step is a hard barrier and must wait for this exact pattern.
                if (candidate.blocksOrderedPhase()) blockedOrderedPhases.add(candidate.phaseIndex());
                continue;
            }
            for (var provider : providers) {
                long craftCount = 1L;
                double singlePower = CraftingCpuHelper.calculatePatternPower(inputs);
                double power = singlePower;
                var capacityProvider = provider instanceof ECOBatchCapacityProvider nativeProvider
                    ? nativeProvider : ECOUselessBatchProviderBridge.adapt(provider);
                ECOUselessDynamicOutputBridge.Registration batchRegistration = null;
                var batch = capacityProvider != null
                    ? ECOBatchCraftingExecutor.prepare(capacityProvider, pattern, inputs, outputs, containers,
                        inventory, allowedCount,
                        energyService, level, current.link.getCraftingID())
                    : null;
                if (batch != null) {
                    craftCount = batch.craftCount();
                    try {
                        batchRegistration = ECOUselessDynamicOutputBridge.prepare(this, pattern, craftCount);
                    } catch (RuntimeException failure) {
                        LOGGER.warn("Batch dynamic output registration unavailable; trying ordinary provider push", failure);
                    }
                    if (batchRegistration != null) {
                        power = batch.power();
                        boolean acceptedBatch;
                        try {
                            providerCursor.advanceAfter(pattern, provider);
                            acceptedBatch = batch.push(inventory);
                        } catch (RuntimeException failure) {
                            LOGGER.warn("Atomic batch rejected; inputs restored, trying ordinary provider push", failure);
                            acceptedBatch = false;
                        }
                        if (acceptedBatch) {
                            // Once accepted, the worker owns the inputs even if the energy service fails.
                            chargeAcceptedEnergy(energyService, power);
                            for (var output : batch.outputs()) {
                                current.waitingFor.insert(output.what(), output.amount(), Actionable.MODULATE);
                            }
                            for (var remainder : batch.remainders()) {
                                current.waitingFor.insert(remainder.what(), remainder.amount(), Actionable.MODULATE);
                                current.timeTracker.addMaxItems(remainder.amount(), remainder.what().getType());
                            }
                            progress.value -= craftCount;
                            if (current.executionRuntime != null) {
                                current.executionRuntime.onAccepted(candidate, craftCount, inputs);
                            }
                            try {
                                batchRegistration.commit(current.link.getCraftingID(),
                                    current.finalOutput == null ? null : current.finalOutput.what());
                            } catch (RuntimeException failure) {
                                // The provider already owns this batch. Never replay its inputs or task on a
                                // notification failure.
                                LOGGER.error("Accepted batch could not register Useless dynamic outputs", failure);
                            }
                            for (var output : pattern.getOutputs()) postChange(output.what());
                            markCpuDirty();
                            // Keep the int Mixin entry point; job accounting above retains the full long count.
                            return (int) Math.min(craftCount, Integer.MAX_VALUE);
                        }
                        // A rejected batch restores its own extraction. The ordinary fallback is exactly one
                        // copy, so it must use the per-copy power rather than the rejected batch total.
                        power = singlePower;
                    }
                }

                // Batch is an optional optimization. A provider that offered a batch still retains the normal
                // one-copy fallback when that batch is unavailable, rejected, or dynamically ambiguous.
                if (ordinaryLimit <= 0 || normalPushProbesThisPass >= probeLimit) {
                    continue;
                }
                if (energyService.extractAEPower(power, Actionable.SIMULATE,
                        PowerMultiplier.CONFIG) < power - 0.01) {
                    // Power is shared by all providers for this pattern; there is no value in retrying the rest
                    // of this provider snapshot in the same tick.
                    break;
                }
                ECOBatchCraftingHelper.extractExact(inventory, ECOFastPathStacks.copyCounters(inputs));
                boolean acceptedSingle = false;
                try {
                    providerCursor.advanceAfter(pattern, provider);
                    normalPushProbesThisPass++;
                    // Keep fairness separate from task progress: rejected pushes must never call onAccepted.
                    // Preserve this position while probes are exhausted, including across batch-only passes.
                    resumeDispatchPattern = candidates.get((candidateIndex + 1) % candidates.size()).pattern();
                    clearProviderDiagnostics(provider);
                    if (provider instanceof ECOCraftingPatternBusBlockEntity) {
                        acceptedSingle = ECOSingleCraftingExecutor.pushPattern(
                            provider, pattern, inputs, outputs, containers, level, current.link.getCraftingID());
                    } else {
                        // Useless Mod wraps this exact invocation in executeCrafting to register dynamic outputs.
                        // Keep it here: moving it into an executor breaks its required Mixin injection.
                        acceptedSingle = provider.pushPattern(pattern, inputs);
                    }
                    if (!acceptedSingle) {
                        continue;
                    }

                    // Once accepted, the worker owns the inputs even if the energy service fails.
                    chargeAcceptedEnergy(energyService, power);
                    for (var output : outputs) {
                        current.waitingFor.insert(output.getKey(), output.getLongValue(), Actionable.MODULATE);
                    }
                    for (var container : containers) {
                        current.waitingFor.insert(container.getKey(), container.getLongValue(), Actionable.MODULATE);
                        current.timeTracker.addMaxItems(container.getLongValue(), container.getKey().getType());
                    }
                    progress.value--;
                    if (current.executionRuntime != null) {
                        current.executionRuntime.onAccepted(candidate, 1L, inputs);
                    }
                    lastAcceptedNormalPushes++;
                    for (var output : pattern.getOutputs()) postChange(output.what());
                    markCpuDirty();
                    return 1;
                } finally {
                    // A rejected ordinary provider does not own the extracted inputs; try the next provider in
                    // the same provider-first-fit pass.
                    if (!acceptedSingle) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, inputs);
                    }
                }
            }
            if (candidate.blocksOrderedPhase()) blockedOrderedPhases.add(candidate.phaseIndex());
        }
        return 0;
    }

    private void clearProviderDiagnostics(ICraftingProvider provider) {
        if (provider instanceof ECOPatternPushDiagnostics diagnostics) {
            try {
                diagnostics.neoecoae$clearPushDiagnostics();
            } catch (RuntimeException ignored) {
                // Observability must not prevent dispatch or interfere with ownership transfer.
            }
        }
    }

    private java.util.List<ECOExecutionRuntime.DispatchCandidate> nativeDispatchCandidates(
            ExecutingCraftingJob current) {
        var result = new java.util.ArrayList<ECOExecutionRuntime.DispatchCandidate>();
        for (var entry : current.tasks.entrySet()) {
            if (entry.getValue().value > 0L) {
                // Native jobs do not consult task ids; the placeholder id is never committed to a runtime.
                result.add(new ECOExecutionRuntime.DispatchCandidate(0, 0, entry.getKey(),
                    entry.getValue().value, false));
            }
        }
        return result;
    }

    private void chargeAcceptedEnergy(IEnergyService energyService, double power) {
        if (power == 0.0D) return;
        try {
            double charged = energyService.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (!Double.isFinite(charged) || charged < power - 0.01D) {
                LOGGER.error("ECO crafting was accepted, but only {} of {} energy was charged", charged, power);
            }
        } catch (RuntimeException failure) {
            LOGGER.error("ECO crafting was accepted, but its energy could not be charged", failure);
        }
    }

    /** Accept only outstanding outputs; every accepted item becomes physical CPU inventory. */
    public long insert(AEKey what, long amount, Actionable type) {
        var current = job;
        if (what == null || amount <= 0L || current == null) return 0L;
        if (ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), current.link.getCraftingID())) return 0L;
        if (deliveringFinalOutput && what.matches(current.finalOutput)) return 0L;
        long accepted = current.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (accepted <= 0L) return 0L;
        if (type == Actionable.MODULATE) {
            inventory.insert(what, accepted, Actionable.MODULATE);
            current.waitingFor.extract(what, accepted, Actionable.MODULATE);
            current.timeTracker.decrementItems(accepted, what.getType());
            markCpuDirty();
        }
        return accepted;
    }

    /**
     * Accepts a worker output only when this CPU still owns the supplied crafting job, retaining surplus locally.
     *
     * <p>Worker outputs carry the job id, but AE2's legacy {@code insertIntoCpus} API does not. Keeping this
     * guard at the CPU boundary prevents an output from being assigned to another CPU that happens to wait for
     * the same key.</p>
     */
    public long insertForJob(UUID craftingJobId, AEKey what, long amount, Actionable type) {
        if (what == null || amount <= 0L || craftingJobId == null || job == null
                || !craftingJobId.equals(job.link.getCraftingID())
                || ECOCraftingJobLifecycle.isTerminated(cpu.getLevel(), craftingJobId)) {
            return 0L;
        }
        long accepted = insert(what, amount, type);
        if (accepted < 0L || accepted > amount) {
            throw new IllegalStateException("Invalid CPU insertion amount: " + accepted + " for " + amount);
        }
        // Job-directed surplus belongs to this CPU too, but must not decrement unrelated waiting entries.
        if (type == Actionable.MODULATE && accepted < amount) {
            inventory.insert(what, amount - accepted, Actionable.MODULATE);
            markCpuDirty();
        }
        return amount;
    }

    public boolean hasCraftingJob(UUID craftingJobId) {
        return craftingJobId != null && job != null && craftingJobId.equals(job.link.getCraftingID());
    }

    /**
     * 完成当前合成任务。
     *
     * @param success 任务完成则为 true，取消则为 false。
     */
    private void finishJob(boolean success) {
        ECOCraftingJobLifecycle.finish(cpu.getLevel(), job.link.getCraftingID(), success);
        if (success) {
            job.link.markDone();
            var grid = cpu.getGrid();
            if (grid != null) {
                for (var worker : grid.getMachines(ECOCraftingWorkerBlockEntity.class)) {
                    worker.releaseCompletedJobOutputs(job.link.getCraftingID());
                }
            }
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
        providerCursor.clear();
        resumeDispatchPattern = null;

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
        Level level = cpu.getLevel();
        if (level == null || level.getServer() == null) return;
        // Loaded workers may already have left this grid. Unloaded workers reconcile the durable decision later.
        for (ECOCraftingWorkerBlockEntity worker : ECOCraftingWorkerBlockEntity.getLoadedServerWorkers()) {
            if (worker.getLevel() != null && worker.getLevel().getServer() == level.getServer()) {
                worker.recoverTerminatedJob(craftingJobId);
            }
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

        markCpuDirty();
    }

    private void postChange(@Nullable AEKey what) {
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        notifyListeners(what);
    }

    private void notifyListeners(@Nullable AEKey what) {
        for (var listener : listeners) listener.accept(what);
    }

    private void markCpuDirty() {
        cpu.markDirty();
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
        providerCursor.clear();
        resumeDispatchPattern = null;
        dispatchStrategy.reset();
        this.inventory.readFromNBT(data.getList("inventory", 10), registries);
        if (data.contains("job")) {
            var jobData = data.getCompound("job");
            this.job = new ExecutingCraftingJob(jobData, registries, this::postChange, this);
            // One-time migration of physical items held in the former separate final-output buffer.
            long buffered = jobData.getLong("bufferedFinalOutput");
            if (buffered > 0L && job.finalOutput != null) {
                inventory.insert(job.finalOutput.what(), buffered, Actionable.MODULATE);
            }
            if (this.job.finalOutput == null) {
                finishJob(false);
            }
        }
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.put("inventory", this.inventory.writeToNBT(registries));
        if (this.job != null) {
            data.put("job", this.job.writeToNBT(registries));
        } else {
            data.remove("job");
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

    /** Collects only items physically owned by this CPU, excluding planned and in-flight outputs. */
    public void getOwnedItems(KeyCounter out) {
        out.addAll(this.inventory.list);
    }

    /** Allocation-free counterpart of {@link #getOwnedItems(KeyCounter)}; must cover the same ledgers. */
    public boolean hasOwnedItems() {
        return !this.inventory.list.isEmpty();
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    /** Stable diagnostic hook for CPU menus/integrations; null means the job is still executable. */
    public @Nullable String getPermanentExecutionError() {
        return null;
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
            markCpuDirty();
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        var playerId = job.playerId;
        if (playerId == null || job.finalOutput == null) {
            return;
        }

        Level level = cpu.getLevel();
        if (level == null) {
            return;
        }

        var server = level.getServer();
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
