package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A finite long-range variant of MEGA Cells' bulk cell.
 *
 * <p>The normal insert/extract path uses long values. BigInteger is only used when the MEGA API
 * expands a stored amount into its compressed display variants.</p>
 */
public final class ECOMegaLongBulkStorageCell extends ECOStorageCell {
    private static final String DATA_TAG = "neoecoae_mega_long_bulk";
    private static final String ENTRIES_TAG = "entries";
    private static final String ITEM_TAG = "item";
    private static final String COMPONENTS_TAG = "components";
    private static final String UNITS_TAG = "units";
    private static final long MAX_UNITS = Long.MAX_VALUE;

    private final ItemStack stack;
    @Nullable
    private final ISaveProvider container;
    private final Map<AEItemKey, Long> storedUnits = new LinkedHashMap<>();
    private boolean persisted = true;
    private long availableStacksSignature = Long.MIN_VALUE;
    private KeyCounter availableStacksCache;
    @Nullable
    private LookupContext cachedLookupContext;

    /** Operation-local decoded state; avoids rebuilding component-backed inventories in loops. */
    private final class LookupContext {
        private final long fingerprint;
        private LookupContext(long fingerprint) {
            this.fingerprint = fingerprint;
        }
        private final boolean compressionCard = hasCompressionCard();
        private final Map<AEItemKey, CompressionChain> chains = new HashMap<>();
        private final long typeLimit = getTotalItemTypes();
        private final List<AEItemKey> filters = readFilters(this);

        private CompressionChain chain(AEItemKey key) {
            // Keep this cache operation-local so datapack reloads cannot leave stale chains.
            return chains.computeIfAbsent(key, CompressionService::getChain);
        }
    }

