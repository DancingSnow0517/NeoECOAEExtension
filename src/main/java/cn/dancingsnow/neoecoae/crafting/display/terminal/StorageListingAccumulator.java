package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.storage.SaturatingStackAccumulator;
import java.util.Map;
import java.util.function.Consumer;

/** Reuses one mounted-inventory counter per network; ordinary listings need only one merge pass. */
public final class StorageListingAccumulator {
    private final KeyCounter contribution = new KeyCounter();

    public void append(MEStorage storage, KeyCounter output, Consumer<KeyCounter> listing) {
        if (!ExactAmountCollector.isActive() && storage instanceof ExactAmountSource source
                && source.neoecoae$hasSaturatingListing()) {
            listing.accept(output);
            return;
        }
        if (output.isEmpty()) {
            // The first nonempty mount has nothing to merge with. Listing into an isolated
            // counter and rebuilding every submap in output doubles work for a one-mount network.
            try {
                if (ExactAmountCollector.isActive()) ExactAmountCollector.collect(storage, output, listing);
                else {
                    listing.accept(output);
                    sanitize(storage, output);
                }
            } catch (RuntimeException | Error failure) {
                // Preserve the previous empty output if a third-party listing only partly completed.
                output.clear();
                throw failure;
            }
            return;
        }
        contribution.clear();
        // KeyCounter.addAll can clone a source submap efficiently when the destination
        // has no submap. Keeping an empty variant map instead forces entry-by-entry
        // addition, which allocates more for inventories with many NBT variants.
        contribution.removeEmptySubmaps();
        if (ExactAmountCollector.isActive()) {
            ExactAmountCollector.collect(storage, contribution, listing);
            SaturatingStackAccumulator.addAll(output, contribution);
            return;
        }

        listing.accept(contribution);
        Map<AEKey, ExactAmount> exactAmounts = null;
        for (var entry : contribution) {
            long amount = entry.getLongValue();
            if (amount < 0) {
                // Malformed third-party projections are rare. Query exact values only on
                // this cold path, so valid ordinary listings never traverse their source twice.
                if (exactAmounts == null) exactAmounts = ExactAmountCollector.sourceAmounts(storage);
                ExactAmount exact = exactAmounts.get(entry.getKey());
                if (exact == null) continue;
                amount = exact.longValueSaturated();
            }
            if (amount != 0) SaturatingStackAccumulator.add(output, entry.getKey(), amount);
        }
    }

    private static void sanitize(MEStorage storage, KeyCounter output) {
        Map<AEKey, ExactAmount> exactAmounts = null;
        boolean removeZeros = false;
        for (var entry : output) {
            long amount = entry.getLongValue();
            if (amount < 0) {
                if (exactAmounts == null) exactAmounts = ExactAmountCollector.sourceAmounts(storage);
                ExactAmount exact = exactAmounts.get(entry.getKey());
                long repaired = exact == null ? 0 : exact.longValueSaturated();
                output.set(entry.getKey(), repaired);
                removeZeros |= repaired == 0;
            } else removeZeros |= amount == 0;
        }
        if (removeZeros) output.removeZeros();
    }
}
