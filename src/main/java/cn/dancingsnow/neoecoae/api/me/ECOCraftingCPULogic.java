package cn.dancingsnow.neoecoae.api.me;

import java.util.HashSet;
import java.util.BitSet;
import java.util.List;
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
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
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
    /** Energy already debited for a rejected dispatch that the full grid could not accept back. */
    private double prepaidEnergyCredit;
    private long lastEnergyAccountingFailureLogTick = Long.MIN_VALUE;
    private long lastIdleEnergyRefundAttemptTick = Long.MIN_VALUE;
    private final ECOProviderCursor providerCursor = new ECOProviderCursor();
    private final ECOCraftingDispatchStrategy dispatchStrategy = new ECOCraftingDispatchStrategy();
    private final ECODispatchStallDiagnostics stallDiagnostics = new ECODispatchStallDiagnostics();
    // Per-call result, consumed by tickCraftingLogic after each executeCrafting invocation.
    private int normalPushProbesThisPass;
    private int lastAcceptedNormalPushes;
    private static final int MIN_NORMAL_PROBES_PER_TICK = 64;
    // Shared across every pass of tickCraftingLogic; -1 denotes a standalone executeCrafting call.
    private int remainingNormalProbes = -1;
    private final ECODispatchBudget dispatchBudget = new ECODispatchBudget();
    private boolean dispatchYieldedThisPass;
    private int acceptedDispatchesThisTick;
    private long dispatchAccountingTick = Long.MIN_VALUE;
    private IPatternDetails resumeDispatchPattern;
    private final java.util.List<ECOExecutionRuntime.DispatchCandidate> nativeCandidateBuffer =
        new java.util.ArrayList<>();
    private final java.util.List<ECOExecutionRuntime.DispatchCandidate> prioritizedCandidateBuffer =
        new java.util.ArrayList<>();
    private final java.util.List<ECOExecutionRuntime.DispatchCandidate> directCandidateBuffer =
        new java.util.ArrayList<>();
    private final java.util.List<ECOExecutionRuntime.DispatchCandidate> resolvedCandidateBuffer =
        new java.util.ArrayList<>();
    private final java.util.List<ECOExecutionRuntime.DispatchCandidate> genericCandidateBuffer =
        new java.util.ArrayList<>();
    private final java.util.Map<Integer, ECOResolvedDispatchLease> resolvedDispatchLeases =
        new java.util.HashMap<>();

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
        resolvedDispatchLeases.clear();
        providerCursor.clear();
        resumeDispatchPattern = null;
        stallDiagnostics.bind(craftId, TickHandler.instance().getCurrentTick());
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
            returnIdleEnergyCredit(eg);
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

        stallDiagnostics.bind(job.link.getCraftingID(), TickHandler.instance().getCurrentTick());
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
        long dispatchTick = TickHandler.instance().getCurrentTick();
        dispatchBudget.beginTick(dispatchTick);
        acceptedDispatchesThisTick = 0;
        dispatchAccountingTick = dispatchTick;
        try {
            while (job != null) {
                // Reset pass results only. The remaining tick probe budget is never reset here.
                normalPushProbesThisPass = 0;
                lastAcceptedNormalPushes = 0;
                dispatchYieldedThisPass = false;
                int pushed = executeCrafting(operationLimit, cc, eg, level);
                remainingNormalProbes = Math.max(0, remainingNormalProbes - normalPushProbesThisPass);
                operationLimit = Math.max(0, operationLimit - lastAcceptedNormalPushes);
                acceptedNormalPushes += lastAcceptedNormalPushes;
                if (dispatchYieldedThisPass || pushed == 0) break;
            }
        } finally {
            remainingNormalProbes = -1;
        }
        // Match the rolling three-tick accounting for ordinary pushes. Verified ECO batches are bounded
        // by the live provider capacity and deliberately do not consume this operation window.
        dispatchStrategy.finishTick(acceptedNormalPushes);
        if (job != null) {
            stallDiagnostics.check(TickHandler.instance().getCurrentTick(), job);
        }
    }

    /** Retry delivery from the same physical inventory used for all recipe inputs. */
    private void deliverStoredFinalOutput() {
        var current = job;
        if (current == null) return;
        AEKey key = current.finalOutput.what();
        long storedFinalOutput = inventory.list.get(key);
        if (storedFinalOutput > 0L) {
            PlannerAmount reserve = PlannerAmount.ZERO;
            for (var task : current.tasks.entrySet()) {
                reserve = reserve.add(ECOPhaseScheduler.growingPatternFeedbackReserveExact(
                    task.getKey(), task.getValue().value, key));
            }
            if (current.executionRuntime != null) {
                reserve = reserve.max(PlannerAmount.of(current.executionRuntime.reservedInputAmount(key)));
            }
            // Keep returned feedback available for the next growth wave before delivering any surplus.
            PlannerAmount deliverable = PlannerAmount.of(storedFinalOutput)
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
                if (inserted > 0L) {
                    stallDiagnostics.progress(TickHandler.instance().getCurrentTick());
                } else {
                    stallDiagnostics.finalDeliveryBlocked(key, amount);
                }
            }
        }
        boolean tasksDone = !hasPendingTasks(current);
        boolean physicallyComplete = current.remainingAmount <= 0L
                && current.waitingFor.list.isEmpty()
                && tasksDone;
        if (physicallyComplete) {
            if (current.executionRuntime != null && !current.executionRuntime.isComplete()) {
                LOGGER.warn("ECO crafting job {} reached terminal crafting state "
                                + "but execution runtime is incomplete; forcing finalization",
                        current.link.getCraftingID());
            }
            finishJob(true);
        }
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
        long dispatchTick = TickHandler.instance().getCurrentTick();
        if (dispatchAccountingTick != dispatchTick) {
            dispatchAccountingTick = dispatchTick;
            acceptedDispatchesThisTick = 0;
        }
        dispatchBudget.beginTick(dispatchTick);
        providerCursor.beginPass(craftingService, dispatchTick);
        int ordinaryLimit = Math.max(0, maxPatterns);
        // Direct callers get a bounded standalone pass. CPU ticks supply the shared remaining budget.
        int probeLimit = remainingNormalProbes >= 0
            ? remainingNormalProbes : Math.max(MIN_NORMAL_PROBES_PER_TICK, ordinaryLimit);
        stallDiagnostics.beginDispatch(ordinaryLimit, probeLimit);
        int totalPushed = 0;
        BitSet blockedOrderedPhases = new BitSet();
        while (job == current) {
            var rawCandidates = current.executionRuntime == null
                ? nativeDispatchCandidates(current)
                : current.executionRuntime.candidates();
            var candidates = prioritizeCandidates(rawCandidates, current.executionRuntime);
            stallDiagnostics.candidates(candidates.size());
            if (candidates.isEmpty()) {
                if (stallDiagnostics.isActive()) {
                    stallDiagnostics.noCandidates(current.executionRuntime != null && hasPendingTasks(current));
                }
                break;
            }

            int start = 0;
            if (resumeDispatchPattern != null && current.executionRuntime == null) {
                for (int i = 0; i < candidates.size(); i++) {
                    if (candidates.get(i).pattern().equals(resumeDispatchPattern)) {
                        start = i;
                        break;
                    }
                }
            }
            blockedOrderedPhases.clear();
            boolean acceptedInSweep = false;
            for (int offset = 0; offset < candidates.size(); offset++) {
                int candidateIndex = (start + offset) % candidates.size();
                var candidate = candidates.get(candidateIndex);
                boolean candidateAccepted = false;
                if (acceptedDispatchesThisTick >= NEConfig.ecoDispatchSafetyLimitPerTick) {
                    dispatchYieldedThisPass = true;
                    stallDiagnostics.budget();
                    break;
                }
                if (blockedOrderedPhases.get(candidate.phaseIndex())) continue;
                var progress = current.tasks.get(candidate.pattern());
                if (progress == null || progress.value <= 0L) {
                    providerCursor.forget(candidate.pattern());
                    continue;
                }
                long allowedCount = Math.min(candidate.maxDispatchCount(), progress.value);
                if (allowedCount <= 0L) {
                    if (candidate.blocksOrderedPhase()) stallDiagnostics.phaseBarrier();
                    continue;
                }
                var pattern = candidate.pattern();
                // The explicit execution runtime owns phase/cycle gating. The growth barrier remains the fallback
                // policy for legacy jobs that have no bound ECO plan.
                if (current.executionRuntime == null && !current.canDispatchAfterGrowth(pattern)) {
                    stallDiagnostics.phaseBarrier();
                    continue;
                }
                // Skip input resolution when no eligible provider is ready for a dispatch.
                var providers = providerCursor.availableProviders(
                    pattern, () -> collectAvailableProviders(craftingService, pattern),
                    providerCandidate -> {
                        boolean eligible = (ordinaryLimit > 0 && lastAcceptedNormalPushes < ordinaryLimit
                            && normalPushProbesThisPass < probeLimit)
                            || providerCandidate instanceof ECOBatchCapacityProvider
                            || ECOUselessBatchProviderBridge.supports(providerCandidate);
                        stallDiagnostics.providerConsidered(eligible);
                        return eligible;
                    }, (providerCandidate, busy) ->
                        stallDiagnostics.provider(pattern, providerCandidate, busy));
                if (providers.isEmpty()) {
                    stallDiagnostics.noReadyProvider();
                    if (candidate.blocksOrderedPhase()) blockedOrderedPhases.set(candidate.phaseIndex());
                    continue;
                }
                var outputs = new KeyCounter();
                var containers = new KeyCounter();
                ECOCompiledPatternInputs compiled = current.executionRuntime == null
                    ? null : ECOCompiledPatternInputs.get(pattern);
                var inputInventory = current.executionRuntime == null
                    ? new ECOCraftingInputPreview(inventory)
                    : new ECOCraftingInputPreview(inventory, compiled,
                        key -> current.executionRuntime.protectedStartupSeedAmount(candidate, key));
                KeyCounter[] inputs;
                if (compiled != null
                        && compiled.resolutionMode() == ECOCompiledPatternInputs.ResolutionMode.DIRECT_EXACT) {
                    inputs = compiled.resolveDirect(inputInventory, level, outputs, containers);
                } else {
                    ECOResolvedDispatchLease lease = current.executionRuntime == null
                        ? null : resolvedDispatchLeases.get(candidate.taskId());
                    inputs = lease == null ? null : lease.resolve(
                        pattern, inputInventory, level, outputs, containers,
                        current.executionRuntime.startupSeedEpoch());
                    boolean leaseMaterialMissing = lease != null && inputs == null && lease.materialMissing();
                    if (lease != null && inputs == null && !leaseMaterialMissing) {
                        resolvedDispatchLeases.remove(candidate.taskId());
                    }
                    if (inputs == null && !leaseMaterialMissing) {
                        if (!dispatchBudget.tryBeginGeneric(dispatchTick)) {
                            dispatchYieldedThisPass = true;
                            stallDiagnostics.budget();
                            break;
                        }
                        try {
                            inputs = CraftingCpuHelper.extractPatternInputs(
                                pattern, inputInventory, level, outputs, containers);
                        } finally {
                            dispatchBudget.finishGeneric(dispatchTick);
                        }
                        if (inputs != null && compiled != null && compiled.resolvedLeaseSafe()
                                && current.executionRuntime != null) {
                            resolvedDispatchLeases.put(candidate.taskId(), ECOResolvedDispatchLease.create(
                                inputs, outputs, containers, current.executionRuntime.startupSeedEpoch()));
                        }
                    }
                }
                if (inputs == null) {
                    if (current.executionRuntime != null) current.executionRuntime.onMissingInputs(candidate);
                    if (stallDiagnostics.isActive()) {
                        stallDiagnostics.missingInputs(pattern, inputInventory);
                    }
                    // Missing intermediates do not prevent another ready DAG/dynamic candidate from running, but an
                    // ordered step is a hard barrier and must wait for this exact pattern.
                    if (candidate.blocksOrderedPhase()) {
                        stallDiagnostics.phaseBarrier();
                        blockedOrderedPhases.set(candidate.phaseIndex());
                    }
                    continue;
                }
                // Inputs are resolved once for this provider-first-fit pass; their power cost is identical
                // for every provider attempt and must not be recalculated inside that loop.
                double singlePower = CraftingCpuHelper.calculatePatternPower(inputs);
                List<GenericStack> ordinaryInputStacks = null;
                for (var provider : providers) {
                    if (acceptedDispatchesThisTick >= NEConfig.ecoDispatchSafetyLimitPerTick) {
                        dispatchYieldedThisPass = true;
                        break;
                    }
                    long craftCount = 1L;
                    double power = singlePower;
                    var capacityProvider = provider instanceof ECOBatchCapacityProvider nativeProvider
                        ? nativeProvider : ECOUselessBatchProviderBridge.adapt(provider);
                    ECOUselessDynamicOutputBridge.Registration batchRegistration = null;
                    var batch = capacityProvider != null
                        ? ECOBatchCraftingExecutor.prepare(capacityProvider, pattern, inputs, outputs, containers,
                            inventory, allowedCount, singlePower,
                            energyService, level, current.link.getCraftingID())
                        : null;
                    if (batch != null && current.executionRuntime != null
                            && !current.executionRuntime.preservesStartupSeeds(
                                candidate, batch.inputTotal(), inventory)) {
                        // The batch calculator sees the physical CPU inventory. Reject a batch that would cross a
                        // different phase's seed lease; the one-copy fallback still uses the protected preview.
                        batch = null;
                    }
                    if (batch != null) {
                        craftCount = batch.craftCount();
                        try {
                            batchRegistration = ECOUselessDynamicOutputBridge.prepare(this, pattern, craftCount);
                        } catch (RuntimeException failure) {
                            LOGGER.warn("Batch dynamic output registration unavailable; trying ordinary provider push", failure);
                        }
                        if (batchRegistration != null) {
                            power = batch.power();
                            EnergyReservation energyReservation = reserveEnergy(energyService, power);
                            if (energyReservation == null) {
                                stallDiagnostics.insufficientPower(power, 0.0D);
                                power = singlePower;
                            } else {
                                boolean acceptedBatch;
                                try {
                                    providerCursor.advanceAfter(pattern, provider);
                                    acceptedBatch = batch.push(inventory);
                                } catch (RuntimeException failure) {
                                    LOGGER.warn("Atomic batch rejected; inputs restored, trying ordinary provider push", failure);
                                    acceptedBatch = false;
                                }
                                if (acceptedBatch) {
                                    acceptedDispatchesThisTick++;
                                    energyReservation.commit();
                                    for (var output : batch.outputs()) {
                                        current.waitingFor.insert(output.what(), output.amount(), Actionable.MODULATE);
                                    }
                                    for (var remainder : batch.remainders()) {
                                        current.waitingFor.insert(remainder.what(), remainder.amount(), Actionable.MODULATE);
                                        current.timeTracker.addMaxItems(remainder.amount(), remainder.what().getType());
                                    }
                                    progress.value -= craftCount;
                                    if (progress.value <= 0L && current.executionRuntime != null) {
                                        resolvedDispatchLeases.remove(candidate.taskId());
                                    }
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
                                    stallDiagnostics.progress(TickHandler.instance().getCurrentTick());
                                    // Resume after this candidate on the next sweep; the current sweep continues filling
                                    // unrelated ready workers before rebuilding phase state.
                                    resumeDispatchPattern = nextCandidatePattern(candidates, candidateIndex);
                                    totalPushed = addPushed(totalPushed, craftCount);
                                    candidateAccepted = true;
                                    acceptedInSweep = true;
                                    break;
                                }
                                energyReservation.refund();
                                stallDiagnostics.batchRejected(pattern, provider);
                                // A rejected batch restores its own extraction. The ordinary fallback is exactly one
                                // copy, so it must use the per-copy power rather than the rejected batch total.
                                power = singlePower;
                            }
                        }
                    }

                    // Batch is an optional optimization. A provider that offered a batch still retains the normal
                    // one-copy fallback when that batch is unavailable, rejected, or dynamically ambiguous.
                    if (ordinaryLimit <= 0 || normalPushProbesThisPass >= probeLimit) {
                        stallDiagnostics.budget();
                        continue;
                    }
                    double availablePower = energyService.extractAEPower(
                        power, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                    if (availablePower < power - 0.01) {
                        stallDiagnostics.insufficientPower(power, availablePower);
                        // Power is shared by all providers for this pattern; there is no value in retrying the rest
                        // of this provider snapshot in the same tick.
                        break;
                    }
                    if (ordinaryInputStacks == null) {
                        ordinaryInputStacks = ECOFastPathStacks.copyCounters(inputs);
                    }
                    EnergyReservation energyReservation = reserveEnergy(energyService, power);
                    if (energyReservation == null) {
                        stallDiagnostics.insufficientPower(power, 0.0D);
                        break;
                    }
                    boolean inputsExtracted;
                    try {
                        inputsExtracted = ECOBatchCraftingHelper.extractExact(inventory, ordinaryInputStacks);
                    } catch (RuntimeException failure) {
                        energyReservation.refund();
                        throw failure;
                    }
                    if (!inputsExtracted) {
                        // extractExact already restored the partial extraction. Do not reinject the complete
                        // resolved input set, and do not offer that stale set to another provider.
                        energyReservation.refund();
                        break;
                    }
                    boolean acceptedSingle = false;
                    try {
                        providerCursor.advanceAfter(pattern, provider);
                        normalPushProbesThisPass++;
                        stallDiagnostics.probe();
                        // Keep fairness separate from task progress: rejected pushes must never call onAccepted.
                        // Preserve this position while probes are exhausted, including across batch-only passes.
                        resumeDispatchPattern = nextCandidatePattern(candidates, candidateIndex);
                        if (stallDiagnostics.isActive()) clearProviderDiagnostics(provider);
                        if (provider instanceof ECOCraftingPatternBusBlockEntity) {
                            acceptedSingle = ECOSingleCraftingExecutor.pushPattern(
                                provider, pattern, inputs, outputs, containers, level, current.link.getCraftingID());
                        } else {
                            // Useless Mod wraps this exact invocation in executeCrafting to register dynamic outputs.
                            // Keep it here: moving it into an executor breaks its required Mixin injection.
                            acceptedSingle = provider.pushPattern(pattern, inputs);
                        }
                        if (!acceptedSingle) {
                            stallDiagnostics.pushRejected(pattern, provider);
                            continue;
                        }

                        energyReservation.commit();
                        for (var output : outputs) {
                            current.waitingFor.insert(output.getKey(), output.getLongValue(), Actionable.MODULATE);
                        }
                        for (var container : containers) {
                            current.waitingFor.insert(container.getKey(), container.getLongValue(), Actionable.MODULATE);
                            current.timeTracker.addMaxItems(container.getLongValue(), container.getKey().getType());
                        }
                        progress.value--;
                        if (progress.value <= 0L && current.executionRuntime != null) {
                            resolvedDispatchLeases.remove(candidate.taskId());
                        }
                        if (current.executionRuntime != null) {
                            current.executionRuntime.onAccepted(candidate, 1L, inputs);
                        }
                        lastAcceptedNormalPushes++;
                        acceptedDispatchesThisTick++;
                        totalPushed = addPushed(totalPushed, 1L);
                        for (var output : pattern.getOutputs()) postChange(output.what());
                        markCpuDirty();
                        stallDiagnostics.progress(TickHandler.instance().getCurrentTick());
                        candidateAccepted = true;
                        acceptedInSweep = true;
                        break;
                    } finally {
                        // A rejected ordinary provider does not own the extracted inputs; try the next provider in
                        // the same provider-first-fit pass.
                        if (!acceptedSingle) {
                            CraftingCpuHelper.reinjectPatternInputs(inventory, inputs);
                            energyReservation.refund();
                        }
                    }
                }
                if (dispatchYieldedThisPass) break;
                if (!candidateAccepted && candidate.blocksOrderedPhase()) {
                    blockedOrderedPhases.set(candidate.phaseIndex());
                }
            }
            if (dispatchYieldedThisPass || !acceptedInSweep) break;
        }
        return totalPushed;
    }

    private static IPatternDetails nextCandidatePattern(
            java.util.List<ECOExecutionRuntime.DispatchCandidate> candidates, int candidateIndex) {
        return candidates.get((candidateIndex + 1) % candidates.size()).pattern();
    }

    private static int addPushed(int current, long accepted) {
        return (int) Math.min(Integer.MAX_VALUE, (long) current + Math.max(0L, accepted));
    }

    private static boolean hasPendingTasks(ExecutingCraftingJob current) {
        for (var task : current.tasks.values()) {
            if (task.value > 0L) return true;
        }
        return false;
    }

    private java.util.List<ECOExecutionRuntime.DispatchCandidate> prioritizeCandidates(
            java.util.List<ECOExecutionRuntime.DispatchCandidate> candidates,
            @Nullable ECOExecutionRuntime runtime) {
        if (runtime == null || candidates.size() < 2) return candidates;
        directCandidateBuffer.clear();
        resolvedCandidateBuffer.clear();
        genericCandidateBuffer.clear();
        prioritizedCandidateBuffer.clear();
        for (var candidate : candidates) {
            ECOCompiledPatternInputs compiled = ECOCompiledPatternInputs.get(candidate.pattern());
            if (compiled.resolutionMode() == ECOCompiledPatternInputs.ResolutionMode.DIRECT_EXACT) {
                directCandidateBuffer.add(candidate);
            } else if (resolvedDispatchLeases.containsKey(candidate.taskId())) {
                resolvedCandidateBuffer.add(candidate);
            } else {
                genericCandidateBuffer.add(candidate);
            }
        }
        prioritizedCandidateBuffer.addAll(directCandidateBuffer);
        prioritizedCandidateBuffer.addAll(resolvedCandidateBuffer);
        prioritizedCandidateBuffer.addAll(genericCandidateBuffer);
        return prioritizedCandidateBuffer;
    }

    private static final class ECOResolvedDispatchLease {
        private final KeyCounter[] inputs;
        private final KeyCounter outputs;
        private final KeyCounter containers;
        private final long reloadGeneration;
        private final long startupSeedEpoch;
        private boolean materialMissing;

        private ECOResolvedDispatchLease(KeyCounter[] inputs, KeyCounter outputs, KeyCounter containers,
                long reloadGeneration, long startupSeedEpoch) {
            this.inputs = copyCounters(inputs);
            this.outputs = copyCounter(outputs);
            this.containers = copyCounter(containers);
            this.reloadGeneration = reloadGeneration;
            this.startupSeedEpoch = startupSeedEpoch;
        }

        static ECOResolvedDispatchLease create(KeyCounter[] inputs, KeyCounter outputs,
                KeyCounter containers, long startupSeedEpoch) {
            return new ECOResolvedDispatchLease(inputs, outputs, containers,
                AE2PatternIntrospection.reloadGeneration(), startupSeedEpoch);
        }

        @Nullable
        KeyCounter[] resolve(IPatternDetails pattern, ECOCraftingInputPreview inventory, Level level,
                KeyCounter expectedOutputs, KeyCounter expectedContainers, long currentSeedEpoch) {
            materialMissing = false;
            IPatternDetails.IInput[] patternInputs = pattern.getInputs();
            if (reloadGeneration != AE2PatternIntrospection.reloadGeneration()
                    || startupSeedEpoch != currentSeedEpoch
                    || patternInputs.length != inputs.length) {
                return null;
            }
            KeyCounter[] result = new KeyCounter[inputs.length];
            for (int slot = 0; slot < inputs.length; slot++) {
                KeyCounter resolved = new KeyCounter();
                result[slot] = resolved;
                for (var entry : inputs[slot]) {
                    if (!patternInputs[slot].isValid(entry.getKey(), level)) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, result);
                        return null;
                    }
                    if (inventory.extract(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE)
                            < entry.getLongValue()) {
                        materialMissing = true;
                        CraftingCpuHelper.reinjectPatternInputs(inventory, result);
                        return null;
                    }
                    long extracted = inventory.extract(
                        entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                    if (extracted > 0L) resolved.add(entry.getKey(), extracted);
                    if (extracted != entry.getLongValue()) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, result);
                        throw new IllegalStateException("Resolved ECO input lease simulation changed");
                    }
                }
            }
            expectedOutputs.addAll(outputs);
            expectedContainers.addAll(containers);
            return result;
        }

        boolean materialMissing() {
            return materialMissing;
        }

        private static KeyCounter[] copyCounters(KeyCounter[] source) {
            KeyCounter[] result = new KeyCounter[source.length];
            for (int index = 0; index < source.length; index++) {
                result[index] = copyCounter(source[index]);
            }
            return result;
        }

        private static KeyCounter copyCounter(KeyCounter source) {
            KeyCounter result = new KeyCounter();
            if (source != null) result.addAll(source);
            return result;
        }
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
        var result = nativeCandidateBuffer;
        result.clear();
        for (var entry : current.tasks.entrySet()) {
            if (entry.getValue().value > 0L) {
                // Native jobs do not consult task ids; the placeholder id is never committed to a runtime.
                result.add(new ECOExecutionRuntime.DispatchCandidate(0, 0, entry.getKey(),
                    entry.getValue().value, false));
            }
        }
        return result;
    }

    @Nullable
    private EnergyReservation reserveEnergy(IEnergyService energyService, double power) {
        if (power == 0.0D) return new EnergyReservation(energyService, 0.0D, 0.0D);
        if (!Double.isFinite(power) || power < 0.0D) return null;
        double credit = Math.min(power, prepaidEnergyCredit);
        prepaidEnergyCredit -= credit;
        double networkPower = power - credit;
        if (networkPower <= 0.0D) {
            return new EnergyReservation(energyService, credit, 0.0D);
        }
        try {
            double charged = energyService.extractAEPower(networkPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (!Double.isFinite(charged)
                    || charged < networkPower - 0.01D
                    || charged > networkPower + 0.01D) {
                restoreEnergyCredit(credit);
                if (Double.isFinite(charged) && charged > 0.0D) {
                    refundEnergyOrRetainCredit(energyService, charged);
                }
                logEnergyAccountingFailure(
                    "reservation charged " + charged + " of " + networkPower
                        + " after " + credit + " prepaid credit",
                    null);
                return null;
            }
            return new EnergyReservation(energyService, credit, charged);
        } catch (RuntimeException failure) {
            restoreEnergyCredit(credit);
            logEnergyAccountingFailure("energy reservation failed", failure);
            return null;
        }
    }

    private void refundEnergyOrRetainCredit(IEnergyService energyService, double amount) {
        if (amount <= 0.0D) return;
        try {
            double overflow = energyService.injectPower(amount, Actionable.MODULATE);
            if (!Double.isFinite(overflow) || overflow < -0.01D || overflow > amount + 0.01D) {
                restoreEnergyCredit(amount);
                logEnergyAccountingFailure("invalid refund overflow " + overflow + " of " + amount, null);
                return;
            }
            restoreEnergyCredit(Math.max(0.0D, overflow));
        } catch (RuntimeException failure) {
            restoreEnergyCredit(amount);
            logEnergyAccountingFailure("refund failed for " + amount + " energy", failure);
        }
    }

    private void restoreEnergyCredit(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        double updated = prepaidEnergyCredit + amount;
        prepaidEnergyCredit = Double.isFinite(updated) ? updated : Double.MAX_VALUE;
        markCpuDirty();
    }

    private void returnIdleEnergyCredit(IEnergyService energyService) {
        if (prepaidEnergyCredit <= 0.0D || !Double.isFinite(prepaidEnergyCredit)) return;
        long tick = TickHandler.instance().getCurrentTick();
        long elapsed = tick - lastIdleEnergyRefundAttemptTick;
        if (lastIdleEnergyRefundAttemptTick != Long.MIN_VALUE && elapsed >= 0L && elapsed < 20L) return;
        lastIdleEnergyRefundAttemptTick = tick;
        double offered = prepaidEnergyCredit;
        try {
            double overflow = energyService.injectPower(offered, Actionable.MODULATE);
            if (!Double.isFinite(overflow) || overflow < -0.01D || overflow > offered + 0.01D) {
                logEnergyAccountingFailure("invalid idle-credit overflow " + overflow + " of " + offered, null);
                return;
            }
            double retained = Math.max(0.0D, overflow);
            if (retained != prepaidEnergyCredit) {
                prepaidEnergyCredit = retained;
                markCpuDirty();
            }
        } catch (RuntimeException failure) {
            logEnergyAccountingFailure("idle-credit refund failed for " + offered + " energy", failure);
        }
    }

    private void logEnergyAccountingFailure(String reason, @Nullable RuntimeException failure) {
        long tick = TickHandler.instance().getCurrentTick();
        long elapsed = tick - lastEnergyAccountingFailureLogTick;
        if (lastEnergyAccountingFailureLogTick != Long.MIN_VALUE && elapsed >= 0L && elapsed < 1200L) return;
        lastEnergyAccountingFailureLogTick = tick;
        if (failure == null) {
            LOGGER.error("ECO crafting energy accounting anomaly: {}", reason);
        } else {
            LOGGER.error("ECO crafting energy accounting anomaly: {}", reason, failure);
        }
    }

    private final class EnergyReservation {
        private final IEnergyService energyService;
        private final double reservedCredit;
        private final double networkDebit;
        private boolean settled;

        private EnergyReservation(IEnergyService energyService, double reservedCredit, double networkDebit) {
            this.energyService = energyService;
            this.reservedCredit = reservedCredit;
            this.networkDebit = networkDebit;
        }

        private void commit() {
            settled = true;
            if (reservedCredit > 0.0D) markCpuDirty();
        }

        private void refund() {
            if (!settled) {
                settled = true;
                restoreEnergyCredit(reservedCredit);
                refundEnergyOrRetainCredit(energyService, networkDebit);
            }
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
            if (current.executionRuntime != null) current.executionRuntime.onInventoryChanged(what);
            current.waitingFor.extract(what, accepted, Actionable.MODULATE);
            current.timeTracker.decrementItems(accepted, what.getType());
            markCpuDirty();
            stallDiagnostics.progress(TickHandler.instance().getCurrentTick());
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
            if (job != null && job.executionRuntime != null) job.executionRuntime.onInventoryChanged(what);
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
        resolvedDispatchLeases.clear();
        providerCursor.clear();
        resumeDispatchPattern = null;
        stallDiagnostics.reset();

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
        resolvedDispatchLeases.clear();
        resumeDispatchPattern = null;
        dispatchStrategy.reset();
        stallDiagnostics.reset();
        double restoredEnergyCredit = data.getDouble("prepaidEnergyCredit");
        prepaidEnergyCredit = Double.isFinite(restoredEnergyCredit) && restoredEnergyCredit > 0.0D
            ? restoredEnergyCredit : 0.0D;
        this.inventory.readFromNBT(data.getList("inventory", 10), registries);
        if (data.contains("job")) {
            var jobData = data.getCompound("job");
            this.job = new ExecutingCraftingJob(jobData, registries, this::postChange, this);
            IGrid grid = cpu.getGrid();
            if (grid != null) {
                // Publish the restored link only after the complete job decoded successfully. A failed restore stays
                // quarantined in the threading core and must not leave an orphan link in the crafting service.
                ((CraftingService) grid.getCraftingService()).addLink(this.job.link);
            }
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
        if (prepaidEnergyCredit > 0.0D && Double.isFinite(prepaidEnergyCredit)) {
            data.putDouble("prepaidEnergyCredit", prepaidEnergyCredit);
        } else {
            data.remove("prepaidEnergyCredit");
        }
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
                        count = saturatingAdd(count, saturatingMultiply(output.amount(), t.getValue().value));
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
        addAllSaturating(out, this.inventory.list);
        if (this.job != null) {
            addAllSaturating(out, job.waitingFor.list);
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    long amount = saturatingMultiply(output.amount(), t.getValue().value);
                    out.set(output.what(), saturatingAdd(out.get(output.what()), amount));
                }
            }
        }
    }

    /** Collects only items physically owned by this CPU, excluding planned and in-flight outputs. */
    public void getOwnedItems(KeyCounter out) {
        out.addAll(this.inventory.list);
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static long saturatingAdd(long left, long right) {
        if (left <= 0L) return Math.max(0L, right);
        if (right <= 0L) return left;
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static void addAllSaturating(KeyCounter target, KeyCounter source) {
        for (var entry : source) {
            target.set(entry.getKey(), saturatingAdd(target.get(entry.getKey()), entry.getLongValue()));
        }
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
            stallDiagnostics.progress(TickHandler.instance().getCurrentTick());
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
