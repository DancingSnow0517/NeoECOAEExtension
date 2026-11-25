package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.Iterator;
import net.minecraft.server.level.ServerLevel;

/**
 * Bulk migration and legacy reconciliation contract. Infinite hosts use its read-only contents checks without
 * migrating ordinary disk balances when their mode changes.
 */
public interface IECOStorageMigrationCell extends IECOStorageCell {
    void getMigrationStacks(KeyCounter out);

    default boolean isEmptyForInfiniteStorage() {
        KeyCounter contents = new KeyCounter();
        getMigrationStacks(contents);
        return contents.isEmpty();
    }

    default boolean canVerifyLegacyContents(ServerLevel level) { return true; }

    default long getMigrationAmount(AEKey key) {
        KeyCounter contents = new KeyCounter();
        getMigrationStacks(contents);
        return contents.get(key);
    }

    /**
     * Flushes contents before ECO checkpoints the owning chunk. The default covers item-component inventories.
     * A separate-file backend must override this and confirm its write, or throw, before returning. Merely marking
     * that backend dirty is insufficient: a source seal and a destination receipt depend on the corresponding data.
     */
    default void persistMigrationContents(ServerLevel level) { persist(); }

    default Iterator<Object2LongMap.Entry<AEKey>> migrationEntries() {
        KeyCounter contents = new KeyCounter();
        getMigrationStacks(contents);
        return contents.iterator();
    }

    void clearMigrationStacks();

    long insertForMigration(AEKey what, long amount, Actionable mode, IActionSource source);

    long simulateInsertForMigration(
        AEKey what,
        long amount,
        KeyCounter simulatedContents,
        long simulatedTypes,
        long simulatedAmount
    );

    long getUsedBytesForMigration(KeyCounter simulatedContents);
}
