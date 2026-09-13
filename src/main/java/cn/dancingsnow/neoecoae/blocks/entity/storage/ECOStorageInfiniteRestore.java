package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.util.NEMath;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Plans and resumes durable restoration to normal cells, including safe component extraction. */
final class ECOStorageInfiniteRestore {
    private static final long INFINITE_RESTORE_MARGIN_NUMERATOR = 95L;
    private static final long INFINITE_RESTORE_MARGIN_DENOMINATOR = 100L;
    private long extractionCheckTick = Long.MIN_VALUE;
    private String extractionCheckReason;
    private RestorePlan activeRestorePlan;
    private final java.util.ArrayDeque<AEKey> restoreQueue = new java.util.ArrayDeque<>();

    private final ECOStorageSystemBlockEntity host;

    ECOStorageInfiniteRestore(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    boolean isRestoring() {
        return activeRestorePlan != null;
    }

    void invalidateExtractionCheck() {
        extractionCheckTick = Long.MIN_VALUE;
    }

    IItemHandlerModifiable componentItemHandler(IItemHandlerModifiable delegate) {
        return new InfiniteComponentItemHandler(delegate);
    }

    void restoreInfiniteDomainToNormalStorageIfPossible() {
        RestorePlan plan = createInfiniteRestorePlan(true);
        if (plan.canRestore()) {
            restoreInfiniteDomainToNormalStorage(plan);
        } else {
            host.storageFaults().report("restore", plan.reason(), host.getLevel().getGameTime());
        }
    }

    private RestorePlan createInfiniteRestorePlan(boolean enforceMargin) {
        if (activeRestorePlan != null) return activeRestorePlan;
        ECOInfiniteStorageEngine engine = host.getInfiniteEngine();
        if (engine == null) {
            return RestorePlan.blocked("missing infinite storage engine");
        }
        if (!engine.canExitOrRestore()) {
            return RestorePlan.blocked(
                "infinite storage domain is " + engine.status() + " and cannot be restored to normal storage");
        }
        if (engine.isEmpty()) {
            return RestorePlan.allowed(List.of());
        }
        if (host.getCluster() == null || host.infiniteDomainId() == null) {
            return RestorePlan.blocked("missing storage cluster or infinite domain");
        }
        if (engine.hasHugeStacks()) {
            return RestorePlan.blocked("domain contains stacks larger than a normal storage cell can hold");
        }

        List<RestoreTarget> targets = createRestoreTargets(host.infiniteDomainId());
        if (targets.isEmpty()) {
            return RestorePlan.blocked("no L9 storage matrices are available");
        }
        java.util.Set<UUID> targetIds = new java.util.HashSet<>();
        for (RestoreTarget target : targets) {
            if (!targetIds.add(target.identity)) return RestorePlan.blocked("duplicate restore target identity");
        }

        KeyCounter pending = new KeyCounter();
        engine.getRestoreStacks(pending);
        IActionSource source = IActionSource.ofMachine(host);
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            AEKey key = entry.getKey();
            if (!targetIds.containsAll(engine.restoreTargetIds(key))) {
                return RestorePlan.blocked("an original restore target is missing; return its sealed matrix to resume");
            }
            HugeAmount amount = engine.getRestoreAmount(key);
            if (amount.compareTo(HugeAmount.of(Long.MAX_VALUE)) > 0) {
                return RestorePlan.blocked("domain contains stacks larger than a normal storage cell can hold");
            }
            var savedPlan = engine.restorePlan(key);
            if (!savedPlan.isEmpty()) {
                for (RestoreTarget target : targets) {
                    var goal = savedPlan.get(target.identity);
                    if (goal == null) continue;
                    long current = target.simulatedContents().get(key);
                    if (current < goal.before() || current > goal.after()) {
                        return RestorePlan.blocked("restore target contents differ from the saved transfer plan");
                    }
                    target.addSimulated(key, goal.after() - current);
                }
                continue;
            }
            long remaining = amount.toLongSaturated();
            for (RestoreTarget target : targets) {
                UUID transactionId = host.migrationTransactionId(host.infiniteDomainId(), target.drive(), key,
                    amount.toLongSaturated(), "from-domain");
                long alreadyRestored = Math.min(remaining, target.drive().getRestoreReceipt(transactionId));
                remaining -= alreadyRestored;
                if (remaining <= 0L) {
                    break;
                }
                long inserted = simulateInsertForRestore(target, key, remaining, source);
                if (inserted > 0L) {
                    target.addSimulated(key, inserted);
                }
                remaining -= inserted;
                if (remaining <= 0L) {
                    break;
                }
            }
            if (remaining > 0L) {
                return RestorePlan.blocked("normal storage matrices do not have enough compatible capacity");
            }
        }
        if (enforceMargin && !restoreTargetsHaveMargin(targets)) {
            return RestorePlan.blocked("normal storage matrices would exceed the reserve margin");
        }
        return RestorePlan.allowed(targets);
    }

