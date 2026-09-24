package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.storage.IECOBulkDisplayCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.bulk.BulkUnits;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.integration.megacells.NEMegaItems;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import gripe._90.megacells.definition.MEGAItems;
import gripe._90.megacells.misc.CompressionChain;
import gripe._90.megacells.misc.CompressionService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A finite long-range variant of MEGA Cells' bulk cell.
 *
 * <p>All quantities use a persisted, immutable table of basic units. MEGA supplies recipes
 * and conversion patterns, but does not perform inventory arithmetic.</p>
 */
public final class ECOMegaLongBulkStorageCell extends ECOStorageCell implements IECOBulkDisplayCell {
    private static final String DATA_TAG = "neoecoae_mega_long_bulk";
    private static final String ENTRIES_TAG = "entries";
    private static final String ITEM_TAG = "item";
    private static final String COMPONENTS_TAG = "components";
    private static final String UNITS_TAG = "units";
    private static final long MAX_UNITS = Long.MAX_VALUE;

    private final ItemStack stack;
    @Nullable
    private final ISaveProvider container;
    private final Object2LongLinkedOpenHashMap<AEItemKey> storedUnits = new Object2LongLinkedOpenHashMap<>();
    private final Map<AEItemKey, BulkUnits> definitions = new LinkedHashMap<>();
    private final Map<AEItemKey, AEItemKey> cutoffs = new LinkedHashMap<>();
    private final ListTag unresolvedEntries = new ListTag();
    private boolean persisted = true;
    private KeyCounter availableStacksCache;
    @Nullable
    private LookupContext cachedLookupContext;

    /** Configuration state, independent of content mutations. */
    private final class LookupContext {
        private final ConfigState state;
        private final boolean compressionCard;
        private final long typeLimit;
        private final List<AEItemKey> filters;

        private LookupContext(ConfigState state) {
            this.state = state;
            this.compressionCard = state.compressionCard();
            this.typeLimit = state.typeLimit();
            this.filters = readFilters(this);
        }
        private final Map<AEItemKey, CompressionChain> chains = new Object2ObjectOpenHashMap<>();
        private final Map<AEItemKey, BulkUnits> compiled = new Object2ObjectOpenHashMap<>();

        private CompressionChain chain(AEItemKey key) {
            CompressionChain current = CompressionService.getChain(key);
            CompressionChain previous = chains.put(key, current);
            if (previous != current) {
                if (previous != null) markContentChanged();
                compiled.remove(key);
                availableStacksCache = null;
            }
            return current;
        }

        private BulkUnits units(AEItemKey key) {
            CompressionChain current = chain(key);
            return compiled.computeIfAbsent(key, ignored -> MegaBulkUnits.compile(current, key));
        }
    }

    private record ConfigState(List<GenericStack> markers, boolean compressionCard, long typeLimit) {}

