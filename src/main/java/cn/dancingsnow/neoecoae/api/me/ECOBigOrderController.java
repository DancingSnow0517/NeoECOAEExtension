package cn.dancingsnow.neoecoae.api.me;

import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.api.me.bigorder.*;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOBigOrderPlanner;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.concurrent.Future;

/** Keeps the published link alive while only one complete long child owns the execution ledger. */
final class ECOBigOrderController {
    private final ECOCraftingCPULogic host;
    private ECOBigCraftingOrder order;
    private AEKey goal;
    private ECOPlannerOptions options;
    private IActionSource source;
    private Future<ECOBigOrderPlanner.Answer> planning;
    private long safeSegment;
    private long safeRevision = Long.MIN_VALUE;
    private long planningRevision = Long.MIN_VALUE;
    // Display-only parent forecast. Never inserted into the child's execution or inventory ledger.
    private java.util.Map<AEKey, java.math.BigInteger> pendingPreview = java.util.Map.of();

    ECOBigOrderController(ECOCraftingCPULogic host) { this.host = host; }

    void start(ECOBigOrderRequest admission, IActionSource source) {
        this.order = new ECOBigCraftingOrder(host.getJob().link.getCraftingID(), admission.requested(), admission.forced());
        this.goal = admission.goal();
        this.options = admission.options();
        this.source = source;
        this.pendingPreview = admission.pendingPreview();
        bindStandaloneLink();
    }

    long pendingPreview(AEKey key) {
        if (order == null || host.getJob() == null || order.terminal()
                || order.state() == ECOBigOrderState.RUNNING_CHILD) return 0;
        var amount = pendingPreview.getOrDefault(key, java.math.BigInteger.ZERO);
        if (key.equals(goal)) amount = amount.max(order.remaining());
        return amount.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }

    void collectPendingPreview(appeng.api.stacks.KeyCounter out) {
        if (goal == null) return;
        for (var key : pendingPreview.keySet()) {
            long amount = pendingPreview(key);
            if (amount > 0) out.set(key, Math.max(out.get(key), amount));
        }
        long amount = pendingPreview(goal);
        if (amount > 0) out.set(goal, Math.max(out.get(goal), amount));
    }

    private void bindStandaloneLink() {
        var link = host.getJob().link;
        if (link.isStandalone()) link.setNexus(new appeng.crafting.CraftingLinkNexus(link.getCraftingID()));
    }

    ECOBigOrderProgress progress() {
        return order == null ? null : order.progress(host.getJob() == null ? 0 : host.getJob().remainingAmount);
    }