    private List<RestoreTarget> createRestoreTargets(UUID domainId) {
        List<RestoreTarget> targets = new ArrayList<>();
        if (host.getCluster() == null) {
            return targets;
        }
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (!ECOInfiniteStorageMember.isMemberOf(stack, domainId)) {
                continue;
            }
            ItemStack simulationStack = stack.copy();
            ECOInfiniteStorageMember.clearMember(simulationStack);
            IECOStorageCell simulatedCell = ECOStorageCells.getCellInventory(simulationStack, null);
            if (simulatedCell instanceof IECOStorageMigrationCell
                && simulatedCell.getTier() == ECOTier.L9
                && simulatedCell.isInfiniteStorageEligible()) {
                KeyCounter simulatedContents = new KeyCounter();
                simulatedCell.getAvailableStacks(simulatedContents);
                targets.add(new RestoreTarget(drive, simulatedCell, simulatedContents));
            }
        }
        return targets;
    }

    private boolean restoreTargetsHaveMargin(List<RestoreTarget> targets) {
        long used = 0L;
        long total = 0L;
        for (RestoreTarget target : targets) {
            used = NEMath.saturatingAdd(used, getUsedBytesForRestore(target));
            total = NEMath.saturatingAdd(total, target.simulatedCell().getTotalBytes());
        }
        if (total <= 0L) {
            return false;
        }
        long reserved = Math.max(
            1L,
            total / INFINITE_RESTORE_MARGIN_DENOMINATOR
                * (INFINITE_RESTORE_MARGIN_DENOMINATOR - INFINITE_RESTORE_MARGIN_NUMERATOR)
        );
        return used <= total - reserved;
    }

    private long simulateInsertForRestore(
        RestoreTarget target,
        AEKey key,
        long amount,
        IActionSource source
    ) {
        IECOStorageCell cell = target.simulatedCell();
        if (cell instanceof IECOStorageMigrationCell migrationCell) {
            return migrationCell.simulateInsertForMigration(
                key, amount, target.simulatedContents(), target.simulatedTypes, target.simulatedAmount);
        }
        // Unknown handlers must still be probed without mutation. Such handlers are allowed to return a conservative
        // capacity; the real restore below remains authoritative and verifies the final aggregate.
        return insertForRestore(cell, key, amount, Actionable.SIMULATE, source);
    }

    private long getUsedBytesForRestore(RestoreTarget target) {
        IECOStorageCell cell = target.simulatedCell();
        if (cell instanceof IECOStorageMigrationCell migrationCell) {
            return migrationCell.getUsedBytesForMigration(target.simulatedContents());
        }
        return cell.getUsedBytes();
    }

    private void restoreInfiniteDomainToNormalStorage(RestorePlan plan) {
        if (!(host.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ECOInfiniteStorageEngine engine = host.getInfiniteEngine();
        if (engine == null || engine.isEmpty()) {
            host.exitInfiniteModeIfSafe();
            return;
        }
        if (host.infiniteDomainId() == null) {
            return;
        }
        if (!engine.canExitOrRestore()) return;
        java.util.Set<UUID> targetIds = new java.util.HashSet<>();
        for (RestoreTarget target : plan.targets()) {
            ECODriveBlockEntity drive = target.drive();
            if (drive.isRemoved() || host.getCluster() == null || !host.getCluster().getDrives().contains(drive)
                || serverLevel.getBlockEntity(drive.getBlockPos()) != drive
                || !ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), host.infiniteDomainId())
                || !target.identity.equals(ECOInfiniteStorageMember.identity(drive.getCellStack()))
                || !targetIds.add(target.identity)) {
                activeRestorePlan = null;
                restoreQueue.clear();
                host.storageFaults().report("restore", "Restore target changed; waiting for original sealed matrices", host.getLevel().getGameTime());
                return;
            }
        }
        if (activeRestorePlan == null) {
            KeyCounter pending = new KeyCounter();
            engine.getRestoreStacks(pending);
            Map<AEKey, UUID> transactions = new HashMap<>();
            Map<AEKey, Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts>> goals = new HashMap<>();
            for (var entry : pending) {
                AEKey key = entry.getKey();
                UUID transaction = engine.restoreTransaction(key);
                transactions.put(key, transaction == null ? UUID.randomUUID() : transaction);
                var savedPlan = engine.restorePlan(key);
                if (!savedPlan.isEmpty()) {
                    goals.put(key, savedPlan);
                    continue;
                }
                Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> amounts = new HashMap<>();
                for (RestoreTarget target : plan.targets()) {
                    var cell = (IECOStorageMigrationCell) target.drive().getCellInventory();
                    long before = cell.getMigrationAmount(key);
                    long after = target.simulatedContents().get(key);
                    UUID oldReceipt = host.migrationTransactionId(host.infiniteDomainId(), target.drive(), key,
                        entry.getLongValue(), "from-domain");
                    long transferred = Math.addExact(target.drive().getRestoreReceipt(oldReceipt), after - before);
                    amounts.put(target.identity, new ECOInfiniteStorageEngine.RestoreTargetAmounts(before, after, transferred));
                }
                goals.put(key, amounts);
            }
            // Freeze all source keys before any target changes. The durable target quantities also make a crash
            // between an external cell's SavedData write and its chunk receipt recoverable without reinsertion.
            if (!engine.reserveRestores(transactions, targetIds, goals)) return;
            restoreQueue.clear();
            for (var entry : pending) restoreQueue.addLast(entry.getKey());
            activeRestorePlan = plan;
            IStorageProvider.requestUpdate(host.getMainNode());
        }
        while (!restoreQueue.isEmpty() && engine.getRestoreAmount(restoreQueue.peekFirst()).isZero()) restoreQueue.removeFirst();
        Map<AEKey, UUID> completed = new HashMap<>();
        java.util.Set<IECOStorageMigrationCell> changedCells = new java.util.HashSet<>();
        IActionSource source = IActionSource.ofMachine(host);
        long started = System.nanoTime();
        for (AEKey key : restoreQueue) {
            if (completed.size() >= NEConfig.storageTransferKeysPerTick
                || (!completed.isEmpty() && System.nanoTime() - started >= host.currentStorageBudget())) break;
            var goals = engine.restorePlan(key);
            for (RestoreTarget target : plan.targets()) {
                var goal = goals.get(target.identity);
                if (goal == null) continue;
                var cell = (IECOStorageMigrationCell) target.drive().getCellInventory();
                if (!ECOInfiniteStorageTransfer.restoreTarget(cell, key, goal, source)) {
                    host.storageFaults().report("restore", "Waiting for compatible target capacity", host.getLevel().getGameTime());
                    return;
                }
                UUID receipt = host.migrationTransactionId(host.infiniteDomainId(), target.drive(), key,
                    engine.getRestoreAmount(key).toLongSaturated(), "from-domain");
                target.drive().putRestoreReceipt(receipt, goal.transferred());
                changedCells.add(cell);
            }
            completed.put(key, engine.restoreTransaction(key));
        }
        if (!completed.isEmpty()) {
            for (var cell : changedCells) cell.persistMigrationContents(serverLevel);
            serverLevel.getChunkSource().save(true);
            if (!engine.finishRestores(completed)) return;
            restoreQueue.removeIf(completed::containsKey);
            host.storageFaults().recovered("restore");
            IStorageProvider.requestUpdate(host.getMainNode());
        }
        if (restoreQueue.isEmpty()) {
            activeRestorePlan = null;
            host.exitInfiniteModeIfSafe();
        }
    }

    private long insertForRestore(
        IECOStorageCell cell,
        AEKey key,
        long amount,
        Actionable mode,
        IActionSource source
    ) {
        if (cell instanceof IECOStorageMigrationCell migrationCell) {
            return migrationCell.insertForMigration(key, amount, mode, source);
        }
        return cell.insert(key, amount, mode, source);
    }

    @Nullable
    public String blockedInfiniteComponentExtractionReason() {
        ItemStack stack = host.infiniteComponentInventory().getStackInSlot(0);
        if (!host.hasRequiredInfiniteComponents(stack) || !host.storageHostMode().isInfiniteState()) {
            return null;
        }
        if (host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            return "infinite storage migration is still in progress";
        }
        long tick = host.getLevel() == null ? 0L : host.getLevel().getGameTime();
        if (extractionCheckTick == Long.MIN_VALUE || tick - extractionCheckTick >= 20L) {
            extractionCheckTick = tick;
            RestorePlan plan = createInfiniteRestorePlan(true);
            extractionCheckReason = plan.canRestore() ? null : plan.reason();
        }
        return extractionCheckReason;
    }

    private static final class RestoreTarget {
        private final ECODriveBlockEntity drive;
        private final IECOStorageCell simulatedCell;
        private final KeyCounter simulatedContents;
        private final UUID identity;
        private long simulatedTypes;
        private long simulatedAmount;
        private RestoreTarget(ECODriveBlockEntity drive, IECOStorageCell cell, KeyCounter contents) {
            this.drive = drive;
            this.simulatedCell = cell;
            this.simulatedContents = contents;
            this.identity = ECOInfiniteStorageMember.identity(drive.getCellStack());
            drive.setChanged();
            for (var entry : contents) {
                if (entry.getLongValue() > 0L) {
                    simulatedTypes++;
                    simulatedAmount = NEMath.saturatingAdd(simulatedAmount, entry.getLongValue());
                }
            }
        }
        private ECODriveBlockEntity drive() { return drive; }
        private IECOStorageCell simulatedCell() { return simulatedCell; }
        private KeyCounter simulatedContents() { return simulatedContents; }
        private void addSimulated(AEKey key, long amount) {
            if (amount <= 0L) return;
            if (simulatedContents.get(key) == 0L) simulatedTypes++;
            simulatedAmount = NEMath.saturatingAdd(simulatedAmount, amount);
            simulatedContents.add(key, amount);
        }
    }

    private record RestorePlan(boolean canRestore, List<RestoreTarget> targets, String reason) {
        private static RestorePlan allowed(List<RestoreTarget> targets) {
            return new RestorePlan(true, List.copyOf(targets), "");
        }

        private static RestorePlan blocked(String reason) {
            return new RestorePlan(false, List.of(), reason);
        }
    }

    private final class InfiniteComponentItemHandler implements IItemHandlerModifiable {
        private final IItemHandlerModifiable delegate;

        private InfiniteComponentItemHandler(IItemHandlerModifiable delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStack stack = delegate.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            // The component's serialized data can be very large. The UI only needs the item
            // identity/icon; sending its full tag through the open-screen advanced packet can
            // overflow LDLib's fixed buffer. Keep the authoritative stack in the delegate.
            return new ItemStack(stack.getItem(), stack.getCount());
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot != 0 || (!stack.isEmpty() && !ECOStorageSystemBlockEntity.isInfiniteComponent(stack))) {
                return;
            }
            if (slot == 0) {
                ItemStack current = delegate.getStackInSlot(slot);
                if (host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE
                    && host.hasRequiredInfiniteComponents(current)
                    && !host.hasRequiredInfiniteComponents(stack)) {
                    return;
                }
                if (host.storageHostMode().isInfiniteState()
                    && host.hasRequiredInfiniteComponents(current)
                    && !host.hasRequiredInfiniteComponents(stack)) {
                    RestorePlan plan = createInfiniteRestorePlan(true);
                    if (!plan.canRestore()) {
                        return;
                    }
                    host.requestInfiniteExit();
                    host.setChanged();
                    restoreInfiniteDomainToNormalStorage(plan);
                    if (host.storageHostMode().isInfiniteState()) {
                        return;
                    }
                }
            }
            delegate.setStackInSlot(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !ECOStorageSystemBlockEntity.isInfiniteComponent(stack)) {
                return stack;
            }
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = delegate.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!host.storageHostMode().isInfiniteState() || !host.hasRequiredInfiniteComponents(stack)) {
                return delegate.extractItem(slot, amount, simulate);
            }

            if (host.storageHostMode() == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
                return ItemStack.EMPTY;
            }

            RestorePlan plan = createInfiniteRestorePlan(true);
            if (!plan.canRestore()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                host.requestInfiniteExit();
                host.setChanged();
                restoreInfiniteDomainToNormalStorage(plan);
                if (host.storageHostMode().isInfiniteState()) {
                    return ItemStack.EMPTY;
                }
            }
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && ECOStorageSystemBlockEntity.isInfiniteComponent(stack) && delegate.isItemValid(slot, stack);
        }
    }
}
