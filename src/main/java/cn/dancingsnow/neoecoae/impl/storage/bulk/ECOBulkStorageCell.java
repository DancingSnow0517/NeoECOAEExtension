package cn.dancingsnow.neoecoae.impl.storage.bulk;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.storage.IECOBulkDisplayCell;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOBulkStorageCellItem;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Unlimited exact balances with an immutable historical denomination table for every account. */
public final class ECOBulkStorageCell extends ECOStorageCell implements IECOBulkDisplayCell, ExactAmountSource, ECOBigIntegerStorage {
    private static final String DATA_TAG = "neoecoae_bulk";
    private static final String LEGACY_TAG = "neoecoae_mega_long_bulk";
    private final ItemStack stack;
    private final Map<AEItemKey, Account> accounts = new LinkedHashMap<>();
    private final Map<AEItemKey, AEItemKey> cutoffs = new LinkedHashMap<>();
    private final Map<AEItemKey, BigInteger> pending = new LinkedHashMap<>();
    private final ListTag unresolved = new ListTag();
    private int storagePriority = 1;
    private boolean persisted = true;
    private long checkedRevision = -1;
    private boolean recovering;
    private List<GenericStack> cachedMarkers;
    private int cachedSlots;
    private long filterRevision = -1;
    private List<AEItemKey> cachedFilters = List.of();
    private record Account(BulkUnits units, BigInteger amount) {}

    public ECOBulkStorageCell(ItemStack stack, @Nullable ISaveProvider container) {
        super(stack, container);
        this.stack = stack;
        load();
    }

    @Override public boolean isInfiniteStorageEligible() { return false; }
    @Override public boolean prioritizesMarkedInserts() { return storagePriority > 0; }
    @Override public int getStoragePriority() { return storagePriority; }
    public int getConfiguredStoragePriority() { return storagePriority; }
    public void setConfiguredStoragePriority(int priority) {
        if (storagePriority == priority) return;
        storagePriority = priority;
        saveChanges();
        persist();
    }
    @Override public CellState getStatus() {
        return canFitInsideCell() ? CellState.EMPTY : getRemainingItemCount() > 0 ? CellState.NOT_EMPTY : CellState.FULL;
    }
    @Override public long getTotalItemTypes() { return ((ECOBulkStorageCellItem) stack.getItem()).activeSlots(stack); }
    @Override public long getStoredItemTypes() { return accounts.size() + unresolved.size(); }
    @Override public long getRemainingItemTypes() { return Math.max(0, getTotalItemTypes() - getStoredItemTypes()); }
    @Override public long getStoredItemCount() {
        return BulkUnits.clamp(accounts.values().stream().map(Account::amount).reduce(BigInteger.ZERO, BigInteger::add));
    }
    @Override public long getRemainingItemCount() {
        return getEffectiveConfiguredFilters().stream().anyMatch(key -> insert(key, 1, Actionable.SIMULATE, null) > 0)
            ? Long.MAX_VALUE : 0;
    }
    @Override public long getUsedBytes() { return getStoredItemCount(); }
    public BigInteger getExactUsedBytes() {
        return accounts.values().stream().map(Account::amount).reduce(BigInteger.ZERO, BigInteger::add);
    }
    @Override public long getTotalBytes() { return Long.MAX_VALUE; }
    @Override public long getFreeBytes() { return Long.MAX_VALUE; }
    @Override public int getUnusedItemCount() { return 0; }
    @Override public boolean canFitInsideCell() { return accounts.isEmpty() && pending.isEmpty() && unresolved.isEmpty(); }