    /** True means the parent owns this tick and the empty waiting carrier must not execute. */
    boolean tick() {
        if (order == null) return false;
        var current = host.getJob();
        if (current == null) return true;
        if (current.link.isCanceled()) { host.cancel(); return true; }
        if (order.terminal()) return true;
        if (order.state() == ECOBigOrderState.RUNNING_CHILD) {
            if (current.permanentExecutionError != null) {
                order.fail(current.permanentExecutionError);
                host.markCpuDirty();
                return true;
            }
            return false;
        }
        if (current.suspended) return true;
        if (planning != null) {
            if (!planning.isDone()) return true;
            try {
                var answer = planning.get();
                planning = null;
                org.slf4j.LoggerFactory.getLogger("neoecoae").info(
                    "[big-order] Segment plan ready: order={}, status={}, capacity={}, fatal={}",
                    order.id(), answer.result().status(), answer.capacity(), answer.fatal());
                var liveGrid = host.cpu.getGrid();
                if (planningRevision != Long.MIN_VALUE && liveGrid != null
                        && liveGrid.getCraftingService() instanceof
                            cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision revision
                        && revision.neoecoae$getProviderRevision() != planningRevision
                        && !patternsStillAvailable(liveGrid.getCraftingService(), answer.result())) {
                    order.planning();
                    org.slf4j.LoggerFactory.getLogger("neoecoae").info(
                        "[big-order] Replanning after provider revision changed: order={}", order.id());
                    host.markCpuDirty();
                    return true;
                }
                if (answer.fatal()) {
                    order.fail(answer.result().status().name());
                } else if (answer.capacity() || answer.result().status()
                        != cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus.SUCCESS) {
                    order.waitFor(answer.capacity() ? ECOBigOrderState.WAITING_CAPACITY : ECOBigOrderState.WAITING_MATERIALS,
                            answer.capacity() ? "CAPACITY" : "MATERIALS");
                } else {
                    order.resetRetry();
                    var plan = answer.result().plan();
                    var grid = host.cpu.getGrid();
                    var action = actionSource();
                    if (grid == null || action == null) {
                        order.waitFor(ECOBigOrderState.WAITING_CAPACITY, "SOURCE_UNAVAILABLE");
                    } else if (!host.cpu.getCluster().replaceBigOrderPlan(host.cpu, plan)) {
                        order.waitFor(ECOBigOrderState.WAITING_CAPACITY, "CAPACITY");
                    } else {
                        var missing = CraftingCpuHelper.tryExtractInitialItems(plan, grid, host.getInventory(), action);
                        if (missing != null) {
                            releaseReservation();
                            order.waitFor(ECOBigOrderState.WAITING_MATERIALS, "MATERIALS");
                        } else {
                            var contract = answer.result().executionContract();
                            var child = new ExecutingCraftingJob(plan, contract.executionPlan(),
                                    host::postChange, current.link, current.playerId);
                            order.startChild(plan.finalOutput().amount());
                            org.slf4j.LoggerFactory.getLogger("neoecoae").info(
                                "[big-order] Child started: order={}, amount={}, patterns={}",
                                order.id(), plan.finalOutput().amount(), plan.patternTimes().size());
                            safeSegment = plan.finalOutput().amount();
                            safeRevision = planningRevision;
                            host.setJobFromLifecycle(child);
                            host.taskSchedulerForLifecycle().reset();
                            for (var key : pendingPreview.keySet()) host.postChange(key);
                            pendingPreview = java.util.Map.of();
                            host.postChange(goal);
                            for (var entry : host.getInventory().list) host.postChange(entry.getKey());
                            for (var entry : plan.patternTimes().keySet())
                                for (var output : entry.getOutputs()) host.postChange(output.what());
                        }
                    }
                }
            } catch (Exception failure) {
                planning = null;
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                order.fail("PLANNING_FAILED");
                org.slf4j.LoggerFactory.getLogger("neoecoae").warn("[big-order] Segment planning failed", failure);
            }
            host.markCpuDirty();
            if (order.terminal()) {
                for (var key : pendingPreview.keySet()) host.postChange(key);
                host.postChange(goal);
            }
            return true;
        }
        if (!order.tickRetry()) { host.markCpuDirty(); return true; }
        if (!flushIdleInventory()) {
            order.waitFor(ECOBigOrderState.WAITING_CAPACITY, "OUTPUT_STORAGE");
            host.markCpuDirty();
            return true;
        }
        var grid = host.cpu.getGrid();
        if (grid == null || actionSource() == null) {
            order.waitFor(ECOBigOrderState.WAITING_CAPACITY, "SOURCE_UNAVAILABLE");
            host.markCpuDirty();
            return true;
        }
        order.planning();
        try {
            planningRevision = grid.getCraftingService() instanceof
                    cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision revision
                    ? revision.neoecoae$getProviderRevision() : Long.MIN_VALUE;
            long candidate = order.candidate();
            if (planningRevision != Long.MIN_VALUE && planningRevision == safeRevision && safeSegment > 0)
                candidate = Math.min(candidate, safeSegment);
            planning = ECOBigOrderPlanner.begin(grid, goal, candidate, host.cpu.getAvailableStorage(), options);
            org.slf4j.LoggerFactory.getLogger("neoecoae").info(
                "[big-order] Planning segment: order={}, goal={}, candidate={}, bytes={}",
                order.id(), goal, candidate, host.cpu.getAvailableStorage());
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            order.waitFor(ECOBigOrderState.WAITING_CAPACITY, "PLANNER_BUSY");
        }
        host.markCpuDirty();
        return true;
    }

