package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.IPartitionList;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IBatchedECOCellSaveProvider;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ECOStorageCell implements IECOStorageCell {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable private final ISaveProvider container;

    private final IBasicECOCellItem cellType;

    @Getter
    private final AEKeyType keyType;
    // filter
    @Getter
    private final IPartitionList partitionList;

    @Getter
    private final IncludeExclude partitionListMode;

    private final boolean hasVoidUpgrade;

    private final ItemStack cellStack;

    private final int maxItemTypes;

    private final ECOCellContents contents;

    @Getter
    private final IECOTier tier;

    public ECOStorageCell(ItemStack cellStack, @Nullable ISaveProvider container) {
        this.container = container;
        this.cellStack = cellStack;

        if (cellStack.getItem() instanceof IBasicECOCellItem c) {
            keyType = c.getKeyType();
            maxItemTypes = c.getTotalTypes();
            this.contents = new ECOCellContents(cellStack, maxItemTypes);
            this.cellType = c;
            this.tier = c.getTier();

            // Updates the partition list and mode based on installed upgrades and the
            // configured filter.
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
        return capacity().status();
    }

    public long getRemainingItemCount() {
        return capacity().remainingItemCount();
    }

    public long getFreeBytes() {
        return capacity().freeBytes();
    }

    public int getUnusedItemCount() {
        return capacity().unusedItemCount();
    }

    public int getBytesPerType() {
        return this.cellType.getBytesPerType();
    }

    public long getUsedBytes() {
        return capacity().usedBytes();
    }

    public long getTotalBytes() {
        return cellType.getBytes();
    }

    public long getTotalItemTypes() {
        return this.maxItemTypes;
    }

    public long getRemainingItemTypes() {
        return capacity().remainingItemTypes();
    }

    private boolean canHoldNewItem() {
        return capacity().canHoldNewItem();
    }

    public long getStoredItemTypes() {
        return contents.storedItemTypes();
    }

    public long getStoredItemCount() {
        return contents.storedItemCount();
    }

    private ECOCellCapacity capacity() {
        return new ECOCellCapacity(
                keyType,
                getTotalBytes(),
                getBytesPerType(),
                getTotalItemTypes(),
                getStoredItemTypes(),
                getStoredItemCount());
    }

    @Override
    public double getIdleDrain() {
        return (double) cellType.getIdleDrainBytes() / (1 << 20);
    }

    @Override
    public void persist() {
        if (this.contents.isPersisted()) {
            return;
        }

        int actualTypes = contents.persist();
        if (actualTypes > this.maxItemTypes) {
            LOGGER.warn(
                    "ECO storage cell contains more types than allowed: actual={} max={} stack={}",
                    actualTypes,
                    this.maxItemTypes,
                    cellStack);
        }
    }

    protected void saveChanges() {
        this.contents.markDirty();
        if (this.container == null) {
            this.persist();
        } else if (this.container instanceof IBatchedECOCellSaveProvider) {
            this.container.saveChanges();
        } else {
            this.persist();
            this.container.saveChanges();
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !keyType.contains(what)) {
            return 0;
        }

        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }

        if (this.cellType.isBlackListed(cellStack, what)) {
            return 0;
        }

        // Run regular insert logic and then apply void upgrade to the returned value.
        long inserted = innerInsert(what, amount, mode);

        // In the event that a void card is being used on a (full) unformatted cell,
        // ensure it doesn't void any items
        // that the cell isn't even storing and cannot store to begin with
        if (partitionList.isEmpty() && hasVoidUpgrade && !canHoldNewItem()) {
            return contents.contains(what) ? amount : inserted;
        }

        return hasVoidUpgrade ? amount : inserted;
    }

    private long innerInsert(AEKey what, long amount, Actionable mode) {
        if (what instanceof AEItemKey itemKey) {
            var stack = itemKey.toStack();

            var cellInv = StorageCells.getCellInventory(stack, null);
            if (cellInv != null && !cellInv.canFitInsideCell()) {
                return 0;
            }
        }

        var currentAmount = contents.getAmount(what);
        long remainingItemCount = this.getRemainingItemCount();

        if (currentAmount <= 0) {
            if (!canHoldNewItem()) {
                // No space for more types
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

        if (mode == Actionable.MODULATE) {
            contents.add(what, amount);
            this.saveChanges();
        }

        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var currentAmount = contents.getAmount(what);
        if (currentAmount > 0) {
            if (amount >= currentAmount) {
                if (mode == Actionable.MODULATE) {
                    contents.remove(what, currentAmount);
                    this.saveChanges();
                }

                return currentAmount;
            } else {
                if (mode == Actionable.MODULATE) {
                    contents.subtract(what, amount);
                    this.saveChanges();
                }

                return amount;
            }
        }

        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        return contents.isEmpty();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        contents.addTo(out);
    }

    @Override
    public Component getDescription() {
        return cellStack.getHoverName();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        boolean used = !contents.isEmpty() && this.insert(what, 1, Actionable.SIMULATE, source) == 1;
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
}
