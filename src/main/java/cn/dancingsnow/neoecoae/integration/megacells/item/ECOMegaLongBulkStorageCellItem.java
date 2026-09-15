package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.integration.megacells.NEMegaItems;
import cn.dancingsnow.neoecoae.integration.megacells.backend.ECOMegaLongBulkStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ItemLike;

public final class ECOMegaLongBulkStorageCellItem extends ECOStorageCellItem {
    public ECOMegaLongBulkStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type) {
        super(
                properties,
                tier,
                AEKeyType.items(),
                type,
                Long.MAX_VALUE,
                MegaCellCapacities.normalBytesPerType(tier),
                MegaCellCapacities.LONG_BULK_TYPE_LIMIT,
                1024.0);
    }

    @Override
    public int getTotalTypes() {
        return MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        // Keep all 50 slots persisted even while the second page is inactive. The storage
        // inventory itself only exposes the first 25 until the upgrade card is installed.
        return CellConfig.create(
                key -> key.getType() == AEKeyType.items(), stack, MegaCellCapacities.LONG_BULK_UPGRADED_TYPE_LIMIT);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return new LockedUpgradeInventory(stack, super.getUpgrades(stack));
    }

    private static boolean hasTooManyMarkers(ItemStack stack) {
        return CellConfig.create(
                                key -> key.getType() == AEKeyType.items(),
                                stack,
                                MegaCellCapacities.LONG_BULK_UPGRADED_TYPE_LIMIT)
                        .keySet()
                        .size()
                >= MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
    }

    private static void compactMarkers(ItemStack stack) {
        ConfigInventory config = CellConfig.create(
                key -> key.getType() == AEKeyType.items(), stack, MegaCellCapacities.LONG_BULK_UPGRADED_TYPE_LIMIT);
        var configured = new java.util.ArrayList<appeng.api.stacks.GenericStack>();
        for (int slot = 0; slot < config.size(); slot++) {
            if (config.getStack(slot) != null) configured.add(config.getStack(slot));
        }
        for (int slot = 0; slot < config.size(); slot++) {
            config.setStack(slot, slot < configured.size() ? configured.get(slot) : null);
        }
    }

    private static final class LockedUpgradeInventory implements IUpgradeInventory {
        private final ItemStack cellStack;
        private final IUpgradeInventory delegate;

        private LockedUpgradeInventory(ItemStack cellStack, IUpgradeInventory delegate) {
            this.cellStack = cellStack;
            this.delegate = delegate;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            if (isLockedCard(slot) && (stack.isEmpty() || !stack.is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get()))) {
                return;
            }
            boolean removingCard = delegate.getStackInSlot(slot).is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get())
                    && !stack.is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get());
            delegate.setItemDirect(slot, stack);
            if (removingCard) {
                compactMarkers(cellStack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (isLockedCard(slot)) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = delegate.extractItem(slot, amount, simulate);
            if (!simulate && extracted.is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get())) {
                compactMarkers(cellStack);
            }
            return extracted;
        }

        private boolean isLockedCard(int slot) {
            ItemStack installed = delegate.getStackInSlot(slot);
            return !installed.isEmpty()
                    && installed.is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get())
                    && hasTooManyMarkers(cellStack);
        }

        @Override
        public ItemLike getUpgradableItem() {
            return delegate.getUpgradableItem();
        }

        @Override
        public int getInstalledUpgrades(ItemLike upgrade) {
            return delegate.getInstalledUpgrades(upgrade);
        }

        @Override
        public int getMaxInstalled(ItemLike upgrade) {
            return delegate.getMaxInstalled(upgrade);
        }

        @Override
        public void readFromNBT(CompoundTag data, String subtag) {
            delegate.readFromNBT(data, subtag);
        }

        @Override
        public void writeToNBT(CompoundTag data, String subtag) {
            delegate.writeToNBT(data, subtag);
        }
    }

    @Override
    protected ECOStorageCell createCellInventory(ItemStack stack, ISaveProvider host) {
        return new ECOMegaLongBulkStorageCell(stack, host);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level,
            List<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        MegaCellTooltips.append(this, lines);
        lines.add(Component.translatable("tooltip.neoecoae.megacells.configure_item")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.neoecoae.megacells.compression_builtin")
                .withStyle(ChatFormatting.GRAY));
    }
}