    private IActionSource actionSource() {
        var current = host.getJob();
        if (current != null && current.playerId != null) {
            var level = host.cpu.getLevel();
            var player = level == null ? null : IPlayerRegistry.getConnected(level.getServer(), current.playerId);
            if (player == null) return null;
            return IActionSource.ofPlayer(player, source == null ? null : source.machine().orElse(null));
        }
        return source != null ? source : host.cpu.getActionSource();
    }

    static boolean patternsStillAvailable(appeng.api.networking.crafting.ICraftingService service,
            cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult result) {
        if (result.status() != cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus.SUCCESS
                || result.plan() == null) return false;
        for (var pattern : result.plan().patternTimes().keySet()) {
            if (pattern.getOutputs().isEmpty()
                    || !service.getCraftingFor(pattern.getOutputs().getFirst().what()).contains(pattern)) return false;
        }
        return true;
    }

    java.util.Map<AEKey, java.math.BigInteger> exactPendingPreview() {
        if (order == null || host.getJob() == null || order.terminal()
                || order.state() == ECOBigOrderState.RUNNING_CHILD) return java.util.Map.of();
        var result = new java.util.HashMap<>(pendingPreview);
        result.merge(goal, order.remaining(), java.math.BigInteger::max);
        return java.util.Map.copyOf(result);
    }

    /** Called after actual final output delivery, before any child-level terminal side effects. */
    boolean finishChild() {
        if (order == null) return false;
        if (order.state() != ECOBigOrderState.RUNNING_CHILD) return true;
        var child = host.getJob();
        if (child == null || child.remainingAmount > 0 || !child.waitingFor.list.isEmpty()
                || host.taskSchedulerForLifecycle().hasPendingTasks(child)
                || host.outputDeliveryForPersistence().hasPendingFinalOutputs()
                || child.executionRuntime != null && !child.executionRuntime.isComplete()) {
            order.fail("CHILD_INCOMPLETE");
            host.markCpuDirty();
            return true;
        }
        if (order.completeChild()) return false;
        var previous = host.getJob();
        host.setJobFromLifecycle(new ExecutingCraftingJob(admission().carrier(), host::postChange, previous.link, previous.playerId));
        flushIdleInventory();
        for (var pattern : previous.tasks.keySet())
            for (var output : pattern.getOutputs()) host.postChange(output.what());
        host.outputDeliveryForPersistence().clearPendingFinalOutputs();
        host.taskSchedulerForLifecycle().reset();
        releaseReservation();
        host.postChange(goal);
        host.markCpuDirty();
        return true;
    }

    private ECOBigOrderRequest admission() {
        return new ECOBigOrderRequest(goal, order.remaining(), order.forced(), options);
    }
    private void releaseReservation() { host.cpu.getCluster().replaceBigOrderPlan(host.cpu, admission().carrier()); }

    /** Never let a storage callback failure discard the parent's only link carrier. */
    private boolean flushIdleInventory() {
        if (host.getInventory().list.isEmpty()) return true;
        var carrier = host.getJob();
        try {
            host.setJobFromLifecycle(null);
            host.storeItems();
            return host.getInventory().list.isEmpty();
        } catch (RuntimeException unavailable) {
            return false;
        } finally {
            host.setJobFromLifecycle(carrier);
        }
    }

    void cancel() {
        if (planning != null) { planning.cancel(true); planning = null; }
        if (order != null) order.cancel();
    }
    void clear() {
        cancel();
        var oldKeys = new HashSet<>(pendingPreview.keySet());
        if (goal != null) oldKeys.add(goal);
        pendingPreview = java.util.Map.of();
        order = null;
        source = null;
        goal = null;
        options = null;
        safeSegment = 0;
        safeRevision = Long.MIN_VALUE;
        planningRevision = Long.MIN_VALUE;
        oldKeys.forEach(host::postChange);
    }

