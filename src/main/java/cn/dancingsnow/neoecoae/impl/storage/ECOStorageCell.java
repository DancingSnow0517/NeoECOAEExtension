package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.IPartitionList;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.util.NEMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOStorageCell implements IECOStorageCell {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    @Nullable
    private final ISaveProvider container;
    private final IBasicECOCellItem cellType;
    @Getter
    private final AEKeyType keyType;
    // 过滤器
    @Getter
    private final IPartitionList partitionList;
    @Getter
    private final IncludeExclude partitionListMode;
    private final boolean hasVoidUpgrade;

    private final ItemStack cellStack;

    private final int maxItemTypes;
    private int storedItems;
    @Getter
    private long storedItemCount;
    private Object2LongMap<AEKey> storedAmounts;
    private long contentRevision;

    public long contentRevision() { return contentRevision; }
    private boolean isPersisted = true;
    private boolean persistenceDeferred;
    @Getter
    private final IECOTier tier;

    public ECOStorageCell(ItemStack cellStack, @Nullable ISaveProvider container) {
        this.container = container;
        this.cellStack = cellStack;

        if (cellStack.getItem() instanceof IBasicECOCellItem c) {
            keyType = c.getKeyType();
            maxItemTypes = c.getTotalTypes();
            var storedStacks = getStoredStacks();
            this.storedItems = storedStacks.size();
            this.storedItemCount = storedStacks.stream().mapToLong(GenericStack::amount).sum();
            this.storedAmounts = null;
            this.cellType = c;
            this.tier = c.getTier();

            // 根据已安装的升级和配置的过滤器更新分区列表和模式。
            var builder = IPartitionList.builder();

            var upgrades = getUpgradesInventory();
            var config = getConfigInventory();

            boolean hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD);
            boolean isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD);
            if (isFuzzy) {
                builder.fuzzyMode(c.getFuzzyMode(cellStack));
            }

            builder.addAll(config.keySet());

            partitionListMode = (hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
            partitionList = builder.build();

            this.hasVoidUpgrade = upgrades.isInstalled(AEItems.VOID_CARD);
        } else {
            throw new IllegalArgumentException("itemStack must be an ECOStorageCellItem");
        }
    }

    @Override
    public CellState getStatus() {
        if (this.getStoredItemTypes() == 0) {
            return CellState.EMPTY;
        }
        if (this.canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        if (this.getRemainingItemCount() > 0) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    public long getRemainingItemCount() {
        final long remaining = this.getFreeBytes() * keyType.getAmountPerByte() + this.getUnusedItemCount();
        return remaining > 0 ? remaining : 0;
    }

    public long getFreeBytes() {
        return this.getTotalBytes() - this.getUsedBytes();
    }

    public int getUnusedItemCount() {
        final int div = (int) (this.getStoredItemCount() % keyType.getAmountPerByte());

        if (div == 0) {
            return 0;
        }

        return keyType.getAmountPerByte() - div;
    }

    public int getBytesPerType() {
        return this.cellType.getBytesPerType();
    }

    public long getUsedBytes() {
        var bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / keyType.getAmountPerByte();
        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    public long getTotalBytes() {
        return cellType.getBytes();
    }

    public long getTotalItemTypes() {
        return this.maxItemTypes;
    }

    public long getRemainingItemTypes() {
        var basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        var baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    private boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType()
                || bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0)
                && this.getRemainingItemTypes() > 0;
    }

    public long getStoredItemTypes() {
        return storedItems;
    }

    private List<GenericStack> getStoredStacks() {
        return cellStack.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of());
    }

    public static boolean canStoreKeyInsideStorageCell(AEKey what) {
        if (what instanceof AEItemKey itemKey) {
            var stack = itemKey.toStack();
            var cellInv = StorageCells.getCellInventory(stack, null);
            return cellInv == null || cellInv.canFitInsideCell();
        }
        return true;
    }

    protected Object2LongMap<AEKey> getCellItems() {
        if (this.storedAmounts == null) {
            this.storedAmounts = new Object2LongOpenHashMap<>(maxItemTypes);
            this.loadCellItems();
        }

        return this.storedAmounts;
    }

    private void loadCellItems() {
        var stacks = getStoredStacks();
        for (var stack : stacks) {
            storedAmounts.put(stack.what(), stack.amount());
        }
    }

    @Override
    public double getIdleDrain() {
        return cellType.getIdleDrain();
    }

    @Override
    public void persist() {
        if (this.isPersisted) {
            return;
        }

        var itemCount = 0L;
        var stacks = new ArrayList<GenericStack>(storedAmounts.size());

        for (var entry : this.storedAmounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            itemCount = NEMath.saturatingAdd(itemCount, amount);

            if (amount > 0) {
                stacks.add(new GenericStack(entry.getKey(), amount));
            }
        }

        if (stacks.isEmpty()) {
            cellStack.remove(AEComponents.STORAGE_CELL_INV);
        } else {
            cellStack.set(AEComponents.STORAGE_CELL_INV, stacks);
        }

        int actualTypes = this.storedAmounts.size();
        if (actualTypes > this.maxItemTypes) {
            LOGGER.warn(
                    "ECO storage cell contains more types than allowed: actual={} max={} stack={}",
                    actualTypes,
                    this.maxItemTypes,
                    cellStack);
        }
        this.storedItems = actualTypes;

        this.storedItemCount = itemCount;
        this.isPersisted = true;
    }

    protected void saveChanges() {
        contentRevision++;
        this.isPersisted = false;
        if (persistenceDeferred) {
            return;
        }
        if (ECOCellMutationBatch.defer(this)) return;
        flushBatchedChanges();
    }

    void flushBatchedChanges() {
        // The host only marks its block entity dirty; it does not serialize this
        // transient inventory instance back into the cell stack for us.
        this.persist();
        if (this.container != null) {
            this.container.saveChanges();
        }
    }

    /**
     * Keeps logical mutations in memory while a controller finite-storage domain is active. The caller must invoke
     * {@link #materializeDeferredChanges()} before the cell stack can leave the controller.
     */
    public void deferPersistence() {
        persistenceDeferred = true;
    }

    protected boolean isPersistenceDeferred() {
        return persistenceDeferred;
    }

    /** Writes all deferred mutations to {@code STORAGE_CELL_INV} exactly once. */
    public void materializeDeferredChanges() {
        persistenceDeferred = false;
        persist();
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return 0L;
        if (amount == 0 || !keyType.contains(what)) {
            return 0;
        }

        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }

        if (this.cellType.isBlackListed(cellStack, what)) {
            return 0;
        }

        // 执行常规插入逻辑，然后对返回值应用虚空升级效果。
        long inserted = innerInsert(what, amount, mode);

        // 当虚空卡用于（已满的）未格式化元件时，确保不会虚空掉元件未存储且无法存储的物品。
        if (partitionList.isEmpty() && hasVoidUpgrade && !canHoldNewItem()) {
            return getCellItems().containsKey(what) ? amount : inserted;
        }

        return hasVoidUpgrade ? amount : inserted;
    }

    /** Inserts for a lossless migration without applying the void upgrade's reported acceptance. */
    public long insertForMigration(AEKey what, long amount, Actionable mode) {
        if (amount <= 0 || !keyType.contains(what)) {
            return 0;
        }
        if (!partitionList.matchesFilter(what, partitionListMode) || cellType.isBlackListed(cellStack, what)) {
            return 0;
        }
        return innerInsert(what, amount, mode);
    }

    /**
     * Computes migration capacity against a caller-owned inventory snapshot. A restore preflight must not mutate a
     * real cell because some cell implementations back copied ItemStacks with world-level storage.
     */
    public long simulateInsertForMigration(AEKey what, long amount, KeyCounter simulatedContents) {
        if (simulatedContents == null) return 0L;

        long currentAmount = simulatedContents.get(what);
        long storedTypes = 0L;
        long storedItemCount = 0L;
        for (Object2LongMap.Entry<AEKey> entry : simulatedContents) {
            if (entry.getLongValue() > 0L) {
                storedTypes = NEMath.saturatingAdd(storedTypes, 1L);
                storedItemCount = NEMath.saturatingAdd(storedItemCount, entry.getLongValue());
            }
        }

        return simulateInsertForMigration(what, amount, currentAmount, storedTypes, storedItemCount);
    }

    public long simulateInsertForMigration(AEKey what, long amount, long currentAmount, long storedTypes, long storedItemCount) {
        if (amount <= 0L || !keyType.contains(what) || !partitionList.matchesFilter(what, partitionListMode)
            || cellType.isBlackListed(cellStack, what) || !canStoreKeyInsideStorageCell(what)) return 0L;

        long amountPerByte = Math.max(1L, keyType.getAmountPerByte());
        long unusedItemCount = storedItemCount % amountPerByte == 0L
            ? 0L
            : amountPerByte - storedItemCount % amountPerByte;
        long bytesForItems = NEMath.saturatingAdd(storedItemCount, unusedItemCount) / amountPerByte;
        long typeBytes = NEMath.saturatingMultiply(storedTypes, getBytesPerType());
        long usedBytes = NEMath.saturatingAdd(typeBytes, bytesForItems);
        long freeBytes = Math.max(0L, getTotalBytes() - usedBytes);
        long remainingItemCount = NEMath.saturatingAdd(
            NEMath.saturatingMultiply(freeBytes, amountPerByte), unusedItemCount);
        long remainingTypes = Math.min(
            Math.max(0L, getTotalItemTypes() - Math.min(getTotalItemTypes(), storedTypes)),
            getBytesPerType() <= 0 ? 0L : freeBytes / getBytesPerType());

        if (currentAmount <= 0L) {
            boolean canHoldNewType = (freeBytes > getBytesPerType()
                || freeBytes == getBytesPerType() && unusedItemCount > 0L)
                && remainingTypes > 0L;
            if (!canHoldNewType) {
                return 0L;
            }
            remainingItemCount = Math.max(
                0L,
                remainingItemCount - NEMath.saturatingMultiply(getBytesPerType(), amountPerByte));
        }

        return Math.min(amount, remainingItemCount);
    }

    public long getUsedBytesForMigration(KeyCounter simulatedContents) {
        if (simulatedContents == null) {
            return 0L;
        }
        long storedTypes = 0L;
        long storedItemCount = 0L;
        for (Object2LongMap.Entry<AEKey> entry : simulatedContents) {
            if (entry.getLongValue() > 0L) {
                storedTypes = NEMath.saturatingAdd(storedTypes, 1L);
                storedItemCount = NEMath.saturatingAdd(storedItemCount, entry.getLongValue());
            }
        }
        long amountPerByte = Math.max(1L, keyType.getAmountPerByte());
        long unusedItemCount = storedItemCount % amountPerByte == 0L
            ? 0L
            : amountPerByte - storedItemCount % amountPerByte;
        long bytesForItems = NEMath.saturatingAdd(storedItemCount, unusedItemCount) / amountPerByte;
        return NEMath.saturatingAdd(
            NEMath.saturatingMultiply(storedTypes, getBytesPerType()), bytesForItems);
    }

    private long innerInsert(AEKey what, long amount, Actionable mode) {
        if (!canStoreKeyInsideStorageCell(what)) {
            return 0;
        }

        var currentAmount = this.getCellItems().getLong(what);
        long remainingItemCount = this.getRemainingItemCount();

        if (currentAmount <= 0) {
            if (!canHoldNewItem()) {
                // 无更多类型空间
                return 0;
            }

            remainingItemCount -= (long) this.getBytesPerType() * keyType.getAmountPerByte();
            if (remainingItemCount <= 0) {
                return 0;
            }
        }

        remainingItemCount = Math.max(0, Math.min(Long.MAX_VALUE - currentAmount, remainingItemCount));

        if (amount > remainingItemCount) {
            amount = remainingItemCount;
        }
        if (amount <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            long newAmount = currentAmount + amount;
            getCellItems().put(what, newAmount);
            if (currentAmount <= 0) {
                storedItems++;
            }
            storedItemCount = NEMath.saturatingAdd(storedItemCount, amount);
            this.saveChanges();
        }

        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0L || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return 0L;
        var currentAmount = getCellItems().getLong(what);
        if (currentAmount > 0) {
            if (amount >= currentAmount) {
                if (mode == Actionable.MODULATE) {
                    getCellItems().remove(what, currentAmount);
                    storedItems = Math.max(0, storedItems - 1);
                    storedItemCount = Math.max(0, storedItemCount - currentAmount);
                    this.saveChanges();
                }

                return currentAmount;
            } else {
                if (mode == Actionable.MODULATE) {
                    getCellItems().put(what, currentAmount - amount);
                    storedItemCount = Math.max(0, storedItemCount - amount);
                    this.saveChanges();
                }

                return amount;
            }
        }

        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        return !cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)
            && getAvailableStacks().isEmpty();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return;
        getMigrationStacks(out);
    }

    public void getMigrationStacks(KeyCounter out) {
        for (var entry : Object2LongMaps.fastIterable(this.getCellItems())) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    public java.util.Iterator<Object2LongMap.Entry<AEKey>> migrationEntries() {
        return getCellItems().object2LongEntrySet().iterator();
    }

    @Override
    public Component getDescription() {
        return cellStack.getHoverName();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        boolean used = !this.getCellItems().isEmpty() && this.insert(what, 1, Actionable.SIMULATE, source) == 1;
        boolean sameItem = this.extract(what, 1, Actionable.SIMULATE, source) > 0;
        return used || sameItem;
    }

    @Override
    public ECOCellType getCellType() {
        return ((ECOStorageCellItem) cellStack.getItem()).getCellType();
    }

    public IUpgradeInventory getUpgradesInventory() {
        return ((ECOStorageCellItem) cellStack.getItem()).getUpgrades(cellStack);
    }

    public ConfigInventory getConfigInventory() {
        return ((ECOStorageCellItem) cellStack.getItem()).getConfigInventory(cellStack);
    }

    public void clearAllStoredStacks() {
        getCellItems().clear();
        storedItems = 0;
        storedItemCount = 0;
        saveChanges();
    }

}
