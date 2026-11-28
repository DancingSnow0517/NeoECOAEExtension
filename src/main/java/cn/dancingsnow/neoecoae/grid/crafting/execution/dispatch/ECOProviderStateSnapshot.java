package cn.dancingsnow.neoecoae.grid.crafting.execution.dispatch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Snapshot view of an AE2 mounted crafting provider entry used to avoid bumping
 * provider revisions on non-provider nodes or no-op refreshes.
 */
public interface ECOProviderStateSnapshot {
    ICraftingProvider neoecoae$provider();

    Set<AEKey> neoecoae$emitableItems();

    List<IPatternDetails> neoecoae$patterns();

    int neoecoae$priority();

    default boolean neoecoae$isEmpty() {
        return neoecoae$patterns().isEmpty() && neoecoae$emitableItems().isEmpty();
    }

    default boolean neoecoae$sameContent(ECOProviderStateSnapshot other) {
        return neoecoae$priority() == other.neoecoae$priority()
                && neoecoae$emitableItems().equals(other.neoecoae$emitableItems())
                && neoecoae$patterns().equals(other.neoecoae$patterns());
    }

    static boolean equivalent(@Nullable ECOProviderStateSnapshot before,
            @Nullable ECOProviderStateSnapshot after) {
        boolean beforeEmpty = before == null || before.neoecoae$isEmpty();
        boolean afterEmpty = after == null || after.neoecoae$isEmpty();
        if (beforeEmpty && afterEmpty) {
            return true;
        }
        if (beforeEmpty || afterEmpty) {
            return false;
        }
        return before.neoecoae$sameContent(after);
    }

    /** Exposes mounted node and global provider snapshots from AE2's provider registry. */
    interface Container {
        @Nullable ECOProviderStateSnapshot neoecoae$nodeState(@Nullable IGridNode node);

        @Nullable ECOProviderStateSnapshot neoecoae$globalState(@Nullable ICraftingProvider provider);
    }
}
