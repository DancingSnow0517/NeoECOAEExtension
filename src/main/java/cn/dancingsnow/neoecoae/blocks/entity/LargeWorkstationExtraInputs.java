package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;

/** Transfers additional recipe costs into the same recoverable ownership ledger as CPU inputs. */
final class LargeWorkstationExtraInputs {
    private LargeWorkstationExtraInputs() {}

    static boolean acquire(KeyCounter missing, KeyCounter owned, MEStorage storage, IActionSource source, Runnable changed) {
        for (var extra : ECOFastPathStacks.copyCounter(missing)) {
            long extracted = storage.extract(extra.what(), extra.amount(), Actionable.MODULATE, source);
            if (extracted < 0 || extracted > extra.amount()) throw new IllegalStateException("Invalid extra input extraction");
            if (extracted > 0) {
                owned.add(extra.what(), extracted);
                missing.remove(extra.what(), extracted);
                changed.run();
            }
        }
        for (var entry : missing) if (entry.getLongValue() > 0) return false;
        return true;
    }
}
