package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.integration.ae2lt.WorkstationLightningInputs;

/** Transfers additional recipe costs into the same recoverable ownership ledger as CPU inputs. */
final class LargeWorkstationExtraInputs {
    private LargeWorkstationExtraInputs() {}

    /** Lock unpaid EHV to its real HV cost so saves, portions and refunds share one physical ledger. */
    static void prepareLightningSubstitution(KeyCounter missing, KeyCounter owned, MEStorage storage,
                                            IActionSource source, long portionCount, long craftCount,
                                            Runnable changed) {
        for (var extra : ECOFastPathStacks.copyCounter(missing)) {
            var substitute = WorkstationLightningInputs.highVoltageSubstitute(extra);
            if (substitute == null) continue;
            long requested = requestForPortion(owned.get(extra.what()), extra.amount(), portionCount, craftCount);
            if (requested == 0 || storage.extract(extra.what(), requested, Actionable.SIMULATE, source) >= requested) continue;
            long substituteRequested;
            try {
                long substituteMissing = Math.addExact(missing.get(substitute.what()), substitute.amount());
                substituteRequested = requestForPortion(owned.get(substitute.what()), substituteMissing, portionCount, craftCount);
            } catch (ArithmeticException overflow) {
                continue;
            }
            if (substituteRequested > 0
                && storage.extract(substitute.what(), substituteRequested, Actionable.SIMULATE, source) < substituteRequested) continue;
            missing.remove(extra.what(), extra.amount());
            missing.add(substitute.what(), substitute.amount());
            changed.run();
        }
    }

    private static long requestForPortion(long owned, long missing, long count, long totalCount) {
        long total = Math.addExact(owned, missing);
        long target = total / totalCount * count + total % totalCount * count / totalCount;
        return Math.max(0L, target - owned);
    }

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

    static boolean returnOwned(KeyCounter owned, MEStorage storage, IActionSource source, Runnable changed) {
        for (var stack : ECOFastPathStacks.copyCounter(owned)) {
            long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
            if (inserted < 0L || inserted > stack.amount()) {
                throw new IllegalStateException("Invalid owned input recovery insertion amount");
            }
            if (inserted > 0L) {
                owned.remove(stack.what(), inserted);
                changed.run();
            }
            if (inserted < stack.amount()) return false;
        }
        return true;
    }
}
