package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.features.IPlayerRegistry;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingLifecycle;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingWorkerRecovery;
import cn.dancingsnow.neoecoae.config.NEConfig;
import appeng.core.AELog;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Coordinates CPU job ownership, attachment lifetime and terminal transitions. */
final class ECOCraftingJobLifecycleController {
    private final ECOCraftingCPULogic host;

    ECOCraftingJobLifecycleController(ECOCraftingCPULogic host) {
        this.host = host;
    }

    ICraftingSubmitResult submit(IGrid grid, ICraftingPlan plan, IActionSource src,
                                 @Nullable ICraftingRequester requester) {
        if (host.getJob() != null) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (!host.cpu.isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (host.cpu.getAvailableStorage() < plan.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }

        if (!host.getInventory().list.isEmpty() && host.exactInventory().isEnabled()) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (!host.getInventory().list.isEmpty()) {
            AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");
        }

        var attachedPlanningResult = (Object) plan instanceof ECOCraftingPlanDiagnostics diagnostics
                ? diagnostics.neoecoae$getPlanningResult() : null;
        var contract = ECOPlanningResultRegistry.resolveContract(plan, attachedPlanningResult);
        var executionPlan = contract == null ? ECOPlanningResultRegistry.resolveExecutionPlan(plan)
                : contract.executionPlan();
        if (plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan exact)
            executionPlan = exact.execution();

        // Imported automatic wrappers must be validated before taking network materials.
        if (executionPlan == null && !(plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan)) {
            try {
                cn.dancingsnow.neoecoae.compat.extendedaeplus.ECOExternalPatternNormalization.normalize(plan.patternTimes());
            } catch (RuntimeException invalidWrapper) {
                AELog.warn("Cannot normalize imported crafting plan: %s", invalidWrapper.getMessage());
                return CraftingSubmitResult.INCOMPLETE_PLAN;
            }
        }

        host.exactInventory().setEnabled(plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan);
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(
                plan, grid, host.getInventory(), src);
        if (missingIngredient != null) {
            return CraftingSubmitResult.missingIngredient(missingIngredient);
        }

        var playerId = src.player()
                .map(player -> player instanceof ServerPlayer serverPlayer
                        ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(
                CraftingCpuHelper.generateLinkData(craftId, requester == null, false), host.cpu);
        var job = new ExecutingCraftingJob(
                plan, executionPlan, host::postChange, linkCpu, playerId);
        host.setJobFromLifecycle(job);
        if (job.exactOrder && linkCpu.isStandalone())
            linkCpu.setNexus(new appeng.crafting.CraftingLinkNexus(linkCpu.getCraftingID()));
        var admission = cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest.forSubmission(plan);
        if (admission != null) {
            host.bigOrder.start(admission, src);
        }
        host.outputDeliveryForPersistence().clearPendingFinalOutputs();
        initializeJobAttachments();

        var taskScheduler = host.taskSchedulerForLifecycle();
        taskScheduler.resetDispatchState();
        taskScheduler.bindDiagnostics(craftId, appeng.hooks.ticking.TickHandler.instance().getCurrentTick());
        // Publish planned outputs immediately so AE2 can display and cancel the new job before the first machine tick.
        var initialStatusItems = new appeng.api.stacks.KeyCounter();
        host.getAllItems(initialStatusItems);
        for (var entry : initialStatusItems) {
            host.postChange(entry.getKey());
        }

        host.markCpuDirty();
        host.notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        if (requester != null) {
            var linkReq = new CraftingLink(
                    CraftingCpuHelper.generateLinkData(craftId, false, true), requester);
            var craftingService = (CraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkReq);
            ECOCraftingLifecycle.fireJobStarted(host.createJobContext(job));
            return CraftingSubmitResult.successful(linkReq);
        }

        ECOCraftingLifecycle.fireJobStarted(host.createJobContext(job));
        return CraftingSubmitResult.successful(null);
    }

    void finish(boolean success) {
        var finishingJob = host.getJob();
        if (finishingJob == null) {
            return;
        }
        if (success && host.bigOrder.finishChild()) return;
        var context = host.createJobContext(finishingJob);
        long remainingAmount = Math.max(0L, finishingJob.remainingAmount);
        long completedAmount = Math.max(0L, context.requestedAmount() - remainingAmount);
        var result = new ECOCraftingJobResult(
                success ? ECOCraftingJobResult.Status.SUCCESS : ECOCraftingJobResult.Status.CANCELLED,
                context.requestedAmount(), completedAmount, remainingAmount);

        ECOCraftingJobLifecycle.finish(host.cpu.getLevel(), finishingJob.link.getCraftingID(), success);
        if (success) {
            finishingJob.link.markDone();
            ECOCraftingWorkerRecovery.releaseCompletedOutputs(
                    host.cpu.getGrid(), finishingJob.link.getCraftingID());
        } else {
            finishingJob.link.cancel();
        }

        finishingJob.waitingFor.clear();
        for (var entry : finishingJob.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                host.postChange(output.what());
            }
        }

        host.notifyJobOwner(
                finishingJob,
                success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);
        clearJobAttachments(result);
        host.setJobFromLifecycle(null);
        host.taskSchedulerForLifecycle().reset();
        host.storeItems();
        host.outputDeliveryForPersistence().clearPendingFinalOutputs();
        ECOCraftingLifecycle.fireJobFinished(context, result);
        host.bigOrder.clear();
    }

    void cancel() {
        var current = host.getJob();
        if (current == null) {
            return;
        }
        UUID craftingJobId = current.link.getCraftingID();
        if (host.bigOrder.progress() != null) {
            host.bigOrder.cancel();
            ECOCraftingJobLifecycle.finish(host.cpu.getLevel(), craftingJobId, false);
            ECOCraftingWorkerRecovery.recoverTerminatedInputs(host.cpu.getLevel(), craftingJobId);
            finish(false);
        } else {
            finish(false);
            ECOCraftingWorkerRecovery.recoverTerminatedInputs(host.cpu.getLevel(), craftingJobId);
        }
    }

    void initializeJobAttachments() {
        var job = host.getJob();
        if (job != null) {
            host.jobAttachmentsForLifecycle().initialize(host.createJobContext(job));
        }
    }

    void clearJobAttachments(ECOCraftingJobResult result) {
        host.jobAttachmentsForLifecycle().clear(result);
    }
}
