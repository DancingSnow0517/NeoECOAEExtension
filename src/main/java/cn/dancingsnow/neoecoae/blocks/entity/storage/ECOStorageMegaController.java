package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.IECOBulkMarkableCellItem;

import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Handles Mega bulk-cell selection, upgrades, marker validation and item-handler adapters. */
final class ECOStorageMegaController {
    private static final ResourceLocation ECO_MEGA_UPGRADE_CARD_ID =
        ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "eco_mega_upgrade_card");
    private static final int ECO_MEGA_SLOTS_PER_PAGE = 25;
    private static final int ECO_MEGA_PAGE_COUNT = 2;
    private final IItemHandlerModifiable ecoMegaUpgradeItemHandler = new EcoMegaUpgradeItemHandler();
    private final IItemHandlerModifiable ecoMegaFilterItemHandler = new EcoMegaFilterItemHandler();

    private final ECOStorageSystemBlockEntity host;

    ECOStorageMegaController(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    IItemHandlerModifiable upgradeItemHandler() {
        return ecoMegaUpgradeItemHandler;
    }

    IItemHandlerModifiable filterItemHandler() {
        return ecoMegaFilterItemHandler;
    }

    public boolean hasEcoMegaUpgradeCard() {
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        int driveIndex = getSelectedEcoMegaBulkCell();
        return driveIndex >= 0 && driveIndex < drives.size()
            && hasEcoMegaUpgradeCard(drives.get(driveIndex).getCellStack());
    }

    private boolean hasEcoMegaUpgradeCard(@Nullable ItemStack cellStack) {
        if (cellStack == null || cellStack.isEmpty()
            || !(cellStack.getItem() instanceof IECOBulkMarkableCellItem cellItem)) {
            return false;
        }
        var upgrades = cellItem.getUpgrades(cellStack);
        if (upgrades == null) {
            return false;
        }
        for (ItemStack upgrade : upgrades) {
            if (!upgrade.isEmpty()
                && ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(upgrade.getItem()))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private appeng.api.upgrades.IUpgradeInventory getSelectedEcoMegaUpgradeInventory() {
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        int driveIndex = getSelectedEcoMegaBulkCell();
        if (driveIndex < 0 || driveIndex >= drives.size()) {
            return null;
        }
        ItemStack cellStack = drives.get(driveIndex).getCellStack();
        if (cellStack == null || cellStack.isEmpty()
            || !(cellStack.getItem() instanceof IECOBulkMarkableCellItem cellItem)) {
            return null;
        }
        return cellItem.getUpgrades(cellStack);
    }

    private void onSelectedEcoMegaUpgradeChanged() {
        host.selectEcoMegaPage(Math.clamp(host.selectedEcoMegaPage(), 0, getEcoMegaPageCount() - 1));
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        int driveIndex = getSelectedEcoMegaBulkCell();
        if (driveIndex >= 0 && driveIndex < drives.size()) {
            drives.get(driveIndex).onCellConfigurationChanged();
        }
        host.notifyStorageConfigurationChanged();
        host.setChanged();
        host.markForUpdate();
    }

    public boolean hasEcoMegaBulkCell() {
        return !getEcoMegaBulkDrives().isEmpty();
    }

    public int getEcoMegaBulkCellCount() {
        return getEcoMegaBulkDrives().size();
    }

    public int getSelectedEcoMegaBulkCell() {
        int count = getEcoMegaBulkCellCount();
        host.selectEcoMegaBulkCell(count == 0 ? 0 : Math.clamp(host.selectedEcoMegaBulkCell(), 0, count - 1));
        return host.selectedEcoMegaBulkCell();
    }

    public int getEcoMegaPageCount() {
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        int index = getSelectedEcoMegaBulkCell();
        int slots = index < drives.size() ? getActiveMarkerSlots(drives.get(index).getCellStack()) : 0;
        return Math.max(1, (slots + ECO_MEGA_SLOTS_PER_PAGE - 1) / ECO_MEGA_SLOTS_PER_PAGE);
    }

    private int getActiveMarkerSlots(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()
            || !(stack.getItem() instanceof IECOBulkMarkableCellItem cellItem)) {
            return 0;
        }
        return Math.clamp(cellItem.getConfigInventory(stack).size(), 0,
            ECO_MEGA_SLOTS_PER_PAGE * ECO_MEGA_PAGE_COUNT);
    }

    public boolean isEcoMegaFilterSlotAvailable(int visualSlot) {
        if (visualSlot < 0 || visualSlot >= ECO_MEGA_SLOTS_PER_PAGE) {
            return false;
        }
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        int index = getSelectedEcoMegaBulkCell();
        return index < drives.size() && getSelectedEcoMegaPage() * ECO_MEGA_SLOTS_PER_PAGE + visualSlot
            < getActiveMarkerSlots(drives.get(index).getCellStack());
    }

    public int getSelectedEcoMegaPage() {
        host.selectEcoMegaPage(Math.clamp(host.selectedEcoMegaPage(), 0, getEcoMegaPageCount() - 1));
        return host.selectedEcoMegaPage();
    }

    public void changeSelectedEcoMegaBulkCell(int delta) {
        int count = getEcoMegaBulkCellCount();
        if (count <= 0) {
            host.selectEcoMegaBulkCell(0);
            host.selectEcoMegaPage(0);
            return;
        }
        host.selectEcoMegaBulkCell(Math.floorMod(getSelectedEcoMegaBulkCell() + delta, count));
        host.selectEcoMegaPage(0);
        host.setChanged();
        host.markForUpdate();
    }

    public void changeSelectedEcoMegaPage(int delta) {
        host.selectEcoMegaPage(Math.floorMod(getSelectedEcoMegaPage() + delta, getEcoMegaPageCount()));
        host.setChanged();
        host.markForUpdate();
    }

    EcoMegaFilterResult setEcoMegaFilterDirect(int driveIndex, int page, int visualSlot, ItemStack stack) {
        if (visualSlot < 0 || visualSlot >= ECO_MEGA_SLOTS_PER_PAGE
            || page < 0 || page >= ECO_MEGA_PAGE_COUNT) {
            return EcoMegaFilterResult.INVALID_TARGET;
        }
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        if (driveIndex < 0 || driveIndex >= drives.size()
            || page * ECO_MEGA_SLOTS_PER_PAGE + visualSlot
                >= getActiveMarkerSlots(drives.get(driveIndex).getCellStack())) {
            return EcoMegaFilterResult.INVALID_TARGET;
        }
        ItemStack normalized = ItemStack.EMPTY;
        if (stack != null && !stack.isEmpty()) {
            normalized = StorageBulkMarkingIntegration.normalizeMarker(stack);
            if (normalized.isEmpty()) {
                return EcoMegaFilterResult.NOT_COMPRESSIBLE;
            }
            if (hasDuplicateEcoMegaMarker(drives, driveIndex, page, visualSlot, normalized)) {
                return EcoMegaFilterResult.DUPLICATE_CHAIN;
            }
        }
        ECODriveBlockEntity drive = drives.get(driveIndex);
        ItemStack cellStack = drive.getCellStack();
        if (cellStack == null || cellStack.isEmpty()
            || !(cellStack.getItem() instanceof IECOBulkMarkableCellItem cellItem)) {
            return EcoMegaFilterResult.INVALID_TARGET;
        }
        AEItemKey key = normalized.isEmpty() ? null : AEItemKey.of(normalized);
        cellItem.getConfigInventory(cellStack).setStack(
            page * ECO_MEGA_SLOTS_PER_PAGE + visualSlot,
            key == null ? null : new GenericStack(key, 0L)
        );
        drive.onCellConfigurationChanged();
        host.notifyStorageConfigurationChanged();
        return EcoMegaFilterResult.SUCCESS;
    }

    enum EcoMegaFilterResult {
        SUCCESS,
        NOT_COMPRESSIBLE,
        DUPLICATE_CHAIN,
        INVALID_TARGET
    }

    private boolean hasDuplicateEcoMegaMarker(
        List<ECODriveBlockEntity> drives,
        int driveIndex,
        int page,
        int visualSlot,
        ItemStack candidate
    ) {
        for (int index = 0; index < drives.size(); index++) {
            ItemStack cellStack = drives.get(index).getCellStack();
            if (cellStack == null || cellStack.isEmpty()
                || !(cellStack.getItem() instanceof IECOBulkMarkableCellItem cellItem)) {
                continue;
            }
            var config = cellItem.getConfigInventory(cellStack);
            int activeSlots = getActiveMarkerSlots(cellStack);
            for (int slot = 0; slot < Math.min(config.size(), activeSlots); slot++) {
                if (index == driveIndex
                    && slot == page * ECO_MEGA_SLOTS_PER_PAGE + visualSlot) {
                    continue;
                }
                AEKey configured = config.getKey(slot);
                if (configured instanceof AEItemKey itemKey
                    && StorageBulkMarkingIntegration.isSameMarkerChain(candidate, itemKey.toStack())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<ECODriveBlockEntity> getEcoMegaBulkDrives() {
        return host.getStorageDrivesForIntegration().stream()
            .filter(drive -> {
                ItemStack stack = drive.getCellStack();
                return stack != null && !stack.isEmpty()
                    && stack.getItem() instanceof IECOBulkMarkableCellItem;
            })
            .sorted(java.util.Comparator.comparingLong(drive -> drive.getBlockPos().asLong()))
            .toList();
    }

    void autoMarkBulkCells(Player player) {
        if (!host.canPlayerInteract(player)) {
            return;
        }
        StorageBulkMarkingIntegration.MarkResult result = StorageBulkMarkingIntegration.autoMark(
            host, NEConfig.megaBulkAutoMarkThreshold);
        String key = switch (result.status()) {
            case SUCCESS -> "gui.neoecoae.storage.bulk_mark.result.success";
            case NO_BULK_CELL -> "gui.neoecoae.storage.bulk_mark.result.no_bulk_cell";
            case BUSY -> "gui.neoecoae.storage.bulk_mark.result.busy";
            case INVALID_THRESHOLD -> "gui.neoecoae.storage.bulk_mark.result.invalid_threshold";
            case UNAVAILABLE -> "gui.neoecoae.storage.bulk_mark.result.unavailable";
        };
        net.minecraft.network.chat.Component message = result.status() == StorageBulkMarkingIntegration.Status.SUCCESS
            ? net.minecraft.network.chat.Component.translatable(
                key, result.added(), result.alreadyMarked(), result.noSpace(), result.transferred())
            : net.minecraft.network.chat.Component.translatable(key);
        player.displayClientMessage(message, true);
    }

    private final class EcoMegaUpgradeItemHandler implements IItemHandlerModifiable {
        private ItemStack clientDisplayStack = ItemStack.EMPTY;

        private boolean isClientHandler() {
            return host.getLevel() != null && host.getLevel().isClientSide;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }
            if (isClientHandler()) {
                return clientDisplayStack;
            }
            var upgrades = getSelectedEcoMegaUpgradeInventory();
            if (upgrades != null) {
                for (ItemStack stack : upgrades) {
                    if (!stack.isEmpty()
                        && ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                        return stack;
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot != 0 || !stack.isEmpty()
                && !ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                return;
            }
            if (isClientHandler()) {
                clientDisplayStack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
                return;
            }
            ItemStack current = getStackInSlot(0);
            if (!current.isEmpty()) {
                extractItem(0, 1, false);
            }
            if (!stack.isEmpty()) {
                insertItem(0, stack.copyWithCount(1), false);
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()
                || !ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                return stack;
            }
            if (isClientHandler()) {
                if (!clientDisplayStack.isEmpty()) {
                    return stack;
                }
                if (!simulate) {
                    clientDisplayStack = stack.copyWithCount(1);
                }
                if (stack.getCount() == 1) {
                    return ItemStack.EMPTY;
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(1);
                return remainder;
            }
            var upgrades = getSelectedEcoMegaUpgradeInventory();
            if (upgrades == null) {
                return stack;
            }
            ItemStack remainder = upgrades.addItems(stack.copyWithCount(1), simulate);
            if (!simulate && remainder.isEmpty()) {
                onSelectedEcoMegaUpgradeChanged();
            }
            if (stack.getCount() <= 1 || !remainder.isEmpty()) {
                return remainder.isEmpty() ? ItemStack.EMPTY : stack;
            }
            ItemStack result = stack.copy();
            result.shrink(1);
            return result;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            if (isClientHandler()) {
                if (clientDisplayStack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                ItemStack extracted = clientDisplayStack.copyWithCount(1);
                if (!simulate) {
                    clientDisplayStack = ItemStack.EMPTY;
                }
                return extracted;
            }
            var upgrades = getSelectedEcoMegaUpgradeInventory();
            if (upgrades == null) {
                return ItemStack.EMPTY;
            }
            for (int upgradeSlot = 0; upgradeSlot < upgrades.size(); upgradeSlot++) {
                ItemStack stack = upgrades.getStackInSlot(upgradeSlot);
                if (!stack.isEmpty()
                    && ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                    ItemStack extracted = upgrades.extractItem(upgradeSlot, 1, simulate);
                    if (!simulate && !extracted.isEmpty()) {
                        onSelectedEcoMegaUpgradeChanged();
                    }
                    return extracted;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 1 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && !stack.isEmpty()
                && ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
    }

    private final class EcoMegaFilterItemHandler implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            return ECO_MEGA_SLOTS_PER_PAGE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= ECO_MEGA_SLOTS_PER_PAGE) {
                return ItemStack.EMPTY;
            }
            List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
            int driveIndex = getSelectedEcoMegaBulkCell();
            if (driveIndex < 0 || driveIndex >= drives.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack cellStack = drives.get(driveIndex).getCellStack();
            if (cellStack == null || cellStack.isEmpty()
                || !(cellStack.getItem() instanceof IECOBulkMarkableCellItem cellItem)) {
                return ItemStack.EMPTY;
            }
            int configSlot = getSelectedEcoMegaPage() * ECO_MEGA_SLOTS_PER_PAGE + slot;
            var config = cellItem.getConfigInventory(cellStack);
            if (configSlot >= config.size()) {
                return ItemStack.EMPTY;
            }
            AEKey key = config.getKey(configSlot);
            return key instanceof AEItemKey itemKey ? itemKey.toStack() : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            setEcoMegaFilterDirect(getSelectedEcoMegaBulkCell(), getSelectedEcoMegaPage(), slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isEcoMegaFilterSlotAvailable(slot) && !stack.isEmpty();
        }
    }
}