    List<GenericStack> configuredStacks() {
        return stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of());
    }

    private LookupContext lookupContext() {
        ConfigState state = new ConfigState(configuredStacks(), hasCompressionCard(), getTotalItemTypes());
        LookupContext cached = cachedLookupContext;
        if (cached != null && cached.state.equals(state)) return cached;
        availableStacksCache = null;
        LookupContext created = new LookupContext(state);
        cachedLookupContext = created;
        return created;
    }

    public ECOMegaLongBulkStorageCell(ItemStack stack, @Nullable ISaveProvider container) {
        super(stack, container);
        this.stack = stack;
        this.container = container;
        loadStoredUnits();
    }

    @Override
    public boolean isInfiniteStorageEligible() {
        return false;
    }

    @Override
    public CellState getStatus() {
        if (storedUnits.isEmpty() && unresolvedEntries.isEmpty()) {
            return CellState.EMPTY;
        }
        return getRemainingItemCount() > 0 ? CellState.NOT_EMPTY : CellState.FULL;
    }

    @Override
    public long getStoredItemCount() {
        // Aggregate UI statistics use atoms too; only this aggregate may saturate.
        long total = 0L;
        for (LongIterator it = storedUnits.values().iterator(); it.hasNext(); ) {
            total = NEMath.saturatingAdd(total, it.nextLong());
        }
        return total;
    }

    @Override
    public long getRemainingItemCount() {
        LookupContext context = lookupContext();
        for (AEItemKey filter : context.filters) {
            AEItemKey slot = findSlot(filter, true, context);
            if (slot != null && BulkUnits.insertable(storedUnits.getLong(slot),
                    1L, unitFactor(slot, filter, context)) > 0) return MAX_UNITS;
            if (context.compressionCard) {
                for (AEItemKey variant : context.units(filter).items()) {
                    slot = findSlot(variant, true, context);
                    if (slot != null && BulkUnits.insertable(storedUnits.getLong(slot),
                            1L, unitFactor(slot, variant, context)) > 0) return MAX_UNITS;
                }
            }
        }
        return 0L;
    }

    @Override
    public long getFreeBytes() {
        return Math.max(0L, getTotalBytes() - getUsedBytes());
    }

    @Override
    public int getUnusedItemCount() {
        return 0;
    }

    @Override
    public long getUsedBytes() {
        long typeBytes = NEMath.saturatingMultiply(getStoredItemTypes(), getBytesPerType());
        long amountBytes = getStoredItemCount() / Math.max(1, getKeyType().getAmountPerByte());
        return NEMath.saturatingAdd(typeBytes, amountBytes);
    }

    @Override
    public long getStoredItemTypes() {
        return storedUnits.size() + unresolvedEntries.size();
    }

    @Override
    public long getTotalItemTypes() {
        return hasEcoMegaUpgradeCard()
            ? MegaCellCapacities.LONG_BULK_UPGRADED_TYPE_LIMIT
            : MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
    }

    @Override
    public long getRemainingItemTypes() {
        return Math.max(0L, getTotalItemTypes() - getStoredItemTypes());
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return insertInternal(what, amount, mode);
    }

    @Override
    public long insertForMigration(AEKey what, long amount, Actionable mode) {
        return insertInternal(what, amount, mode);
    }

    private long insertInternal(AEKey what, long amount, Actionable mode) {
        if (amount <= 0L || !(what instanceof AEItemKey item)) {
            return 0L;
        }

        LookupContext context = lookupContext();
        AEItemKey slot = findSlot(item, true, context);
        if (slot == null) {
            return 0L;
        }

        long factor = unitFactor(slot, item, context);
        long current = storedUnits.getLong(slot);
        long accepted = BulkUnits.insertable(current, amount, factor);
        if (accepted <= 0L) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            definitions.putIfAbsent(slot, context.units(slot));
            storedUnits.put(slot, current + accepted * factor);
            saveChanges();
        }
        return accepted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0L || !(what instanceof AEItemKey item)) {
            return 0L;
        }

        LookupContext context = lookupContext();
        AEItemKey slot = findSlot(item, false, context);
        if (slot == null) {
            return 0L;
        }

        long factor = unitFactor(slot, item, context);
        long available = storedUnits.getLong(slot);
        long extractableUnits = available;
        BulkUnits units = definition(slot, context);
        if (!context.compressionCard && units.equals(context.units(slot))) {
            int index = units.items().indexOf(item);
            int cutoff = units.cutoff(slot);
            if (index >= 0 && index < cutoff) extractableUnits %= units.factors().get(index + 1);
        }
        long extracted = BulkUnits.extractable(extractableUnits, amount, factor);
        if (extracted <= 0L) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            long remaining = available - extracted * factor;
            if (remaining == 0L) {
                storedUnits.removeLong(slot);
                definitions.remove(slot);
            } else {
                storedUnits.put(slot, remaining);
            }
            saveChanges();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        LookupContext context = lookupContext();
        // Check current recipe identities before reusing the view (including after /reload).
        for (AEItemKey key : storedUnits.keySet()) context.chain(key);
        if (availableStacksCache != null) {
            out.addAll(availableStacksCache);
            return;
        }
        KeyCounter computed = new KeyCounter();
        for (var entry : storedUnits.object2LongEntrySet()) {
            BulkUnits units = definition(entry.getKey(), context);
            int cutoff = effectiveCutoff(entry.getKey(), units, context);
            units.publish(entry.getLongValue(), cutoff, computed);
        }
        availableStacksCache = computed;
        out.addAll(computed);
    }

    /** Patterns and the inventory view use the same independent cutoff. */
    public List<IPatternDetails> getDecompressionPatterns() {
        LookupContext context = lookupContext();
        if (!context.compressionCard) return List.of();
        var keys = new java.util.LinkedHashSet<>(context.filters);
        keys.addAll(storedUnits.keySet()); // Unmarked contents still need CPU conversion paths.
        var result = new java.util.LinkedHashSet<IPatternDetails>();
        for (AEItemKey key : keys) {
            BulkUnits units = definition(key, context);
            CompressionChain chain = context.chain(key);
            if (!chain.isEmpty() && units.equals(context.units(key))) {
                result.addAll(chain.getDecompressionPatterns(effectiveCutoff(key, units, context)));
            }
        }
        return List.copyOf(result);
    }

    private BulkUnits definition(AEItemKey key, LookupContext context) {
        BulkUnits saved = definitions.get(key);
        return saved != null ? saved : context.units(key);
    }

    private int effectiveCutoff(AEItemKey key, BulkUnits units, LookupContext context) {
        // On recipe changes recover only the original base item, never reinterpret old atoms.
        if (!units.equals(context.units(key))) return 0;
        if (!context.compressionCard) return units.cutoff(key);
        AEItemKey selected = cutoffs.get(units.items().getFirst());
        return selected == null ? units.items().size() - 1 : units.cutoff(selected);
    }

    /** Selects a display denomination without changing the filter or the stored quantity. */
    public void setCompressionCutoff(AEItemKey chainItem, AEItemKey displayItem) {
        LookupContext context = lookupContext();
        BulkUnits units = context.units(chainItem);
        if (units.factor(displayItem) == 0) throw new IllegalArgumentException("Not a supported denomination");
        cutoffs.put(units.items().getFirst(), displayItem);
        saveChanges();
    }

    @Override
    public AEItemKey cycleCompressionCutoff(AEItemKey chainItem, int delta) {
        BulkUnits units = lookupContext().units(chainItem);
        int current = units.cutoff(getCompressionCutoff(chainItem));
        int next = Math.floorMod(current + delta, units.items().size());
        AEItemKey selected = units.items().get(next);
        setCompressionCutoff(chainItem, selected);
        return selected;
    }

    public AEItemKey getCompressionCutoff(AEItemKey chainItem) {
        LookupContext context = lookupContext();
        BulkUnits units = definition(chainItem, context);
        return units.items().get(effectiveCutoff(chainItem, units, context));
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        // A removed filter may still own old contents, but it must not attract new inserts. The
        // allow-empty lookup applies the same configured-chain gate as insertInternal.
        return what instanceof AEItemKey item && findSlot(item, true, lookupContext()) != null;
    }

    @Override
    public boolean prioritizesMarkedInserts() {
        return true;
    }

    @Override
    public boolean canFitInsideCell() {
        return storedUnits.isEmpty() && unresolvedEntries.isEmpty();
    }

    @Override
    public void clearAllStoredStacks() {
        storedUnits.clear();
        definitions.clear();
        saveChanges();
    }

    @Override
    public void persist() {
        if (persisted) {
            return;
        }

        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (storedUnits.isEmpty() && cutoffs.isEmpty() && unresolvedEntries.isEmpty()) {
            custom.remove(DATA_TAG);
        } else {
            ListTag entries = new ListTag();
            for (var entry : storedUnits.object2LongEntrySet()) {
                CompoundTag value = new CompoundTag();
                value.putString(ITEM_TAG, entry.getKey().getId().toString());
                value.put(COMPONENTS_TAG, writeComponents(entry.getKey()));
                value.putLong(UNITS_TAG, entry.getLongValue());
                BulkUnits definition = definition(entry.getKey(), lookupContext());
                definitions.putIfAbsent(entry.getKey(), definition);
                ListTag table = new ListTag();
                for (int i = 0; i < definition.items().size(); i++) {
                    CompoundTag denomination = writeKey(definition.items().get(i));
                    denomination.putLong("factor", definition.factors().get(i));
                    table.add(denomination);
                }
                value.put("definition", table);
                entries.add(value);
            }
            CompoundTag data = new CompoundTag();
            data.put("unresolved", unresolvedEntries.copy());
            data.putInt("version", 1);
            data.put(ENTRIES_TAG, entries);
            ListTag display = new ListTag();
            for (var cutoff : cutoffs.entrySet()) {
                CompoundTag setting = writeKey(cutoff.getKey());
                setting.put("display", writeKey(cutoff.getValue()));
                display.add(setting);
            }
            data.put("cutoffs", display);
            custom.put(DATA_TAG, data);
        }

        if (custom.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        }
        persisted = true;
    }

    private List<AEItemKey> readFilters() {
        return lookupContext().filters;
    }

    private List<AEItemKey> readFilters(LookupContext context) {
        List<AEItemKey> result = new ArrayList<>();
        List<GenericStack> config = context.state.markers();
        int activeSlots = Math.min(config.size(), (int) context.typeLimit);
        for (int i = 0; i < activeSlots; i++) {
            GenericStack marker = config.get(i);
            if (marker != null && marker.what() instanceof AEItemKey item && !hasFilterForChain(result, item, context)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Keeps the first configured variant as the representative of its compression chain. A later
     * variant from the same chain must not consume another type slot or create a second storage entry.
     */
    private boolean hasFilterForChain(List<AEItemKey> configured, AEItemKey candidate, LookupContext context) {
        for (AEItemKey existing : configured) {
            if (sameCompressionChain(existing, candidate, context)) {
                return true;
            }
        }
        return false;
    }

    private void loadStoredUnits() {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag data = custom.getCompound(DATA_TAG);
        if (data.getInt("version") > 1) throw new IllegalStateException("Unsupported ECO bulk format");
        ListTag entries = new ListTag();
        for (var raw : data.getList(ENTRIES_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag envelope = new CompoundTag();
            envelope.putInt("version", data.getInt("version"));
            envelope.put("entry", raw.copy());
            entries.add(envelope);
        }
        entries.addAll(data.getList("unresolved", Tag.TAG_COMPOUND).copy());
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag envelope = entries.getCompound(i);
            CompoundTag entry = envelope.getCompound("entry");
            AEItemKey key = readKey(entry);
            long count = entry.getLong(UNITS_TAG);
            if (key == null || count <= 0 || storedUnits.containsKey(key)) {
                unresolvedEntries.add(envelope.copy());
                continue;
            }
            try {
                BulkUnits units;
                if (envelope.getInt("version") == 0) {
                    // Legacy data has no historical ratios. Capture the currently available table
                    // once and preserve the old effective display setting during this migration.
                    CompressionChain legacyChain = CompressionService.getChain(key);
                    if (legacyChain.isEmpty()) throw new IllegalArgumentException("Unknown legacy basis");
                    units = MegaBulkUnits.compile(legacyChain, key);
                    if (units.factor(key) == 0) throw new IllegalArgumentException("Unsupported legacy unit");
                    cutoffs.put(units.items().getFirst(), storageFormFor(key, lookupContext()));
                } else {
                    if (envelope.getInt("version") != 1) throw new IllegalArgumentException("Unknown entry version");
                    var items = new ArrayList<AEItemKey>();
                    var factors = new ArrayList<Long>();
                    ListTag table = entry.getList("definition", Tag.TAG_COMPOUND);
                    for (int j = 0; j < table.size(); j++) {
                        CompoundTag denomination = table.getCompound(j);
                        AEItemKey item = readKey(denomination);
                        if (item == null) throw new IllegalArgumentException("Missing bulk item");
                        items.add(item);
                        factors.add(denomination.getLong("factor"));
                    }
                    units = new BulkUnits(items, factors);
                    if (units.factor(key) == 0) throw new IllegalArgumentException("Missing representative");
                }
                if (definitions.values().stream().anyMatch(units::equals)) {
                    throw new IllegalArgumentException("Duplicate bulk account");
                }
                definitions.put(key, units);
                storedUnits.put(key, count);
            } catch (IllegalArgumentException invalid) {
                unresolvedEntries.add(envelope.copy());
            }
        }
        ListTag display = data.getList("cutoffs", Tag.TAG_COMPOUND);
        for (int i = 0; i < display.size(); i++) {
            CompoundTag setting = display.getCompound(i);
            AEItemKey base = readKey(setting);
            AEItemKey item = readKey(setting.getCompound("display"));
            if (base != null && item != null) cutoffs.put(base, item);
        }
        if (!entries.isEmpty()) persisted = false;
    }

    private static CompoundTag writeKey(AEItemKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ITEM_TAG, key.getId().toString());
        tag.put(COMPONENTS_TAG, writeComponents(key));
        return tag;
    }

    @Nullable
    private static AEItemKey readKey(CompoundTag entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.getString(ITEM_TAG));
        if (id == null) {
            return null;
        }
        var item = BuiltInRegistries.ITEM.getHolder(id).orElse(null);
        if (item == null) {
            return null;
        }
        var patch = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, entry.getCompound(COMPONENTS_TAG))
            .result().orElse(null);
        return patch == null ? null : AEItemKey.of(new ItemStack(item, 1, patch));
    }

    private static CompoundTag writeComponents(AEItemKey key) {
        return (CompoundTag) DataComponentPatch.CODEC.encodeStart(
            NbtOps.INSTANCE, key.getReadOnlyStack().getComponentsPatch()).result().orElseThrow(() -> new IllegalStateException("Cannot encode bulk item components"));
    }

    @Nullable
    private AEItemKey findSlot(AEItemKey item, boolean allowEmpty, LookupContext context) {
        for (AEItemKey stored : storedUnits.keySet()) {
            // Existing contents remain extractable after reconfiguration, but a removed filter
            // must not keep accepting new items into that old entry.
            BulkUnits units = definition(stored, context);
            boolean current = units.equals(context.units(stored));
            if (allowEmpty) {
                if (current && units.factor(item) > 0 && accepts(item, context)
                        && (context.compressionCard || stored.equals(item))) return stored;
                if (!context.compressionCard && units.factor(item) > 0) return null;
                // Never open a second account for a changed definition of occupied stock.
                if (!current && (units.factor(item) > 0 || sameCompressionChain(stored, item, context))) return null;
            } else if (units.factor(item) > 0) {
                if (!current ? item.equals(units.items().getFirst())
                    : context.compressionCard || units.cutoff(item) <= units.cutoff(stored)) return stored;
            }
        }
        if (!allowEmpty) return null;
        for (AEItemKey filter : context.filters) {
            // Removed markers retain their old stock and still consume physical type slots.
            if (matches(filter, item, context) && context.units(filter).factor(item) > 0
                && (storedUnits.containsKey(filter)
                || allowEmpty && getStoredItemTypes() < context.typeLimit)) {
                BulkUnits units = context.units(filter);
                return units.factor(filter) > 0 ? filter : units.items().getFirst();
            }
        }
        return null;
    }

    private boolean accepts(AEItemKey item, LookupContext context) {
        return context.filters.stream().anyMatch(filter -> matches(filter, item, context));
    }

    private boolean matches(AEItemKey configured, AEItemKey item, LookupContext context) {
        return configured.equals(item) || context.compressionCard && context.chain(configured).containsVariant(item);
    }

    /**
     * Returns the first configured variant for the stored chain. If the configuration was cleared
     * or changed to another chain while the cell was non-empty, the persisted key remains the
     * fallback so that the old contents can still be recovered.
     */
    private AEItemKey storageFormFor(AEItemKey storedKey, LookupContext context) {
        if (context.compressionCard) {
            for (AEItemKey filter : context.filters) {
                if (sameCompressionChain(filter, storedKey, context)) {
                    return filter;
                }
            }
        }
        return storedKey;
    }

    private boolean sameCompressionChain(AEItemKey first, AEItemKey second, LookupContext context) {
        if (first.equals(second)) {
            return true;
        }

        CompressionChain firstChain = context.chain(first);
        return !firstChain.isEmpty() && firstChain.equals(context.chain(second));
    }


    /**
     * The Drive caches this inventory instance, while the cell configuration can change in place
     * through the cell UI. Read the current configuration whenever the cell is queried so pattern
     * refreshes and storage matching observe the same filters as the ItemStack.
     */
    private List<AEItemKey> configuredFilters() {
        return readFilters();
    }

    public boolean hasEcoMegaUpgradeCard() {
        return getUpgradesInventory().isInstalled(NEMegaItems.ECO_MEGA_UPGRADE_CARD);
    }

    private boolean hasCompressionCard() {
        // Cell UIs can change upgrades while the drive retains this inventory instance.
        return getUpgradesInventory().isInstalled(MEGAItems.COMPRESSION_CARD);
    }

    public List<AEItemKey> getEffectiveConfiguredFilters() {
        return List.copyOf(configuredFilters());
    }

    /** One representative per occupied chain, without expanding compression variants. */
    public List<AEItemKey> getStoredChainFilters() {
        LookupContext context = lookupContext();
        return storedUnits.keySet().stream().map(key -> storageFormFor(key, context)).toList();
    }

    public int getUnresolvedEntryCount() {
        return unresolvedEntries.size();
    }

    /** Returns the persisted storage entries for the item tooltip. */
    public Map<AEItemKey, Long> getStoredEntries() {
        return Map.copyOf(storedUnits);
    }

    public boolean isCompressionEnabled() {
        return hasCompressionCard();
    }

    private long unitFactor(AEItemKey configured, AEItemKey item, LookupContext context) {
        return definition(configured, context).factor(item);
    }

    @Override
    protected void saveChanges() {
        markContentChanged();
        persisted = false;
        availableStacksCache = null;
        if (deferMutationBatch()) {
            return;
        }
        persist();
        if (container != null) {
            container.saveChanges();
        }
    }
}
