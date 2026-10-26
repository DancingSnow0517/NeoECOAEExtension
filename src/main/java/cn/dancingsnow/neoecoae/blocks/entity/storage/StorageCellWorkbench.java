package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.ids.AEComponents;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.blockentity.misc.CellWorkbenchBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.menu.slot.AppEngSlot;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCellSettings;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** An AE2 workbench view of a drive's installed stack; it never owns a second physical cell. */
public final class StorageCellWorkbench extends CellWorkbenchBlockEntity {
    private final ECOStorageSystemBlockEntity controller;
    private final BlockPos drivePosition;
    private final UUID installation;
    private final UUID viewer;
    private ItemStack configuration = ItemStack.EMPTY;
    private boolean extracted;

    public StorageCellWorkbench(ECOStorageSystemBlockEntity controller, BlockPos drivePosition,
        UUID installation, Player player) {
        super(AEBlockEntities.CELL_WORKBENCH.get(), controller.getBlockPos(),
            AEBlocks.CELL_WORKBENCH.block().defaultBlockState());
        this.controller = controller;
        this.drivePosition = drivePosition.immutable();
        this.installation = installation;
        this.viewer = player.getUUID();
        setLevel(player.level());
        if (!player.level().isClientSide) {
            var drive = drive();
            if (drive != null && drive.getCellStack() != null) {
                var cell = drive.getCellInventory();
                if (cell != null) cell.persist();
                getSubInventory(ISegmentedInventory.CELLS).setItemDirect(0, drive.getCellStack());
                configuration = stack().copy();
            }
        }
    }

    public ECOStorageSystemBlockEntity controller() { return controller; }

    @Nullable
    public cn.dancingsnow.neoecoae.impl.storage.bulk.ECOBulkStorageCell bulkCell() {
        var drive = drive();
        return drive != null && drive.getCellInventory() instanceof
            cn.dancingsnow.neoecoae.impl.storage.bulk.ECOBulkStorageCell bulk ? bulk : null;
    }

    private ItemStack stack() {
        return getSubInventory(ISegmentedInventory.CELLS).getStackInSlot(0);
    }

    @Nullable
    private ECODriveBlockEntity drive() {
        return controller.getStorageDrivesForIntegration().stream()
            .filter(drive -> !drive.isRemoved() && drive.getBlockPos().equals(drivePosition)
                && installation.equals(drive.getCellInteractionId()))
            .findFirst().orElse(null);
    }

    public boolean isValidFor(Player player) {
        if (extracted || !viewer.equals(player.getUUID()) || controller.isRemoved()
            || player.level() != controller.getLevel()
            || !controller.canPlayerInteract(player)
            || player.level().getBlockEntity(controller.getBlockPos()) != controller) return false;
        if (player.level().isClientSide) return true;
        var drive = drive();
        return controller.isFormed() && drive != null && drive.canExtractCell()
            && drive.getCellStack() == stack() && stack().getCount() == 1
            && ECOStorageCellSettings.canConfigure(stack());
    }

    /** Rebuild the mounted handler only after a real filter, fuzzy-mode or upgrade change. */
    public void flushConfiguration(Player player) {
        if (player.level().isClientSide || !isValidFor(player)) return;
        ItemStack cell = stack();
        if (Objects.equals(configuration.get(AEComponents.STORAGE_CELL_CONFIG_INV), cell.get(AEComponents.STORAGE_CELL_CONFIG_INV))
            && Objects.equals(configuration.get(AEComponents.STORAGE_CELL_FUZZY_MODE), cell.get(AEComponents.STORAGE_CELL_FUZZY_MODE))
            && Objects.equals(configuration.get(AEComponents.UPGRADES), cell.get(AEComponents.UPGRADES))) return;
        drive().reloadCellConfiguration();
        configuration = cell.copy();
    }

    public void extractToCursor(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !isValidFor(player)
            || !player.containerMenu.getCarried().isEmpty()) return;
        flushConfiguration(player);
        var drive = drive();
        ItemStack cell = drive.getCellStack();
        drive.setCellStack(null);
        if (drive.getCellStack() != null) return;
        extracted = true;
        // Opening another container disposes of its predecessor's cursor, so assign the cell afterwards.
        BlockUIMenuType.openUI(serverPlayer, controller.getBlockPos());
        player.containerMenu.setCarried(cell);
        player.containerMenu.broadcastFullState();
    }

    @Override
    public void saveChanges() {
        // The real drive is persisted by flushConfiguration; this view is never placed in the world.
    }

    /** A live display slot. Only extractToCursor may remove the physical cell on the server. */
    public static final class CellSlot extends AppEngSlot {
        public CellSlot(InternalInventory inventory) { super(inventory, 0); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return player.level().isClientSide; }
        @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }
    }
}
