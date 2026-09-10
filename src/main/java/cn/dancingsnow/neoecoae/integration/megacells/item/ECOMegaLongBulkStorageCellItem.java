package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.integration.megacells.NEMegaItems;
import cn.dancingsnow.neoecoae.integration.megacells.backend.ECOMegaLongBulkStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ECOMegaLongBulkStorageCellItem extends ECOStorageCellItem {
    public ECOMegaLongBulkStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type) {
        super(properties, tier, AEKeyType.items(), type, Long.MAX_VALUE,
            MegaCellCapacities.normalBytesPerType(tier), 1024.0);
    }

    @Override
    public int getTotalTypes() {
        return MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        int slots = getUpgrades(stack).isInstalled(NEMegaItems.ECO_MEGA_UPGRADE_CARD)
            ? MegaCellCapacities.LONG_BULK_UPGRADED_TYPE_LIMIT
            : MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
        return createPreservingConfigInventory(stack, slots);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return new LockedUpgradeInventory(stack, super.getUpgrades(stack));
    }

    private static ConfigInventory createPreservingConfigInventory(ItemStack stack, int activeSlots) {
        ConfigInventory[] holder = new ConfigInventory[1];
        holder[0] = ConfigInventory.configTypes(activeSlots)
            .supportedTypes(Set.of(AEKeyType.items()))
            .changeListener(() -> saveConfigInventory(stack, holder[0]))
            .build();
        // Loading is read-only: a workbench may be copying this stack at the same time.
        // Do not publish partially loaded configurations through the change listener.
        holder[0].beginBatch();
        try {
            holder[0].readFromList(stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of()));
        } finally {
            holder[0].endBatchSuppressed();
        }
        return holder[0];
    }

    private static void saveConfigInventory(ItemStack stack, ConfigInventory activeInventory) {
        List<GenericStack> stored = new ArrayList<>(
            stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of()));
        while (stored.size() < MegaCellCapacities.LONG_BULK_UPGRADED_TYPE_LIMIT) {
            stored.add(null);
        }
        List<GenericStack> active = activeInventory.toList();
        for (int slot = 0; slot < active.size(); slot++) {
            stored.set(slot, active.get(slot));
        }
        stack.set(AEComponents.STORAGE_CELL_CONFIG_INV, stored);
    }

    private static boolean hasTooManyMarkers(ItemStack stack) {
        List<GenericStack> configured = stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of());
        return configured.stream().filter(java.util.Objects::nonNull).count()
            >= MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
    }

    private static void compactMarkers(ItemStack stack) {
        List<GenericStack> configured = stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of());
        stack.set(AEComponents.STORAGE_CELL_CONFIG_INV,
            configured.stream().filter(java.util.Objects::nonNull).toList());
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
            if (isLockedCard(slot) && (stack.isEmpty()
                || !stack.is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get()))) {
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
        public void readFromNBT(CompoundTag data, String subtag, HolderLookup.Provider registries) {
            delegate.readFromNBT(data, subtag, registries);
        }

        @Override
        public void writeToNBT(CompoundTag data, String subtag, HolderLookup.Provider registries) {
            delegate.writeToNBT(data, subtag, registries);
        }
    }

    @Override
    protected ECOStorageCell createCellInventory(ItemStack stack, ISaveProvider host) {
        return new ECOMegaLongBulkStorageCell(stack, host);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        MegaCellTooltips.append(this, lines);
        lines.add(Component.translatable("tooltip.neoecoae.megacells.configure_item")
            .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.neoecoae.megacells.compression_builtin")
            .withStyle(ChatFormatting.GRAY));
    }
}
