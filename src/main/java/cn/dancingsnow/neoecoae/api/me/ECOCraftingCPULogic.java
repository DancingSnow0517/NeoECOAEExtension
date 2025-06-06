package cn.dancingsnow.neoecoae.api.me;

import java.util.HashSet;
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
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
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

public class ECOCraftingCPULogic {

    final ECOCraftingCPU cpu;

    /**
     * Current job.
     */
    @Getter
    private ExecutingCraftingJob job = null;
    /**
     * Inventory.
     */
    @Getter
    private final ListCraftingInventory inventory = new ListCraftingInventory(ECOCraftingCPULogic.this::postChange);
    /**
     * Used crafting operations over the last 3 ticks.
     */
    private final int[] usedOps = new int[3];

    private final Set<Consumer<AEKey>> listeners = new HashSet<>();
    /**
     * True if the CPU is currently trying to clear its inventory but is not able to.
     */
    @Getter
    private boolean cantStoreItems = false;

    @Getter
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();

    @Getter
    private boolean markedForDeletion = false;

    public ECOCraftingCPULogic(ECOCraftingCPU cpu) {
        this.cpu = cpu;
    }

    public ICraftingSubmitResult trySubmitJob(
            IGrid grid, ICraftingPlan plan, IActionSource src, @Nullable ICraftingRequester requester) {
        // Already have a job.
        if (this.job != null) return CraftingSubmitResult.CPU_BUSY;
        // Check that the node is active.
        if (!cpu.isActive()) return CraftingSubmitResult.CPU_OFFLINE;
        // Check bytes.
        if (cpu.getAvailableStorage() < plan.bytes()) return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty()) AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        // Try to extract required items.
        var missingIngredient = CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, src);
        if (missingIngredient != null) return CraftingSubmitResult.missingIngredient(missingIngredient);

        // Set CPU link and job.
        var playerId = src.player()
                .map(p -> p instanceof ServerPlayer serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cpu);
        this.job = new ExecutingCraftingJob(plan, this::postChange, linkCpu, playerId);

        // Crafting Monitor unsupported
        // cpu.updateOutput(plan.finalOutput());
        cpu.markDirty();

        // TODO: post monitor difference?

        notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        // Non-standalone jobs need another link for the requester, and both links need to be submitted to the cache.
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
        // Don't tick if we're not active.
        if (!cpu.isActive()) return;
        cantStoreItems = false;
        // If we don't have a job, just try to dump our items.
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
        // Check if the job was cancelled.
        if (job.link.isCanceled()) {
            cancel();
            return;
        }

        var remainingOperations = cpu.getCoProcessors() + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        final var started = remainingOperations;

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
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - remainingOperations;
    }

    /**
     * Try to push patterns into available interfaces, i.e. do the actual crafting execution.
     *
     * @return How many patterns were successfully pushed.
     */
    public int executeCrafting(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level) {
        var job = this.job;
        if (job == null) return 0;

        var pushedPatterns = 0;

        var it = job.tasks.entrySet().iterator();
        taskLoop:
        while (it.hasNext()) {
            var task = it.next();
            if (task.getValue().value <= 0) {
                it.remove();
                continue;
            }

            var details = task.getKey();
            var expectedOutputs = new KeyCounter();
            var expectedContainerItems = new KeyCounter();
            // Contains the inputs for the pattern.
            @Nullable
            var craftingContainer = CraftingCpuHelper.extractPatternInputs(
                    details, inventory, level, expectedOutputs, expectedContainerItems);

            // Try to push to each provider.
            for (var provider : craftingService.getProviders(details)) {
                if (craftingContainer == null) break;
                if (provider.isBusy()) continue;

                var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);

                if (energyService.extractAEPower(patternPower, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                        < patternPower - 0.01) break;

                if (provider.pushPattern(details, craftingContainer)) {
                    energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    pushedPatterns++;

                    for (var expectedOutput : expectedOutputs) {
                        job.waitingFor.insert(
                                expectedOutput.getKey(), expectedOutput.getLongValue(), Actionable.MODULATE);
                    }
                    for (var expectedContainerItem : expectedContainerItems) {
                        job.waitingFor.insert(
                                expectedContainerItem.getKey(),
                                expectedContainerItem.getLongValue(),
                                Actionable.MODULATE);
                        job.timeTracker.addMaxItems(
                                expectedContainerItem.getLongValue(),
                                expectedContainerItem.getKey().getType());
                    }

                    cpu.markDirty();

                    task.getValue().value--;
                    if (task.getValue().value <= 0) {
                        it.remove();
                        continue taskLoop;
                    }

                    if (pushedPatterns == maxPatterns) {
                        break taskLoop;
                    }

                    // Prepare next inputs.
                    expectedOutputs.reset();
                    expectedContainerItems.reset();
                    craftingContainer = CraftingCpuHelper.extractPatternInputs(
                            details, inventory, level, expectedOutputs, expectedContainerItems);
                }
            }

            // Failed to push this pattern, reinject the inputs.
            if (craftingContainer != null) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, craftingContainer);
            }
        }

        return pushedPatterns;
    }

    /**
     * Called by the CraftingService with an Integer.MAX_VALUE priority to inject items that are being waited for.
     *
     * @return Consumed amount.
     */
    public long insert(AEKey what, long amount, Actionable type) {
        // also stop accepting items when the job is complete, i.e. to prevent re-insertion when pushing out
        // items during storeItems
        if (what == null || job == null) return 0;

        // Only accept items we are waiting for.
        var waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0;
        }

        // Make sure we don't insert more than what we are waiting for.
        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (type == Actionable.MODULATE) {
            job.timeTracker.decrementItems(amount, what.getType());
            job.waitingFor.extract(what, amount, Actionable.MODULATE);
            cpu.markDirty();
        }

        long inserted = amount;
        if (what.matches(job.finalOutput)) {
            // Final output is special: it goes directly into the requester
            inserted = job.link.insert(what, amount, type);

            // Note: we ignore any remainder (could be the entire input if there is no requester),
            // we already marked the items as done, and we might even finish the job.

            // This means that the job can be marked as finished even if some items were not actually inserted.
            // In some cases, repeated failed inserts of a fraction of the final output might prevent some recipes from
            // being pushed.
            // TODO: Look into fixing this, perhaps we could use the network monitor to check how much was really
            // TODO: inserted into the network.
            // TODO: Another solution is to wait until all recipes have been pushed before cancelling the job.

            if (type == Actionable.MODULATE) {
                // Update count and displayed CPU stack, and finish the job if possible.
                postChange(what);
                job.remainingAmount = Math.max(0, job.remainingAmount - amount);

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

    /**
     * Finish the current job.
     *
     * @param success True if the job is complete, false if it was cancelled.
     */
    private void finishJob(boolean success) {
        if (success) {
            job.link.markDone();
        } else {
            job.link.cancel();
        }

        // TODO: log

        // Clear waitingFor list and post all the relevant changes.
        job.waitingFor.clear();
        // Notify opened menus of cancelled scheduled tasks.
        for (var entry : job.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(
                job, success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        // Finish job.
        this.job = null;

        // Store all remaining items.
        this.storeItems();
    }

    /**
     * Cancel the current job.
     */
    public void cancel() {
        // No job to cancel :P
        if (job == null) return;

        finishJob(false);
    }

    /**
     * Tries to dump all locally stored items back into the storage network.
     */
    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        // Short-circuit if there is nothing to do.
        if (this.inventory.list.isEmpty()) return;

        var g = cpu.getGrid();
        if (g == null) return;

        var storage = g.getStorageService().getInventory();

        for (var entry : this.inventory.list) {
            this.postChange(entry.getKey());
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, cpu.getActionSource());

            // The network was unable to receive all of the items, i.e. no or not enough storage space left
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();

        cpu.markDirty();
    }

    private void postChange(AEKey what) {
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (var listener : listeners) {
            listener.accept(what);
        }
    }

    public boolean hasJob() {
        return this.job != null;
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
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
     * Register a listener that will receive stacks when either the stored items, await items or pending outputs change.
     * This is only used by the menu. Make sure to remove it by calling {@link #removeListener}.
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
     * Used by the menu to gather all the kinds of stored items.
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