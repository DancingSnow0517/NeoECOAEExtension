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
        failure = data.getStringOr("ecoInventoryReturnFailure", "");
        if (failure.isEmpty()) failure = null;
        if (data.getBooleanOr("ecoInventoryReturnRetiring", false)) host.markForDeletion();
    }

    void write(CompoundTag data) {
        data.putBoolean("ecoInventoryReturnRetiring", host.isMarkedForDeletion());
        if (failure == null) data.remove("ecoInventoryReturnFailure");
        else data.putString("ecoInventoryReturnFailure", failure);
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
