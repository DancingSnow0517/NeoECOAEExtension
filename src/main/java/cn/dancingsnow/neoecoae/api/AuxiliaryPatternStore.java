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
     * <p>Consulted by the bus before every {@link #insert}, to decide whether a pattern goes to the
     * store or to a slot. Must be free of side effects.</p>
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

    /** Change token for {@link #encodedPatterns}; see the revision contract on the type. */
    default long revision(ECOCraftingPatternBusBlockEntity bus) {
        return 0L;
    }
}
