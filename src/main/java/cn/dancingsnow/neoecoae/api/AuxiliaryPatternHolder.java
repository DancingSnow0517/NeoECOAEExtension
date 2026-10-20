package cn.dancingsnow.neoecoae.api;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A machine that keeps some of its patterns in auxiliary containers - a pattern disk in a slot, say -
 * rather than one pattern per slot.
 *
 * <p>A host answers these from its own state. Both of this mod's pattern hosts - the pattern bus and the large
 * workstation interface - implement it, and both take their answers from the pattern-disk api through
 * {@code PatternDiskSupport}, so callers such as {@code PatternCatalog} and the pattern-preview path can ask
 * either kind without knowing which they are talking to.</p>
 *
 * <p>Implementations answer from their own state. The revision is what callers poll: it must change whenever
 * the set returned by {@link #getAuxiliaryEncodedPatterns()} would, and must be cheap - the catalog reads it
 * on every index refresh.</p>
 */
public interface AuxiliaryPatternHolder {

    /**
     * @param stack a stack in one of this host's slots
     * @return whether {@code stack} is one of this host's auxiliary containers, i.e. a slot holding it is not
     *         a slot holding one pattern. Container slots are hidden from the pattern index and from the
     *         terminal view's writable prefix, so this has to be answerable without reading the container's
     *         contents.
     */
    boolean ownsAuxiliary(ItemStack stack);

    /**
     * @return whether an auxiliary container here could take another pattern right now. A host whose slots are
     *         all occupied but whose disk still has room answers {@code true}, which is what keeps it in the
     *         catalog's writable set.
     */
    boolean hasAuxiliaryRoom();

    /**
     * @return every encoded pattern this host's auxiliary containers currently expose, in a stable order.
     *         Decoded details are derived from this list, so an unstable order would make the network
     *         advertise patterns in a different arrangement on each refresh.
     */
    List<ItemStack> getAuxiliaryEncodedPatterns();

    /**
     * @return search keywords for the patterns of {@link #getAuxiliaryEncodedPatterns()}, index-aligned with
     *         it. Shorter lists are tolerated; a missing entry reads as "no keywords".
     */
    default List<String> getAuxiliarySearchKeywords() {
        return List.of();
    }

    /**
     * @return a token that changes whenever {@link #getAuxiliaryEncodedPatterns()} or
     *         {@link #hasAuxiliaryRoom()} would answer differently. Polled, not pushed.
     */
    long getAuxiliaryRevision();

}
