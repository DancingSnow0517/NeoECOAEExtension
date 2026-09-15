package cn.dancingsnow.neoecoae.api;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;

/**
 * An optional pattern store that lives alongside a bus's slot inventory.
 *
 * <p>{@link cn.dancingsnow.neoecoae.grid.PatternCatalog} indexes a bus by physical slot, one pattern
 * per slot. Some integrations add a container whose <em>single</em> slot holds many encoded patterns -
 * a pattern disk, for example. Without an explicit channel those patterns are invisible to
 * {@link IECOPatternStorage#insertPattern(ItemStack)}: the catalog would treat them as absent from the
 * network and accept a duplicate, and the bus would never advertise them for autocrafting.</p>
 *
 * <p>An integration mod supplies the implementation and registers it through
 * {@link ECOCraftingPatternBusBlockEntity#setAuxiliaryPatternStore(AuxiliaryPatternStore)}. All methods
 * run on the server thread and are handed the bus they were asked about, so one instance serves every
 * bus on the grid.</p>
 *
 * <h2>Revision contract</h2>
 *
 * <p>{@link #revision(ECOCraftingPatternBusBlockEntity)} must change whenever the set of patterns
 * reachable through {@link #encodedPatterns(ECOCraftingPatternBusBlockEntity)} changes.</p>
 *
 * <p>Writing through the bus's inventory does notify it, so the bus knows its own slots changed. What it
 * cannot infer is the rest of a container's contents: one slot changing hands says nothing about the
 * other patterns that<em>same</em> container already carried. The revision is what tells the catalog to
 * re-derive the auxiliary side. Return a cheap counter or fingerprint rather than deep-comparing
 * contents - the catalog polls this on its index refresh.</p>
 */
public interface AuxiliaryPatternStore {

    /**
     * Whether {@code stack} is a container this store serves from - a pattern disk, for example.
     *
     * <p>Two callers depend on this. The bus slot filter otherwise takes encoded patterns and nothing
     * else, so a container would be refused entry. The catalog also keeps recognised containers out of
     * its slot index: one disk is not one pattern, and indexing it as one would surface it as an
     * unsupported entry in the network browser.</p>
     */
    boolean owns(ECOCraftingPatternBusBlockEntity bus, ItemStack stack);

    /**
     * Whether this store would take {@code pattern} right now.
     *
     * <p>Consulted by the bus before every {@link #insert}, to decide whether a pattern goes to the store
     * or to a slot. The grid also probes it across every bus before handing a pattern to any slot, so a
     * disk somewhere on the network wins over a free slot on an unrelated bus - which means this runs on
     * every upload attempt, not only on the bus the pattern ends up on. Must be free of side effects.</p>
     */
    boolean canAccept(ECOCraftingPatternBusBlockEntity bus, ItemStack pattern);

    /**
     * Whether this store could take at least one more pattern of any kind.
     *
     * <p>The catalog keeps a bus in its writable set while the bus has a free slot <em>or</em> an
     * auxiliary store with room (this method). Without it, a bus whose slots are all occupied by disks
     * would be dropped from the writable set even though its disks can still take patterns.</p>
     */
    boolean hasRoom(ECOCraftingPatternBusBlockEntity bus);

    /**
     * Stores an already decoded pattern.
     *
     * @return {@link ECOPatternInsertionResult#INSERTED} only if the store took the pattern; any other
     *         value falls back to the bus's slot inventory
     */
    ECOPatternInsertionResult insert(ECOCraftingPatternBusBlockEntity bus, ECOPreparedPattern prepared);

    /** Every encoded pattern this store currently holds for {@code bus}, in a stable order. */
    List<ItemStack> encodedPatterns(ECOCraftingPatternBusBlockEntity bus);

    /**
     * What a container exposes: the recipes to publish and the encoded stacks they were decoded from.
     *
     * @param details the decoded recipes, in the same order as {@code encoded}
     * @param encoded the stacks {@code details} came from, one to one
     */
    record ExposedPatterns(List<appeng.api.crafting.IPatternDetails> details, List<ItemStack> encoded) {
        public static final ExposedPatterns EMPTY = new ExposedPatterns(List.of(), List.of());
    }

    /**
     * Decodes what {@code store} contributes to the network.
     *
     * <p>Decoding produces the {@code details} instance, while the advertisement, the network index and any
     * caller asking what a container publishes all want the encoded stack for the <em>same</em> entry.
     * Returning both from one pass keeps a single decode path, so those views cannot drift apart.</p>
     *
     * <p>Static on purpose: an integration may supply its store through a dynamic proxy, and a new interface
     * method would land in that proxy's fallback branch - answering {@code null} - instead of here.</p>
     */
    static ExposedPatterns exposedPatterns(AuxiliaryPatternStore store, ECOCraftingPatternBusBlockEntity bus,
                                           net.minecraft.world.level.Level level) {
        if (store == null || level == null) {
            return ExposedPatterns.EMPTY;
        }
        List<appeng.api.crafting.IPatternDetails> details = new java.util.ArrayList<>();
        List<ItemStack> encoded = new java.util.ArrayList<>();
        for (ItemStack candidate : store.encodedPatterns(bus)) {
            appeng.api.crafting.IPatternDetails decoded =
                    appeng.api.crafting.PatternDetailsHelper.decodePattern(candidate, level);
            // Only what a molecular assembler can run is advertised, so the two lists describe the same set.
            if (decoded instanceof appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern) {
                details.add(decoded);
                encoded.add(candidate);
            }
        }
        return new ExposedPatterns(List.copyOf(details), List.copyOf(encoded));
    }

    /**
     * Takes one pattern back out of a container and settles what that costs.
     *
     * <p>Written for a player-facing take: a container's recipes are listed beside the bus's own, and taking one
     * out is two things at once - the recipe leaves the container, and what it was worth has to be paid back,
     * since a pattern that reached the network's index came out of a blank. The store owns both halves because
     * only it knows what its containers cost.</p>
     *
     * <p>Nothing in this mod calls it yet. The pattern access terminal is where a container's recipes can be
     * taken back out, through the view the owning integration supplies there; this is the contract for a
     * management screen in this mod to reach the same thing, when one is meant to offer it.</p>
     *
     * <p>All or nothing: when the cost cannot be settled nothing is removed and this answers {@code false}.
     * Callers report that as "could not be taken" rather than working around it.</p>
     *
     * <p>{@code containerSlot} names which of the bus's slots holds the container, since the same pattern
     * can sit on more than one of them. {@code encodedPattern} is compared as the listing was built - same
     * item and components as one of {@link #encodedPatterns(ECOCraftingPatternBusBlockEntity)}.</p>
     *
     * <p>The default declines, which is also what an integration reached through a dynamic proxy answers
     * until it handles the call. A listing that cannot be acted on is the safe way to be wrong; silently
     * emptying a container without settling it is not.</p>
     */
    default boolean remove(ECOCraftingPatternBusBlockEntity bus, int containerSlot, ItemStack encodedPattern) {
        return false;
    }

    /** Change token for {@link #encodedPatterns}; see the revision contract on the type. */
    default long revision(ECOCraftingPatternBusBlockEntity bus) {
        return 0L;
    }
}
