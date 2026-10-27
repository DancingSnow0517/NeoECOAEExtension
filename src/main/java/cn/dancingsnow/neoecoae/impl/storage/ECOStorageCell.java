package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import cn.dancingsnow.neoecoae.platform.nbt.NECellNbt;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOCellReference;
import cn.dancingsnow.neoecoae.impl.storage.persistence.ECOCellSavedData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
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
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOStorageCell implements IECOStorageMigrationCell,
        cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage,
        cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource, StorageListingVersion {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
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

    /** Protected so specialised cells (for example the infinite resource cell) can read the cell stack. */
    protected final ItemStack cellStack;

    private final int maxItemTypes;
    private final ECOStorageRepository.Access storageAccess;
    public ECOCellSavedData storageData() { return storageAccess.data(); }
    public ECOStorageRepository.Session storageSession() { return storageAccess.session(); }
    public boolean storageWritable() { return storageAccess.writable(); }
    @Override public boolean isInfiniteStorageEligible() { return storageWritable(); }
    public long contentRevision() { return storageData().contentRevision(); }

    @Override
    public long neoecoae$listingVersion() {
        // Subclasses whose listing depends on other state must opt in by overriding this.
        return getClass() == ECOStorageCell.class ? contentListingVersion() : UNKNOWN;
    }

    /** For listings that are exactly {@link #getCellItems()} while the cell may be read. */
    protected final long contentListingVersion() {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)
                || !storageData().canRead() || !storageAccess.claimed() && storageAccess.session() != null) return -1L;
        return storageData().amounts().contentRevision();
    }
    public long getStoredItemCount() {
        return storageAccess.session() == null && ECOCellReference.id(cellStack) != null
            ? NEItemData.read(cellStack).getCompound(ECOCellReference.TAG).getLong("amount")
            : storageData().amounts().totalVisible();
    }
    public BigInteger getStoredItemCountExact() {
        if (storageAccess.session() == null && ECOCellReference.id(cellStack) != null) {
            var ref = NEItemData.read(cellStack).getCompound(ECOCellReference.TAG);
            return ref.contains("exactAmount") ? new BigInteger(ref.getString("exactAmount")) : BigInteger.valueOf(ref.getLong("amount"));
        }
        return storageData().amounts().totalExact();
    }
    private boolean isPersisted = true;
    private final Runnable persistenceNotification = () -> ECOCellMutationBatch.flush(this);
    @Getter
    private final IECOTier tier;

    public ECOStorageCell(ItemStack cellStack, @Nullable ISaveProvider container) {
        this.container = container;
        this.cellStack = cellStack;

        if (cellStack.getItem() instanceof IBasicECOCellItem c) {
            keyType = c.getKeyType();
            maxItemTypes = c.getTotalTypes();
            this.storageAccess = ECOStorageRepository.open(cellStack, container);
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
        if (!storageData().canMutate() || storageAccess.session() != null && !storageAccess.claimed()) return CellState.FULL;
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
        return NEMath.saturatingAdd(
            NEMath.saturatingMultiply(getFreeBytes(), Math.max(1L, keyType.getAmountPerByte())),
            getUnusedItemCount());
    }

    public long getFreeBytes() {
        return Math.max(0L, this.getTotalBytes() - this.getUsedBytes());
    }

    public int getUnusedItemCount() {
        int amountPerByte = Math.max(1, keyType.getAmountPerByte());
        if (getStoredItemCount() == Long.MAX_VALUE) {
            int remainder = getStoredItemCountExact().mod(BigInteger.valueOf(amountPerByte)).intValue();
            return remainder == 0 ? 0 : amountPerByte - remainder;
        }
        final int div = (int) (this.getStoredItemCount() % amountPerByte);

        if (div == 0) {
            return 0;
        }

        return amountPerByte - div;
    }

    public int getBytesPerType() {
        return this.cellType.getBytesPerType();
    }

    public long getUsedBytes() {
        if (getStoredItemCount() == Long.MAX_VALUE) return exactUsedBytes(getStoredItemTypes(), getStoredItemCountExact()).min(MAX_LONG).longValue();
        long storedAmount = Math.max(0L, getStoredItemCount());
        long amountPerByte = Math.max(1L, keyType.getAmountPerByte());
        long bytesForItems = ceilDivide(storedAmount, amountPerByte);
        return NEMath.saturatingAdd(
            NEMath.saturatingMultiply(getStoredItemTypes(), getBytesPerType()), bytesForItems);
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
        return storageAccess.session() == null && ECOCellReference.id(cellStack) != null
            ? NEItemData.read(cellStack).getCompound(ECOCellReference.TAG).getInt("types")
            : storageData().amounts().size();
    }

    private List<GenericStack> getStoredStacks() {
        return NECellNbt.readStacks(cellStack);
    }

    public static boolean canStoreKeyInsideStorageCell(AEKey what) {
        if (what instanceof AEItemKey itemKey) {
            // Only probed, never mutated: skip the per-insert ItemStack copy.
            var cellInv = StorageCells.getCellInventory(itemKey.getReadOnlyStack(), null);
            return cellInv == null || cellInv.canFitInsideCell();
        }
        return true;
    }

    protected Object2LongMap<AEKey> getCellItems() { return storageData().amounts().visibleAmounts(); }

    @Override
    public double getIdleDrain() {
        return cellType.getIdleDrain();
    }

    @Override
    public void persist() {
        if (storageAccess.session() == null) return;
        if (this.isPersisted && NEItemData.read(cellStack).getCompound(ECOCellReference.TAG)
                .getLong("revision") == storageData().contentRevision()) return;
        ECOCellReference.update(cellStack, storageData());
        this.isPersisted = true;
    }

    @Override
    public void persistMigrationContents(net.minecraft.server.level.ServerLevel level) {
        if (!storageWritable()) throw new IllegalStateException("ECO disk is unavailable or mounted twice");
        storageData().metadata(ECOCellReference.metadata(cellStack));
        if (!storageData().flush()) throw new IllegalStateException("Cannot persist ECO migration: " + storageData().failure());
        this.isPersisted = false;
        persist();
    }

    protected void saveChanges() {
        storageData().setDirty();
        this.isPersisted = false;
        if (ECOCellMutationBatch.defer(this)) return;
        if (storageAccess.session() != null) storageAccess.session().schedule(cellStack, persistenceNotification);
    }

    void flushBatchedChanges() {
        this.persist();
        if (this.container != null) this.container.saveChanges();
    }

    /** Specialized cells may accept multiple channels with the same amount-per-byte accounting. */
    protected boolean acceptsKey(AEKey key) {
        return keyType.contains(key);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return 0L;
        if (!storageWritable() || amount <= 0 || what == null || !acceptsKey(what)) {
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
        if (!storageWritable() || amount <= 0 || what == null || !acceptsKey(what)) {
            return 0;
        }
        if (!partitionList.matchesFilter(what, partitionListMode) || cellType.isBlackListed(cellStack, what)) {
            return 0;
        }
        return innerInsert(what, amount, mode);
    }

    /** Marks an in-memory mutation from a specialised cell implementation. */
    protected final void markContentChanged() {
        storageData().setDirty();
    }

    /** Joins the current controller mutation batch, if one is active. */
    protected final boolean deferMutationBatch() {
        return ECOCellMutationBatch.defer(this);
    }

    @Override
    public long insertForMigration(AEKey what, long amount, Actionable mode, IActionSource source) {
        return insertForMigration(what, amount, mode);
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
        if (amount <= 0L || !acceptsKey(what) || !partitionList.matchesFilter(what, partitionListMode)
            || cellType.isBlackListed(cellStack, what)) return 0L;

        // A key already present in the simulated contents was accepted when it was inserted.
        // Avoid probing nested-cell handlers again during migration preflight.
        if (currentAmount <= 0L && !canStoreKeyInsideStorageCell(what)) return 0L;

        long amountPerByte = Math.max(1L, keyType.getAmountPerByte());
        long unusedItemCount = storedItemCount % amountPerByte == 0L
            ? 0L
            : amountPerByte - storedItemCount % amountPerByte;
        long bytesForItems = ceilDivide(storedItemCount, amountPerByte);
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

    @Override
    public long simulateInsertForMigration(
        AEKey what,
        long amount,
        KeyCounter simulatedContents,
        long simulatedTypes,
        long simulatedAmount
    ) {
        return simulateInsertForMigration(
            what, amount, simulatedContents.get(what), simulatedTypes, simulatedAmount);
    }

    @Override
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
        long bytesForItems = ceilDivide(storedItemCount, amountPerByte);
        return NEMath.saturatingAdd(
            NEMath.saturatingMultiply(storedTypes, getBytesPerType()), bytesForItems);
    }

    private static long ceilDivide(long amount, long divisor) {
        long quotient = amount / divisor;
        return amount % divisor == 0L ? quotient : quotient + 1L;
    }

    private long innerInsert(AEKey what, long amount, Actionable mode) {
        if (getStoredItemCount() == Long.MAX_VALUE) return insertExact(what, BigInteger.valueOf(amount), mode).longValueExact();
        var currentAmount = this.getCellItems().getLong(what);
        long remainingItemCount = this.getRemainingItemCount();

        if (currentAmount <= 0) {
            if (!canStoreKeyInsideStorageCell(what)) {
                return 0;
            }
            if (!canHoldNewItem()) {
                // 无更多类型空间
                return 0;
            }

            remainingItemCount = Math.max(0L, remainingItemCount
                - NEMath.saturatingMultiply(this.getBytesPerType(), keyType.getAmountPerByte()));
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
            storageData().amounts().setLong(what, newAmount);
            this.saveChanges();
        }

        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!storageWritable() || amount <= 0L || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return 0L;
        if (storageData().amounts().isBig(what)) {
            if (mode == Actionable.MODULATE) { storageData().amounts().subtract(what, amount); saveChanges(); }
            return amount;
        }
        var currentAmount = getCellItems().getLong(what);
        if (currentAmount > 0) {
            if (amount >= currentAmount) {
                if (mode == Actionable.MODULATE) {
                    storageData().amounts().setLong(what, 0);
                    this.saveChanges();
                }

                return currentAmount;
            } else {
                if (mode == Actionable.MODULATE) {
                    storageData().amounts().setLong(what, currentAmount - amount);
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
            && storageData().canMutate() && getStoredItemTypes() == 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return;
        if (!storageData().canRead() || !storageAccess.claimed() && storageAccess.session() != null) return;
        getMigrationStacks(out);
    }

    @Override
    public void getMigrationStacks(KeyCounter out) {
        // The table's own fast iterator: the read-only view cannot hand one out and would allocate an entry per key.
        var amounts = storageData().amounts();
        if (out.isEmpty()) {
            // Keys are unique within the table, so a fresh counter needs one write per key and no merge.
            amounts.visitVisible(out::set);
            return;
        }
        amounts.visitVisible((key, value) -> {
            long existing = out.get(key);
            out.set(key, existing <= 0 ? value : NEMath.saturatingAdd(existing, value));
        });
    }

    @Override
    public java.util.Iterator<Object2LongMap.Entry<AEKey>> migrationEntries() {
        return getCellItems().object2LongEntrySet().iterator();
    }

    @Override
    public long getMigrationAmount(AEKey key) { return getCellItems().getLong(key); }

    @Override public BigInteger getMigrationAmountBigInteger(AEKey key) { return storageData().amounts().get(key).toBigInteger(); }

    private BigInteger exactUsedBytes(long types, BigInteger amount) {
        BigInteger perByte = BigInteger.valueOf(Math.max(1, keyType.getAmountPerByte()));
        return amount.add(perByte).subtract(BigInteger.ONE).divide(perByte)
            .add(BigInteger.valueOf(types).multiply(BigInteger.valueOf(getBytesPerType())));
    }

    public BigInteger simulateInsertForMigrationBigInteger(AEKey key, BigInteger offered,
            BigInteger current, long types, BigInteger total) {
        if (key == null || offered == null || offered.signum() <= 0 || !acceptsKey(key)
            || !partitionList.matchesFilter(key, partitionListMode) || cellType.isBlackListed(cellStack, key)) return BigInteger.ZERO;
        if (current.signum() == 0 && !canStoreKeyInsideStorageCell(key)) return BigInteger.ZERO;
        BigInteger perByte = BigInteger.valueOf(Math.max(1, keyType.getAmountPerByte()));
        BigInteger free = BigInteger.valueOf(getTotalBytes()).subtract(exactUsedBytes(types, total)).max(BigInteger.ZERO);
        BigInteger unused = total.mod(perByte).signum() == 0 ? BigInteger.ZERO : perByte.subtract(total.mod(perByte));
        BigInteger remaining = free.multiply(perByte).add(unused);
        if (current.signum() == 0) {
            BigInteger typeBytes = BigInteger.valueOf(getBytesPerType());
            if (types >= getTotalItemTypes() || free.compareTo(typeBytes) < 0
                || free.equals(typeBytes) && unused.signum() == 0) return BigInteger.ZERO;
            remaining = remaining.subtract(typeBytes.multiply(perByte)).max(BigInteger.ZERO);
        }
        return offered.min(remaining);
    }

    private BigInteger insertExact(AEKey key, BigInteger amount, Actionable mode) {
        BigInteger current = getMigrationAmountBigInteger(key);
        BigInteger inserted = simulateInsertForMigrationBigInteger(key, amount, current,
            getStoredItemTypes(), getStoredItemCountExact());
        if (mode == Actionable.MODULATE && inserted.signum() > 0) {
            storageData().amounts().set(key, HugeAmount.of(current.add(inserted))); saveChanges();
        }
        return inserted;
    }

    @Override public BigInteger insertBigInteger(AEKey key, BigInteger amount, Actionable mode, IActionSource source) {
        if (!storageWritable() || key == null || amount == null || amount.signum() <= 0
            || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)
            || !acceptsKey(key) || !partitionList.matchesFilter(key, partitionListMode)
            || cellType.isBlackListed(cellStack, key)) return BigInteger.ZERO;
        if (amount.compareTo(MAX_LONG) <= 0 && getStoredItemCount() < Long.MAX_VALUE)
            return BigInteger.valueOf(insert(key, amount.longValueExact(), mode, source));
        BigInteger inserted = insertExact(key, amount, mode);
        if (!hasVoidUpgrade) return inserted;
        return partitionList.isEmpty() && !canHoldNewItem() && getMigrationAmountBigInteger(key).signum() == 0 ? inserted : amount;
    }

    @Override public BigInteger insertForMigrationBigInteger(AEKey key, BigInteger amount, Actionable mode, IActionSource source) {
        if (!storageWritable() || key == null || amount == null || amount.signum() <= 0) return BigInteger.ZERO;
        return insertExact(key, amount, mode);
    }

    @Override public BigInteger extractBigInteger(AEKey key, BigInteger amount, Actionable mode, IActionSource source) {
        if (!storageWritable() || key == null || amount == null || amount.signum() <= 0
            || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return BigInteger.ZERO;
        BigInteger current = getMigrationAmountBigInteger(key), extracted = current.min(amount);
        if (mode == Actionable.MODULATE && extracted.signum() > 0) {
            storageData().amounts().set(key, HugeAmount.of(current.subtract(extracted))); saveChanges();
        }
        return extracted;
    }

    @Override public void neoecoae$visitExactAmounts(java.util.function.BiConsumer<AEKey,
            cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> visitor) {
        if (!storageData().canRead() || storageAccess.session() != null && !storageAccess.claimed()
            || cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(cellStack)) return;
        storageData().amounts().visitExact((key, amount) -> visitor.accept(key,
            cn.dancingsnow.neoecoae.crafting.amount.ExactAmount.finite(amount)));
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
        if (!storageWritable()) throw new IllegalStateException("Cannot clear unavailable ECO disk");
        if (storageData().amounts().isEmpty()) return;
        storageData().amounts().clear();
        saveChanges();
    }

    @Override
    public void clearMigrationStacks() {
        clearAllStoredStacks();
    }

}
