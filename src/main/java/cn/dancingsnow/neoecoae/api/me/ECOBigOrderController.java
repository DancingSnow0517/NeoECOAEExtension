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

    ECOBigOrderController(ECOCraftingCPULogic host) { this.host = host; }

    void start(ECOBigOrderRequest admission, IActionSource source) {
        this.order = new ECOBigCraftingOrder(host.getJob().link.getCraftingID(), admission.requested(), admission.forced());
        this.goal = admission.goal();
        this.options = admission.options();
        this.source = source;
        bindStandaloneLink();
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
                var liveGrid = host.cpu.getGrid();
                if (planningRevision != Long.MIN_VALUE && liveGrid != null
                        && liveGrid.getCraftingService() instanceof
                            cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision revision
                        && revision.neoecoae$getProviderRevision() != planningRevision) {
                    order.planning();
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
                            safeSegment = plan.finalOutput().amount();
                            safeRevision = planningRevision;
                            host.setJobFromLifecycle(child);
                            host.taskSchedulerForLifecycle().reset();
                            for (var entry : plan.patternTimes().keySet())
                                for (var output : entry.getOutputs()) host.postChange(output.what());
                        }
                    }
                }
            } catch (Exception failure) {
                planning = null;
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                order.fail("PLANNING_FAILED");
            }
            host.markCpuDirty();
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
        order = null;
        source = null;
        goal = null;
        options = null;
        safeSegment = 0;
        safeRevision = Long.MIN_VALUE;
        planningRevision = Long.MIN_VALUE;
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
        bindStandaloneLink();
    }
}
