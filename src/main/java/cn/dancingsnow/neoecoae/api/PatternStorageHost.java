package cn.dancingsnow.neoecoae.api;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * A machine the grid's pattern catalog indexes: pattern slots plus the auxiliary containers they hold.
 *
 * <p>{@link AuxiliaryPatternHolder} covers only the container half, which is enough for a caller that just
 * needs to know what a disk holds. The catalog needs the slot half too - it counts slots, derives a content
 * revision to decide between an incremental and a full rebuild, and re-advertises a provider whose disks moved
 * without its slots changing. Those live here so the catalog can work against either kind of host.</p>
 *
 * <p>These are the questions the bus already answers; it implements this interface without change. A host that
 * is not a bus provides its own answers - the workstation interface's pattern slots belong to its provider, so
 * several of these forward there.</p>
 *
 * <p>The catalog polls {@link #getPatternContentRevision()} and {@link #getAuxiliaryRevision()} on every index
 * refresh, so both must be cheap. {@link #getPatternSlotInventory()} is what the catalog indexes: it must be
 * the real slots, not a terminal view that appends rows for the patterns on disks - a view would have those
 * patterns counted twice, once as slots and once as auxiliary contents.</p>
 */
public interface PatternStorageHost extends AuxiliaryPatternHolder {

    /**
     * @return the slots the catalog indexes as one-pattern-per-slot storage. Container slots are included;
     *         {@link #ownsAuxiliary} is what tells the catalog to skip them
     */
    InternalInventory getPatternSlotInventory();

    /**
     * @return how many of {@link #getPatternSlotInventory()}'s slots are pattern slots. Slots past this are
     *         not indexed
     */
    int getPatternSlotCount();

    /**
     * @return a token that changes when a slot's contents change. Distinct from
     *         {@link #getAuxiliaryRevision()}: a disk contents are not slot contents, and the catalog rebuilds
     *         different things for each
     */
    int getPatternContentRevision();

    /** Whether this slot pattern is currently offered by the host's crafting provider. */
    default boolean shouldIndexPattern(ItemStack pattern) {
        return true;
    }

    /**
     * Tells the grid to re-read this host's advertised patterns.
     *
     * <p>Called after auxiliary contents moved without the slot layout moving: nothing else would tell
     * autocrafting that the list it holds is stale.</p>
     */
    void refreshAdvertisedPatterns();

    /** @return the patterns this host offers autocrafting, auxiliary ones included */
    List<IPatternDetails> getAvailablePatterns();

    /** @return this host's position, used to order the catalog's writable storages deterministically */
    BlockPos getBlockPos();

    /**
     * Re-derives whatever {@link #getDecodedPatternDetails(int)} answers from.
     *
     * <p>Called before the catalog walks a host's records, so a host that caches decoded patterns can refresh
     * them once instead of per slot. A host that decodes on demand leaves this empty.</p>
     */
    void refreshPatternDetailsForCatalog();

    /**
     * @param slot a pattern slot
     * @return the decoded details of the pattern in {@code slot}, or {@code null} when it is empty or does
     *         not decode
     */
    @Nullable
    IPatternDetails getDecodedPatternDetails(int slot);

    /**
     * @param slot a pattern slot
     * @return search keywords for the pattern in {@code slot}; empty when a host keeps none
     */
    String getPatternSearchKeywords(int slot);
}