    public List<AEItemKey> getEffectiveConfiguredFilters() {
        List<GenericStack> markers = stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of());
        int slots = (int) getTotalItemTypes();
        long revision = ECOCompressionService.revision();
        if (filterRevision == revision && cachedSlots == slots && markers.equals(cachedMarkers)) return cachedFilters;
        List<AEItemKey> result = new ArrayList<>();
        for (int i = 0; i < Math.min(markers.size(), slots); i++) {
            GenericStack marker = markers.get(i);
            if (marker != null && marker.what() instanceof AEItemKey item
                    && result.stream().noneMatch(existing -> ECOCompressionService.sameChain(existing, item))) result.add(item);
        }
        cachedMarkers = new ArrayList<>(markers); cachedSlots = slots; filterRevision = revision;
        cachedFilters = List.copyOf(result);
        return cachedFilters;
    }
    public List<AEItemKey> getStoredChainFilters() { return List.copyOf(accounts.keySet()); }
    public int getUnresolvedEntryCount() { refreshUnresolved(); return unresolved.size(); }
    public long getRecoveringEntryCount() { return accounts.values().stream().filter(account -> !isCurrent(account.units())).count(); }

    /** Check exact balances, including lower remainders and outputs owned by this disk. */
    public boolean canRemoveMarker(AEItemKey marker) {
        refreshUnresolved();
        if (!unresolved.isEmpty()) return false;
        BulkUnits current = ECOCompressionService.units(marker);
        for (var entry : accounts.entrySet()) {
            Account account = entry.getValue();
            if (account.amount().signum() > 0 && (current.contains(entry.getKey())
                || account.units().items().stream().anyMatch(current::contains))) return false;
        }
        return pending.entrySet().stream().noneMatch(entry -> entry.getValue().signum() > 0
            && (current.contains(entry.getKey()) || accounts.values().stream()
                .anyMatch(account -> account.units().contains(marker) && account.units().contains(entry.getKey()))));
    }

    /** Keep occupied slots when any workbench, drag operation or clear-all action edits the config. */
    public void protectOccupiedMarkers(List<GenericStack> previous, appeng.util.ConfigInventory proposed) {
        proposed.beginBatch();
        try {
            for (int slot = 0; slot < Math.min(previous.size(), proposed.size()); slot++) {
                GenericStack before = previous.get(slot);
                if (before == null || !(before.what() instanceof AEItemKey marker)) continue;
                var after = proposed.getKey(slot);
                if (after instanceof AEItemKey item && ECOCompressionService.sameChain(marker, item)) continue;
                if (!canRemoveMarker(marker)) proposed.setStack(slot, before);
            }
        } finally {
            proposed.endBatchSuppressed();
        }
    }
    public Map<AEItemKey, BigInteger> getStoredEntries() {
        Map<AEItemKey, BigInteger> result = new LinkedHashMap<>();
        accounts.forEach((key, account) -> result.put(key, account.amount()));
        return Map.copyOf(result);
    }

    @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount <= 0 ? 0 : insertBigInteger(what, BigInteger.valueOf(amount), mode, source).longValueExact();
    }
    @Override public BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        refreshUnresolved();
        if (amount.signum() <= 0 || !(what instanceof AEItemKey item) || !canStoreKeyInsideStorageCell(what)) return BigInteger.ZERO;
        if (item.getReadOnlyStack().getItem() instanceof cn.dancingsnow.neoecoae.items.ECOStorageCellItem) {
            var nested = cn.dancingsnow.neoecoae.items.ECOStorageCellItem.getCellInventory(item.toStack());
            if (nested != null && !nested.canFitInsideCell()) return BigInteger.ZERO;
        }
        AEItemKey marker = getEffectiveConfiguredFilters().stream()
            .filter(key -> ECOCompressionService.sameChain(key, item)).findFirst().orElse(null);
        if (marker == null) return BigInteger.ZERO;
        BulkUnits current = ECOCompressionService.units(item);
        AEItemKey target = null;
        for (var entry : accounts.entrySet()) {
            BulkUnits saved = entry.getValue().units();
            if (saved.items().stream().anyMatch(current::contains)) {
                // Reloads may not reinterpret old units, nor open another account for the same stock.
                if (!saved.equals(current)) return BigInteger.ZERO;
                target = entry.getKey();
                break;
            }
        }
        if (target == null && getRemainingItemTypes() == 0) return BigInteger.ZERO;
        if (mode == Actionable.MODULATE) {
            if (target == null) target = marker;
            Account previous = accounts.get(target);
            BigInteger before = previous == null ? BigInteger.ZERO : previous.amount();
            accounts.put(target, new Account(current, before.add(current.factor(item).multiply(amount))));
            saveChanges();
        }
        return amount;
    }

    @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        refreshUnresolved();
        if (amount <= 0 || !(what instanceof AEItemKey item)) return 0;
        long remaining = amount;
        for (var iterator = accounts.entrySet().iterator(); iterator.hasNext() && remaining > 0;) {
            var entry = iterator.next(); Account account = entry.getValue();
            BigInteger factor = account.units().factor(item);
            if (factor.signum() <= 0 || !isCurrent(account.units()) && !item.equals(account.units().items().getFirst())) continue;
            long taken = account.amount().divide(factor).min(BigInteger.valueOf(remaining)).longValueExact();
            if (taken == 0) continue;
            remaining -= taken;
            if (mode == Actionable.MODULATE) {
                BigInteger after = account.amount().subtract(factor.multiply(BigInteger.valueOf(taken)));
                if (after.signum() == 0) iterator.remove();
                else entry.setValue(new Account(account.units(), after));
            }
        }
        if (remaining != amount && mode == Actionable.MODULATE) saveChanges();
        return amount - remaining;
    }

    private boolean isCurrent(BulkUnits units) { return units.equals(ECOCompressionService.units(units.items().getFirst())); }
    private int cutoff(AEItemKey key, Account account) {
        BulkUnits units = account.units();
        if (!isCurrent(units)) return 0;
        return configuredCutoff(key, units);
    }
    private int configuredCutoff(AEItemKey key, BulkUnits units) {
        AEItemKey selected = cutoffs.get(units.items().getFirst());
        if (selected == null) selected = getEffectiveConfiguredFilters().stream().filter(units::contains).findFirst().orElse(key);
        return units.cutoff(selected);
    }
    private void visit(BiConsumer<AEItemKey, BigInteger> visitor) {
        refreshUnresolved();
        accounts.forEach((key, account) -> account.units().visit(account.amount(), cutoff(key, account), visitor));
    }
    @Override public void getAvailableStacks(KeyCounter out) { visit((key, amount) -> BulkUnits.addSaturated(out, key, amount)); }
    @Override public void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
        Map<AEItemKey, BigInteger> totals = new LinkedHashMap<>();
        visit((key, amount) -> totals.merge(key, amount, BigInteger::add));
        totals.forEach((key, amount) -> visitor.accept(key, ExactAmount.finite(amount)));
    }
    @Override public boolean neoecoae$hasSaturatingListing() { return true; }
    @Override public boolean isPreferredStorageFor(AEKey key, IActionSource source) { return insert(key, 1, Actionable.SIMULATE, source) > 0; }

    public List<IPatternDetails> getConversionPatterns() {
        refreshUnresolved();
        Map<BulkUnits, Integer> definitions = new LinkedHashMap<>();
        for (AEItemKey marker : getEffectiveConfiguredFilters()) definitions.put(ECOCompressionService.units(marker),
            ECOCompressionService.units(marker).cutoff(cutoffs.getOrDefault(ECOCompressionService.units(marker).items().getFirst(), marker)));
        accounts.forEach((key, account) -> { if (isCurrent(account.units())) definitions.put(account.units(), cutoff(key, account)); });
        List<IPatternDetails> result = new ArrayList<>();
        definitions.forEach((units, cutoff) -> {
            for (int i = 0; i < units.items().size() - 1; i++) {
                long ratio = units.factors().get(i + 1).divide(units.factors().get(i)).longValueExact();
                if (i < cutoff) result.add(new ECOBulkConversionPattern(units.items().get(i + 1), 1, units.items().get(i), ratio));
                else result.add(new ECOBulkConversionPattern(units.items().get(i), ratio, units.items().get(i + 1), 1));
            }
        });
        return List.copyOf(result);
    }
    @Override public AEItemKey cycleCompressionCutoff(AEItemKey item, int delta) {
        BulkUnits units = ECOCompressionService.units(item);
        AEItemKey before = cutoffs.getOrDefault(units.items().getFirst(), item);
        AEItemKey selected = units.items().get(Math.floorMod(units.cutoff(before) + delta, units.items().size()));
        setCompressionCutoff(selected);
        return selected;
    }

    @Override public List<AEItemKey> getCompressionChain(AEItemKey item) {
        return ECOCompressionService.units(item).items();
    }

    @Override public AEItemKey getCompressionCutoff(AEItemKey item) {
        refreshUnresolved();
        BulkUnits units = ECOCompressionService.units(item);
        for (var entry : accounts.entrySet()) {
            Account account = entry.getValue();
            if (account.units().contains(item) || account.units().items().stream().anyMatch(units::contains)) {
                return account.units().items().get(cutoff(entry.getKey(), account));
            }
        }
        return units.items().get(configuredCutoff(item, units));
    }

    public void setCompressionCutoff(AEItemKey selected) {
        BulkUnits units = ECOCompressionService.units(selected);
        cutoffs.put(units.items().getFirst(), selected);
        saveChanges();
    }

    /** Ownership of already-consumed crafting inputs is transferred to this disk, never to a grid pivot. */
    public void enqueueOutput(AEItemKey key, BigInteger amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Non-positive conversion output");
        pending.merge(key, amount, BigInteger::add);
        saveChanges();
        persist();
    }
    public Map<AEItemKey, BigInteger> getPendingOutputs() { return Map.copyOf(pending); }
    public void drainOutputs(MEStorage network, IActionSource source) {
        for (var iterator = pending.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            long requested = BulkUnits.clamp(entry.getValue());
            long inserted = network.insert(entry.getKey(), requested, Actionable.MODULATE, source);
            if (inserted < 0 || inserted > requested) throw new IllegalStateException("Invalid conversion output acknowledgement");
            if (inserted == 0) continue;
            BigInteger remaining = entry.getValue().subtract(BigInteger.valueOf(inserted));
            if (remaining.signum() == 0) iterator.remove(); else entry.setValue(remaining);
            saveChanges();
        }
    }

    @Override protected void saveChanges() { persisted = false; super.saveChanges(); }
    @Override public void persist() {
        if (persisted) return;
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag data = new CompoundTag(); data.putInt("version", 2);
        ListTag entries = new ListTag();
        accounts.forEach((key, account) -> {
            CompoundTag entry = BulkItemKeys.write(key); entry.putString("units", account.amount().toString());
            ListTag table = new ListTag();
            for (int i = 0; i < account.units().items().size(); i++) {
                CompoundTag denomination = BulkItemKeys.write(account.units().items().get(i));
                denomination.putString("factor", account.units().factors().get(i).toString()); table.add(denomination);
            }
            entry.put("definition", table); entries.add(entry);
        });
        ListTag displays = new ListTag();
        cutoffs.forEach((key, display) -> { CompoundTag setting = BulkItemKeys.write(key); setting.put("display", BulkItemKeys.write(display)); displays.add(setting); });
        ListTag outputs = new ListTag();
        pending.forEach((key, amount) -> { CompoundTag entry = BulkItemKeys.write(key); entry.putString("amount", amount.toString()); outputs.add(entry); });
        data.put("entries", entries); data.put("cutoffs", displays); data.put("pending", outputs); data.put("unresolved", unresolved.copy());
        data.putInt("priority", storagePriority);
        custom.remove(LEGACY_TAG);
        if (accounts.isEmpty() && cutoffs.isEmpty() && pending.isEmpty() && unresolved.isEmpty()
            && storagePriority == 1) custom.remove(DATA_TAG);
        else custom.put(DATA_TAG, data);
        if (custom.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        stack.remove(AEComponents.STORAGE_CELL_INV);
        persisted = true;
    }

    private void load() {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean modern = custom.contains(DATA_TAG, Tag.TAG_COMPOUND);
        CompoundTag data = custom.getCompound(modern ? DATA_TAG : LEGACY_TAG);
        int version = data.getInt("version");
        if (version > 2) throw new IllegalStateException("Unsupported ECO bulk format " + version);
        if (data.contains("priority", Tag.TAG_INT)) storagePriority = data.getInt("priority");
        for (Tag raw : data.getList("entries", Tag.TAG_COMPOUND)) loadEntry((CompoundTag) raw, version);
        for (Tag raw : data.getList("unresolved", Tag.TAG_COMPOUND)) recover((CompoundTag) raw);
        for (Tag raw : data.getList("cutoffs", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            AEItemKey key = BulkItemKeys.read(entry), display = BulkItemKeys.read(entry.getCompound("display"));
            if (key != null && display != null) cutoffs.put(key, display);
        }
        for (Tag raw : data.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                AEItemKey key = BulkItemKeys.read(entry); BigInteger amount = new BigInteger(entry.getString("amount"));
                if (key == null || amount.signum() <= 0) throw new IllegalArgumentException();
                pending.merge(key, amount, BigInteger::add);
            } catch (RuntimeException invalid) { quarantine(entry, version, "pending"); }
        }
        // Ordinary legacy single-type cells stored physical stacks, rather than atom counts.
        List<GenericStack> legacyStacks = stack.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of());
        for (GenericStack stored : legacyStacks) if (stored.what() instanceof AEItemKey key && stored.amount() > 0) {
            merge(key, new Account(BulkUnits.single(key), BigInteger.valueOf(stored.amount())));
        }
        if (!legacyStacks.isEmpty() && getEffectiveConfiguredFilters().isEmpty())
            stack.set(AEComponents.STORAGE_CELL_CONFIG_INV, legacyStacks.stream().map(value -> new GenericStack(value.what(), 0)).toList());
        if (!data.isEmpty() || !legacyStacks.isEmpty()) persisted = false;
        checkedRevision = ECOCompressionService.revision();
    }
    private void refreshUnresolved() {
        long revision = ECOCompressionService.revision();
        if (recovering || revision == checkedRevision) return;
        checkedRevision = revision;
        if (unresolved.isEmpty()) return;
        recovering = true;
        try {
            var saved = unresolved.copy(); int previous = saved.size(); unresolved.clear();
            for (Tag raw : saved) recover((CompoundTag) raw);
            if (unresolved.size() != previous) saveChanges();
        } finally { recovering = false; }
    }
    private void recover(CompoundTag envelope) {
        if (envelope.contains("entry", Tag.TAG_COMPOUND)) loadEntry(envelope.getCompound("entry"), envelope.getInt("version"));
        else if (envelope.contains("pending", Tag.TAG_COMPOUND)) {
            CompoundTag entry = envelope.getCompound("pending");
            try {
                AEItemKey key = BulkItemKeys.read(entry); BigInteger amount = new BigInteger(entry.getString("amount"));
                if (key == null || amount.signum() <= 0) throw new IllegalArgumentException();
                pending.merge(key, amount, BigInteger::add);
            } catch (RuntimeException invalid) { unresolved.add(envelope.copy()); }
        } else unresolved.add(envelope.copy());
    }
    private void loadEntry(CompoundTag entry, int version) {
        try {
            AEItemKey key = BulkItemKeys.read(entry);
            BigInteger amount = version >= 2 ? new BigInteger(entry.getString("units")) : BigInteger.valueOf(entry.getLong("units"));
            if (key == null || amount.signum() <= 0) throw new IllegalArgumentException();
            BulkUnits units;
            if (version == 0) {
                units = ECOCompressionService.units(key);
                if (!units.compressible()) throw new IllegalArgumentException("Legacy unit basis is unavailable");
            } else {
                List<AEItemKey> items = new ArrayList<>(); List<BigInteger> factors = new ArrayList<>();
                for (Tag raw : entry.getList("definition", Tag.TAG_COMPOUND)) {
                    CompoundTag value = (CompoundTag) raw; AEItemKey item = BulkItemKeys.read(value);
                    if (item == null) throw new IllegalArgumentException();
                    items.add(item); factors.add(version >= 2 ? new BigInteger(value.getString("factor")) : BigInteger.valueOf(value.getLong("factor")));
                }
                units = new BulkUnits(items, factors);
            }
            if (!units.contains(key)) throw new IllegalArgumentException();
            merge(key, new Account(units, amount));
        } catch (RuntimeException invalid) { quarantine(entry, version, "entry"); }
    }
    private void merge(AEItemKey key, Account incoming) {
        for (var entry : accounts.entrySet()) if (entry.getValue().units().equals(incoming.units())) {
            entry.setValue(new Account(incoming.units(), entry.getValue().amount().add(incoming.amount()))); return;
        }
        if (accounts.putIfAbsent(key, incoming) != null) throw new IllegalArgumentException("Conflicting historical bulk accounts");
    }
    private void quarantine(CompoundTag entry, int version, String kind) {
        CompoundTag envelope = new CompoundTag(); envelope.putInt("version", version); envelope.put(kind, entry.copy()); unresolved.add(envelope);
    }

    // Exact bulk balances cannot use the long-only infinite-domain migration protocol.
    @Override public long insertForMigration(AEKey key, long amount, Actionable mode) { return insert(key, amount, mode, null); }
    @Override public long insertForMigration(AEKey key, long amount, Actionable mode, IActionSource source) { return insert(key, amount, mode, source); }
    @Override public void getMigrationStacks(KeyCounter out) { throw new UnsupportedOperationException("Bulk migration requires exact balances"); }
    @Override public void clearMigrationStacks() { throw new UnsupportedOperationException("Bulk migration requires exact balances"); }
    @Override public void clearAllStoredStacks() { throw new UnsupportedOperationException("Bulk balances cannot be cleared by a long-only migration"); }
}