    private LookupContext lookupContext() {
        long fingerprint = lookupFingerprint();
        LookupContext cached = cachedLookupContext;
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached;
        }
        LookupContext created = new LookupContext(fingerprint);
        cachedLookupContext = created;
        return created;
    }

    private long lookupFingerprint() {
        long value = 1L;
        var config = getConfigInventory();
        value = 31L * value + config.size();
        for (int i = 0; i < config.size(); i++) {
            GenericStack stack = config.getStack(i);
            value = 31L * value + (stack == null || stack.what() == null ? 0L : stack.what().hashCode());
        }
        value = 31L * value + (hasCompressionCard() ? 1L : 0L);
        value = 31L * value + getTotalItemTypes();
        return value;
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
        if (storedUnits.isEmpty()) {
            return CellState.EMPTY;
        }
        return getRemainingItemCount() > 0 ? CellState.NOT_EMPTY : CellState.FULL;
    }

    @Override
    public long getStoredItemCount() {
        LookupContext context = lookupContext();
        long total = 0L;
        for (Map.Entry<AEItemKey, Long> entry : storedUnits.entrySet()) {
            AEItemKey storageForm = storageFormFor(entry.getKey(), context);
            long factor = unitFactor(entry.getKey(), storageForm, context);
            total = NEMath.saturatingAdd(total, entry.getValue() / factor);
        }
        return total;
    }

    @Override
    public long getRemainingItemCount() {
        LookupContext context = lookupContext();
        for (Map.Entry<AEItemKey, Long> entry : storedUnits.entrySet()) {
            if (entry.getValue() < MAX_UNITS && hasConfiguredChain(entry.getKey(), context)) {
                return MAX_UNITS;
            }
        }

        for (AEItemKey filter : context.filters) {
            boolean occupied = false;
            for (AEItemKey stored : storedUnits.keySet()) {
                if (sameCompressionChain(filter, stored, context)) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied && storedUnits.size() < context.typeLimit) {
                return MAX_UNITS;
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
        return storedUnits.size();
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
        long current = storedUnits.getOrDefault(slot, 0L);
        long remaining = MAX_UNITS - current;
        long accepted = Math.min(amount, divideSaturated(remaining, factor));
        if (accepted <= 0L) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            storedUnits.put(slot, current + NEMath.saturatingMultiply(accepted, factor));
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
        long available = storedUnits.getOrDefault(slot, 0L);
        long requestedUnits = NEMath.saturatingMultiply(amount, factor);
        long extracted = Math.min(requestedUnits, available) / factor;
        if (extracted <= 0L) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            long remaining = available - NEMath.saturatingMultiply(extracted, factor);
            if (remaining == 0L) {
                storedUnits.remove(slot);
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
        long signature = 1L;
        for (var entry : storedUnits.entrySet()) {
            signature = 31 * signature + entry.getKey().hashCode() ^ entry.getValue();
            signature = 31 * signature + context.chain(entry.getKey()).hashCode();
        }
        signature = 31 * signature + context.filters.hashCode();
        signature = 31 * signature + (context.compressionCard ? 1 : 0);
        if (availableStacksCache != null && signature == availableStacksSignature) {
            out.addAll(availableStacksCache);
            return;
        }
        KeyCounter computed = new KeyCounter();
        for (Map.Entry<AEItemKey, Long> entry : storedUnits.entrySet()) {
            long units = entry.getValue();
            if (units <= 0L) {
                continue;
            }

            AEItemKey storedKey = entry.getKey();
            CompressionChain chain = context.chain(storedKey);
            if (!chain.isEmpty() && context.compressionCard) {
                // MEGA's public expansion API uses BigInteger; this is an output boundary, not the storage hot path.
                AEItemKey storageForm = storageFormFor(storedKey, context);
                chain.initStacks(BigInteger.valueOf(units), cutoffFor(chain, storageForm), storageForm)
                    .forEach(computed::add);
            } else if (!chain.isEmpty()) {
                computed.add(storedKey, units / unitFactor(storedKey, storedKey, context));
            } else {
                computed.add(storedKey, units);
            }
        }
        availableStacksSignature = signature;
        availableStacksCache = computed;
        out.addAll(computed);
    }

    /** Exposes conversion paths on both sides of each configured storage-unit marker. */
    public List<IPatternDetails> getDecompressionPatterns() {
        LookupContext context = lookupContext();
        if (!context.compressionCard) {
            return List.of();
        }
        List<IPatternDetails> result = new ArrayList<>();
        for (AEItemKey filter : context.filters) {
            CompressionChain chain = context.chain(filter);
            if (!chain.isEmpty()) {
                result.addAll(chain.getDecompressionPatterns(cutoffFor(chain, filter)));
            }
        }
        return List.copyOf(result);
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
        return storedUnits.isEmpty();
    }

    @Override
    public void clearAllStoredStacks() {
        storedUnits.clear();
        saveChanges();
    }

    @Override
    public void persist() {
        if (persisted) {
            return;
        }

        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (storedUnits.isEmpty()) {
            custom.remove(DATA_TAG);
        } else {
            ListTag entries = new ListTag();
            for (Map.Entry<AEItemKey, Long> entry : storedUnits.entrySet()) {
                CompoundTag value = new CompoundTag();
                value.putString(ITEM_TAG, entry.getKey().getId().toString());
                value.put(COMPONENTS_TAG, writeComponents(entry.getKey()));
                value.putLong(UNITS_TAG, entry.getValue());
                entries.add(value);
            }
            CompoundTag data = new CompoundTag();
            data.put(ENTRIES_TAG, entries);
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
        var config = getConfigInventory();
        int activeSlots = Math.min(config.size(), (int) context.typeLimit);
        for (int i = 0; i < activeSlots; i++) {
            if (config.getKey(i) instanceof AEItemKey item && !hasFilterForChain(result, item, context)) {
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
        ListTag entries = data.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            AEItemKey key = readKey(entry);
            long units = entry.getLong(UNITS_TAG);
            if (key != null && units > 0L) {
                // Keep the persisted representative stable across datapack/config changes. Re-mapping
                // here can silently change quantities when compression ratios are edited.
                Long previous = storedUnits.putIfAbsent(key, units);
                if (previous != null) {
                    try {
                        storedUnits.put(key, Math.addExact(previous, units));
                    } catch (ArithmeticException overflow) {
                        // Fail closed: leave the original stack data untouched instead of loading a
                        // saturated value that would later be persisted as silent item loss.
                        throw new IllegalStateException("ECO MEGA bulk cell contains more than Long.MAX_VALUE units of "
                            + key.getId(), overflow);
                    }
                }
            }
        }
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
            .result().orElse(DataComponentPatch.EMPTY);
        return AEItemKey.of(new ItemStack(item, 1, patch));
    }

    private static CompoundTag writeComponents(AEItemKey key) {
        return (CompoundTag) DataComponentPatch.CODEC.encodeStart(
            NbtOps.INSTANCE, key.getReadOnlyStack().getComponentsPatch()).result().orElseGet(CompoundTag::new);
    }

    @Nullable
    private AEItemKey findSlot(AEItemKey item, boolean allowEmpty, LookupContext context) {
        for (AEItemKey stored : storedUnits.keySet()) {
            // Existing contents remain extractable after reconfiguration, but a removed filter
            // must not keep accepting new items into that old entry.
            if (matches(stored, item, context) && (!allowEmpty || hasConfiguredChain(stored, context))) {
                return stored;
            }
        }
        for (AEItemKey filter : context.filters) {
            // Removed markers retain their old stock and still consume physical type slots.
            if (matches(filter, item, context) && (storedUnits.containsKey(filter)
                || allowEmpty && storedUnits.size() < context.typeLimit)) {
                return filter;
            }
        }
        return null;
    }

    private boolean hasConfiguredChain(AEItemKey candidate, LookupContext context) {
        for (AEItemKey filter : context.filters) {
            if (sameCompressionChain(filter, candidate, context)) {
                return true;
            }
        }
        return false;
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
     * The configured marker is the storage unit for this chain. Variants above it must be folded
     * down into that unit instead of being published as separate, more-compressed terminal stacks.
     * Variants below it are only exposed for the indivisible remainder (for example fewer than
     * nine iron nuggets when the marker is an iron ingot).
     */
    private static int cutoffFor(CompressionChain chain, AEItemKey storageForm) {
        for (int index = 0; index < chain.size(); index++) {
            AEItemKey variant = AEItemKey.of(chain.getItem(index));
            if (storageForm.equals(variant)) {
                return index;
            }
        }
        return Math.max(0, chain.size() - 1);
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

    /** Returns the persisted storage entries for the item tooltip. */
    public Map<AEItemKey, Long> getStoredEntries() {
        return Map.copyOf(storedUnits);
    }

    public boolean isCompressionEnabled() {
        return hasCompressionCard();
    }

    private long unitFactor(AEItemKey configured, AEItemKey item, LookupContext context) {
        return CompressionChain.clamp(context.chain(configured).unitFactor(item), MAX_UNITS);
    }


    private static long divideSaturated(long value, long divisor) {
        if (divisor <= 1L) {
            return value;
        }
        return value / divisor;
    }

    @Override
    protected void saveChanges() {
        markContentChanged();
        persisted = false;
        cachedLookupContext = null;
        availableStacksCache = null;
        availableStacksSignature = Long.MIN_VALUE;
        if (deferMutationBatch()) {
            return;
        }
        persist();
        if (container != null) {
            container.saveChanges();
        }
    }
}
