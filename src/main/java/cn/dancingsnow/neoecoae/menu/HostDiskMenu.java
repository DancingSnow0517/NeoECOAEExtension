package cn.dancingsnow.neoecoae.menu;

import cn.dancingsnow.neoecoae.api.storage.ICellHost;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType.BlockUI;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType.BlockUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import org.jetbrains.annotations.Nullable;

public final class HostDiskMenu extends ModularUIContainerMenu {
    public static final MenuType<ModularUIContainerMenu> TYPE = IMenuTypeExtension.create(HostDiskMenu::read);
    private static final int MAX_OTHER_SLOTS = 36 + 1;
    private static final int MAX_DRIVES = Short.MAX_VALUE + 1 - MAX_OTHER_SLOTS;
    private final Holder holder;

    private HostDiskMenu(int id, Inventory inventory, Holder holder) {
        super(TYPE, id, inventory, holder);
        this.holder = holder;
    }

    private static HostDiskMenu read(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        BlockState state = BlockUIMenuType.BLOCK_STATE_STREAM_CODEC.decode(data);
        int count = data.readVarInt();
        if (!(state.getBlock() instanceof BlockUI block) || count < 0 || count > MAX_DRIVES) {
            throw new IllegalArgumentException("Invalid ECO disk menu layout");
        }
        var holder = new Holder(block, inventory.player, pos, state, null, false);
        for (int index = 0; index < count; index++) {
            int displayIndex = data.readVarInt();
            holder.disks.add(new DiskSlot(holder, data.readBlockPos(), displayIndex, null));
        }
        return new HostDiskMenu(id, inventory, holder);
    }

    public static <Cluster extends NECluster<Cluster>, Drive extends NEBlockEntity<Cluster, Drive> & ICellHost>
        Holder holder(BlockUI block, Player player,
        BlockPos pos, BlockState state, NEBlockEntity<?, ?> host, List<Drive> drives, boolean mergeIntoInventory) {
        var holder = new Holder(block, player, pos, state, host, mergeIntoInventory);
        for (int index = 0; index < drives.size(); index++) {
            Drive drive = drives.get(index);
            if (drive == null) continue;
            if (holder.disks.size() == MAX_DRIVES) {
                holder.overflow = true;
                break;
            }
            holder.disks.add(new DiskSlot(holder, drive.getBlockPos(), index, drive));
        }
        return holder;
    }