    void write(CompoundTag data, HolderLookup.Provider registries) {
        if (order == null || host.getJob() == null) { data.remove("bigOrder"); return; }
        var tag = new CompoundTag();
        tag.putUUID("id", order.id());
        tag.putString("requested", order.requested().toString());
        tag.putString("completed", order.completed().toString());
        tag.putString("remaining", order.remaining().toString());
        tag.putBoolean("forced", order.forced());
        tag.putString("state", order.state().name());
        tag.putLong("child", order.childTarget());
        tag.putLong("retryTicks", order.retryTicks());
        tag.putInt("retryDelay", order.retryDelay());
        tag.putString("reason", order.reason());
        tag.put("goal", GenericStack.writeTag(registries, new GenericStack(goal, 1)));
        tag.putBoolean("cycles", options.cyclePlanningEnabled());
        tag.putBoolean("ignoreSubstitutions", options.ignorePatternSubstitutions());
        var fuzzy = new ListTag();
        options.fuzzyPlanningItemIds().forEach(id -> fuzzy.add(StringTag.valueOf(id.toString())));
        tag.put("fuzzy", fuzzy);
        var forecast = new ListTag();
        pendingPreview.forEach((key, amount) -> {
            var entry = new CompoundTag();
            entry.put("key", GenericStack.writeTag(registries, new GenericStack(key, 1)));
            entry.putString("amount", amount.toString());
            forecast.add(entry);
        });
        tag.put("pendingPreview", forecast);
        data.put("bigOrder", tag);
    }

    void read(CompoundTag data, HolderLookup.Provider registries) {
        clear();
        if (!data.contains("bigOrder")) return;
        var tag = data.getCompound("bigOrder");
        var restored = ECOBigCraftingOrder.restore(tag.getUUID("id"),
                ECOBigCraftingOrder.decode(tag.getString("requested")),
                ECOBigCraftingOrder.decode(tag.getString("completed")), tag.getBoolean("forced"),
                ECOBigOrderState.valueOf(tag.getString("state")), tag.getLong("child"),
                tag.getLong("retryTicks"), tag.getInt("retryDelay"), tag.getString("reason"));
        var current = host.getJob();
        if (!ECOBigCraftingOrder.decode(tag.getString("remaining")).equals(restored.remaining()))
            throw new IllegalArgumentException("Parent amount mismatch");
        if (current == null || !current.link.getCraftingID().equals(restored.id()))
            throw new IllegalArgumentException("Parent link mismatch");
        var target = GenericStack.readTag(registries, tag.getCompound("goal"));
        if (target == null || current.finalOutput == null || !target.what().equals(current.finalOutput.what())
                || restored.state() == ECOBigOrderState.RUNNING_CHILD
                && restored.childTarget() != current.finalOutput.amount())
            throw new IllegalArgumentException("Parent child mismatch");
        goal = target.what();
        var fuzzy = new HashSet<ResourceLocation>();
        var entries = tag.getList("fuzzy", 8);
        if (entries.size() > 10000) throw new IllegalArgumentException("Too many fuzzy items");
        for (int i = 0; i < entries.size(); i++) fuzzy.add(ResourceLocation.parse(entries.getString(i)));
        options = new ECOPlannerOptions(tag.getBoolean("cycles"), tag.getBoolean("ignoreSubstitutions"), fuzzy);
        order = restored;
        var forecast = tag.getList("pendingPreview", 10);
        if (forecast.size() > 100000) throw new IllegalArgumentException("Too many forecast items");
        var amounts = new java.util.HashMap<AEKey, java.math.BigInteger>();
        for (int i = 0; i < forecast.size(); i++) {
            var entry = forecast.getCompound(i);
            var stack = GenericStack.readTag(registries, entry.getCompound("key"));
            if (stack != null) amounts.put(stack.what(), ECOBigCraftingOrder.decode(entry.getString("amount")));
        }
        pendingPreview = java.util.Map.copyOf(amounts);
        bindStandaloneLink();
    }
}
