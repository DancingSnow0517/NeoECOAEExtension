package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.Iterator;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional-neutral contract for storage cells that can participate in resumable infinite-domain migration.
 * Implementations may belong to optional integrations; common storage code must depend only on this contract.
 */
public interface IECOStorageMigrationCell extends IECOStorageCell {
    void getMigrationStacks(KeyCounter out);

    default long getMigrationAmount(AEKey key) {
        KeyCounter contents = new KeyCounter();
        getMigrationStacks(contents);
        return contents.get(key);
    }

    /** Flushes contents before the controller saves the source seal or destination receipt to its chunk. */
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