    public static List<DiskSlot> disks(BlockUIHolder holder) {
        return holder instanceof Holder diskHolder ? List.copyOf(diskHolder.disks) : List.of();
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (!stillValid(player)) return;
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof DiskSlot disk) {
            if (player.level().isClientSide()) return;
            if (clickType == ContainerInput.QUICK_MOVE) {
                quickMoveStack(player, slotId);
            } else if (clickType == ContainerInput.PICKUP && (button == 0 || button == 1)) {
                ItemStack carried = getCarried();
                if (carried.isEmpty()) {
                    setCarried(disk.take());
                } else if (disk.install(carried)) {
                    setCarried(carried.copyWithCount(carried.getCount() - 1));
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotId) {
        if (player.level().isClientSide() || !stillValid(player) || slotId < 0 || slotId >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(slotId);
        if (source instanceof DiskSlot disk) {
            if (!getCarried().isEmpty() || !disk.canTake(player)) return ItemStack.EMPTY;
            int destination = holder.mergeIntoInventory ? inventory.getSlotWithRemainingSpace(disk.getItem()) : -1;
            if (destination < 0 || destination >= Inventory.INVENTORY_SIZE) destination = inventory.getFreeSlot();
            if (destination < 0) return ItemStack.EMPTY;
            ItemStack cell = disk.remove();
            if (!cell.isEmpty()) {
                ItemStack existing = inventory.getItem(destination);
                inventory.setItem(destination, existing.isEmpty() ? cell : existing.copyWithCount(existing.getCount() + 1));
                inventory.setChanged();
            }
            return ItemStack.EMPTY;
        }
        if (source.container == inventory && source.mayPickup(player)) {
            ItemStack remaining = source.getItem();
            if (getCarried().isEmpty() && !remaining.isEmpty()) {
                boolean moved = false;
                for (DiskSlot disk : holder.disks) {
                    if (!disk.install(remaining)) continue;
                    moved = true;
                    remaining = remaining.copyWithCount(remaining.getCount() - 1);
                    source.setByPlayer(remaining);
                    if (remaining.isEmpty()) break;
                }
                if (moved) return ItemStack.EMPTY;
            }
        }
        return super.quickMoveStack(player, slotId);
    }

    @Override
    protected boolean isValidQuickMoveDestination(Slot candidate, ItemStack stack, boolean fromPlayerSide) {
        return !(candidate instanceof DiskSlot) && super.isValidQuickMoveDestination(candidate, stack, fromPlayerSide);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof DiskSlot) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof DiskSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    public static final class Holder extends BlockUIHolder {
        private final List<DiskSlot> disks = new ArrayList<>();
        @Nullable private final NEBlockEntity<?, ?> host;
        @Nullable private final NECluster<?> cluster;
        private final boolean mergeIntoInventory;
        private boolean overflow;

        private Holder(BlockUI block, Player player, BlockPos pos, BlockState state,
            @Nullable NEBlockEntity<?, ?> host, boolean mergeIntoInventory) {
            super(block, player, pos, state);
            this.host = host;
            this.cluster = host == null ? null : host.getCluster();
            this.mergeIntoInventory = mergeIntoInventory;
        }

        @Override
        public boolean isStillValid(Player player) {
            if (player.level().isClientSide()) return true;
            return this.player == player && host != null && !host.isRemoved()
                && player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) == host
                && host.getCluster() == cluster && (cluster == null || !cluster.isDestroyed() && host.isFormed())
                && super.isStillValid(player);
        }

        @Override
        @Nullable
        public ModularUIContainerMenu createMenu(int id, Inventory inventory, Player player) {
            if (overflow) {
                player.sendOverlayMessage(Component.translatableWithFallback("gui.neoecoae.host.disk_menu_limit",
                    "This host exceeds the disk menu limit of %s drives.", MAX_DRIVES));
                return null;
            }
            return isStillValid(player) ? new HostDiskMenu(id, inventory, this) : null;
        }

        @Override
        public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf data) {
            super.writeClientSideData(menu, data);
            data.writeVarInt(disks.size());
            for (DiskSlot disk : disks) {
                data.writeVarInt(disk.displayIndex);
                data.writeBlockPos(disk.position);
            }
        }
    }

    public static final class DiskSlot extends Slot {
        private final Holder holder;
        private final BlockPos position;
        private final int displayIndex;
        @Nullable private final NEBlockEntity<?, ?> drive;
        private BooleanSupplier clientActive = () -> true;

        private DiskSlot(Holder holder, BlockPos position, int displayIndex, @Nullable NEBlockEntity<?, ?> drive) {
            super(new SimpleContainer(1), 0, 0, 0);
            this.holder = holder;
            this.position = position.immutable();
            this.displayIndex = displayIndex;
            this.drive = drive;
        }

        public BlockPos position() { return position; }

        public int displayIndex() { return displayIndex; }

        public void setClientActive(BooleanSupplier clientActive) {
            this.clientActive = clientActive;
        }

        @Override
        public boolean isActive() {
            return !holder.player.level().isClientSide() || clientActive.getAsBoolean();
        }

        @Nullable
        private ICellHost owner() {
            var level = holder.player.level();
            if (drive == null || drive.isRemoved() || holder.cluster == null || holder.cluster.isDestroyed()
                || drive.getCluster() != holder.cluster
                || !level.hasChunkAt(position) || level.getBlockEntity(position) != drive) return null;
            return (ICellHost) drive;
        }

        @Override
        public ItemStack getItem() {
            if (holder.player.level().isClientSide()) return super.getItem();
            ICellHost owner = owner();
            ItemStack cell = owner == null ? null : owner.getCellStack();
            return cell == null ? ItemStack.EMPTY : cell;
        }

        @Override
        public int getMaxStackSize() { return 1; }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }

        @Override
        public boolean mayPickup(Player player) { return false; }

        private boolean install(ItemStack stack) {
            ICellHost owner = owner();
            if (owner == null || stack.isEmpty() || !owner.canExtractCell()) return false;
            ItemStack installed = owner.getCellStack();
            if (installed != null && !installed.isEmpty()) return false;
            return replace(owner, installed, stack.copyWithCount(1));
        }

        private boolean canTake(Player player) {
            ICellHost owner = owner();
            if (owner == null || getItem().getCount() != 1) return false;
            if (owner.canExtractCell()) return true;
            var reason = owner.getCellExtractionBlockReasonText();
            if (reason != null) player.sendOverlayMessage(reason);
            return false;
        }

        private ItemStack take() {
            return canTake(holder.player) ? remove() : ItemStack.EMPTY;
        }

        private ItemStack remove() {
            ICellHost owner = owner();
            if (owner == null) return ItemStack.EMPTY;
            ItemStack cell = owner.getCellStack();
            if (cell == null || cell.getCount() != 1) return ItemStack.EMPTY;
            return replace(owner, cell, null) ? cell : ItemStack.EMPTY;
        }

        private static boolean replace(ICellHost owner, @Nullable ItemStack expected, @Nullable ItemStack replacement) {
            if (owner instanceof ECODriveBlockEntity storageDrive) {
                return storageDrive.replaceCellFromHostMenu(expected, replacement);
            }
            owner.setCellStack(replacement);
            return owner.getCellStack() == replacement;
        }
    }
}
