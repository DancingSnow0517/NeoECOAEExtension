package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.storage.StorageReturnBudget;
import java.math.BigInteger;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;

/** Bounded idle-CPU delivery. The existing CPU inventory remains the only persisted resource ledger. */
final class ECOCpuInventoryReturn {
    static final int KEYS_PER_TICK = 256;
    private static final long NANOS_PER_PASS = 1_000_000L;
    private static final long SEED_RETURN_INTERVAL = 20L;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ECOCraftingCPULogic.class);
    private long nextSeedReturnTick = Long.MIN_VALUE;
    private final ECOCraftingCPULogic host;
    private ArrayList<AEKey> keys = new ArrayList<>();
    private int cursor, used;
    private long tick = Long.MIN_VALUE;
    private String failure;

    ECOCpuInventoryReturn(ECOCraftingCPULogic host) { this.host = host; }
    String failure() { return failure; }

    void stop(String reason) {
        failure = reason;
        host.markCpuDirty();
    }

    void read(CompoundTag data) {
        keys.clear();
        cursor = used = 0;
        tick = Long.MIN_VALUE;
        failure = data.getString("ecoInventoryReturnFailure");
        if (failure.isEmpty()) failure = null;
        if (data.getBoolean("ecoInventoryReturnRetiring")) host.markForDeletion();
    }

    void write(CompoundTag data) {
        data.putBoolean("ecoInventoryReturnRetiring", host.isMarkedForDeletion());
        if (failure == null) data.remove("ecoInventoryReturnFailure");
        else data.putString("ecoInventoryReturnFailure", failure);
    }

    /**
     * A running job hands back cycle seeds it has finished borrowing instead of holding them until it ends, so
     * another order can start from them. Accounting matches the idle return: only the accepted amount leaves the CPU.
     */
    void returnReleasedSeeds(ExecutingCraftingJob job, long currentTick) {
        if (failure != null || currentTick < nextSeedReturnTick) return;
        nextSeedReturnTick = currentTick + SEED_RETURN_INTERVAL;
        if (job.executionRuntime == null || job.exactOrder || host.exactInventory().isEnabled()
                || job.permanentExecutionError != null
                || host.outputDeliveryForPersistence().hasPendingFinalOutputs()) return;
        var grid = host.cpu.getGrid();
        if (grid == null) return;
        var inventory = host.getInventory();
        var surplus = job.executionRuntime.releasedSeedSurplus(inventory);
        if (surplus.isEmpty()) return;
        var storage = grid.getStorageService().getInventory();
        var source = host.cpu.getActionSource();
        for (var entry : surplus.entrySet()) {
            AEKey key = entry.getKey();
            // The final output has its own destination and reserve.
            if (job.finalOutput != null && key.equals(job.finalOutput.what())) continue;
            long current = inventory.list.get(key);
            long offered = Math.min(current, entry.getValue().longValueSaturated());
            if (offered <= 0) continue;
            long accepted;
            try {
                accepted = storage.insert(key, offered, Actionable.MODULATE, source);
                if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid storage acceptance");
            } catch (RuntimeException uncertain) {
                // The receiver may have accepted before throwing. Never automatically replay an uncertain transfer.
                job.failPermanently("SEED_RETURN_UNCERTAIN");
                stop("SEED_RETURN_UNCERTAIN");
                LOGGER.error("ECO cycle seed acceptance is unknown; stopping job {}",
                    job.link.getCraftingID(), uncertain);
                return;
            }
            if (accepted == 0) continue;
            if (accepted == current) inventory.list.remove(key); else inventory.list.set(key, current - accepted);
            host.postChange(key);
            host.markCpuDirty();
        }
    }

    void run(boolean completePass) {
        if (failure != null) return;
        var grid = host.cpu.getGrid();
        if (grid == null) return;
        var inventory = host.getInventory();
        if (inventory.list.isEmpty()) { keys.clear(); cursor = 0; return; }
        var level = host.cpu.getLevel();
        long now = level == null ? tick + 1 : level.getServer() == null ? level.getGameTime() : level.getServer().getTickCount();
        if (now != tick) { tick = now; used = 0; }
        if (!completePass && used >= KEYS_PER_TICK) return;
        var shared = completePass ? null : StorageReturnBudget.forGrid(grid);
        if (shared != null && !shared.available(now)) return;
        if (completePass) cursor = keys.size();
        if (cursor == keys.size()) {
            if (keys.size() > 4096) keys = new ArrayList<>(); else keys.clear();
            for (var entry : inventory.list) if (entry.getLongValue() > 0) keys.add(entry.getKey());
            cursor = 0;
            if (keys.isEmpty()) { inventory.list.removeZeros(); return; }
        }
        var storage = grid.getStorageService().getInventory();
        var source = host.cpu.getActionSource();
        long deadline = System.nanoTime() + NANOS_PER_PASS;
        int processed = 0;
        boolean changed = false;
        try {
            while (cursor < keys.size() && (completePass || used < KEYS_PER_TICK)) {
                if (!completePass && processed > 0 && (processed & 15) == 0 && System.nanoTime() >= deadline) break;
                if (shared != null && !shared.acquire(now)) break;
                AEKey key = keys.get(cursor++);
                processed++;
                used++;
                long offered = Math.max(0L, inventory.list.get(key));
                if (offered == 0) continue;
                if (offered == Long.MAX_VALUE && host.exactInventory().isEnabled()) {
                    BigInteger amount = host.exactInventory().amount(key);
                    BigInteger accepted = ECOBigIntegerStorage.insert(storage, key, amount, Actionable.MODULATE, source);
                    if (accepted.signum() > 0) {
                        changed = true;
                        host.exactInventory().debitTransferred(key, accepted);
                    }
                } else {
                    long accepted = storage.insert(key, offered, Actionable.MODULATE, source);
                    if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid storage acceptance");
                    if (accepted == 0) continue;
                    long current = inventory.list.get(key);
                    if (accepted > current) throw new IllegalStateException("Transfer exceeds retained inventory");
                    changed = true;
                    if (accepted == current) inventory.list.remove(key); else inventory.list.set(key, current - accepted);
                    host.postChange(key);
                }
            }
        } catch (RuntimeException uncertain) {
            // The receiver may have accepted before throwing. Never automatically replay an uncertain transfer.
            failure = "Inventory return stopped: " + uncertain;
            host.markCpuDirty();
            throw uncertain;
        } finally {
            if (changed) host.markCpuDirty();
        }
    }
}
