package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.gui.GuiTitleProvider;
import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import org.jetbrains.annotations.Nullable;

public final class CraftingPatternMenu extends ModularUIContainerMenu {
    public static final int MAX_WINDOW_SLOTS = CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS;
    public static final MenuType<ModularUIContainerMenu> TYPE = IMenuTypeExtension.create(CraftingPatternMenu::decode);

    public record Address(long busPosition, int physicalSlot) {}

    private CraftingPatternMenu(int id, Inventory inventory, Holder holder) {
        super(TYPE, id, inventory, holder);
    }

    public static void openBus(ServerPlayer player, ECOCraftingPatternBusBlockEntity bus, int page) {
        int pages = bus.getPageCount();
        player.openMenu(new Holder(player, bus, Math.clamp(page, 0, pages - 1), pages, List.of(),
                player.containerMenu instanceof CraftingPatternMenu menu && menu.isFor(bus)));
    }

    public static void openInterface(ServerPlayer player, ECOMachineInterfaceBlockEntity<?> host) {
        player.openMenu(new Holder(player, host, 0, 0, List.of(), false));
    }

    public boolean isFor(BlockEntity host) {
        return ((Holder) uiHolder).entity == host;
    }

    public boolean isSameHost(CraftingPatternMenu other) {
        return other.isFor(((Holder) uiHolder).entity);
    }

    public void openWindow(ServerPlayer player, ECOMachineInterfaceBlockEntity<?> host, List<Address> requested) {
        Holder holder = (Holder) uiHolder;
        if (!isFor(host) || !stillValid(player) || !getCarried().isEmpty() || requested.size() > MAX_WINDOW_SLOTS) return;
        var window = requested.stream().distinct().filter(address -> host.resolvePatternMenuBus(address) != null).toList();
        if (!holder.slots.keySet().containsAll(window)) {
            player.openMenu(new Holder(player, host, 0, 0, window, true));
        }
    }

    public void insertCarriedPattern(ServerPlayer player, ECOMachineInterfaceBlockEntity<?> host, boolean single) {
        if (!isFor(host) || !stillValid(player) || getCarried().isEmpty()) return;
        ItemStack carried = getCarried();
        int count = single ? 1 : carried.getCount();
        ItemStack remaining = host.insertPatternFromMenu(carried.copyWithCount(count));
        if (remaining.getCount() != count) {
            setCarried(carried.copyWithCount(carried.getCount() - (count - remaining.getCount())));
            broadcastChanges();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player instanceof ServerPlayer) || !stillValid(player) || !getCarried().isEmpty()
                || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        Holder holder = (Holder) uiHolder;
        ItemStack remaining = source.getItem().copy();
        if (source.container == inventory) {
            if (holder.entity instanceof ECOMachineInterfaceBlockEntity<?> host) {
                remaining = host.insertPatternFromMenu(remaining);
            } else {
                for (PatternSlot destination : holder.slots.values()) {
                    remaining = destination.safeInsert(remaining);
                    if (remaining.isEmpty()) break;
                }
            }
        } else if (source instanceof PatternSlot) {
            for (Slot destination : slots) {
                if (destination.container == inventory && destination.hasItem()) remaining = destination.safeInsert(remaining);
                if (remaining.isEmpty()) break;
            }
            for (Slot destination : slots) {
                if (destination.container == inventory && !destination.hasItem()) remaining = destination.safeInsert(remaining);
                if (remaining.isEmpty()) break;
            }
        }
        if (!ItemStack.matches(source.getItem(), remaining)) source.setByPlayer(remaining);
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(((Holder) uiHolder).entity instanceof ECOMachineInterfaceBlockEntity<?> && slot instanceof PatternSlot)
                && super.canTakeItemForPickAll(stack, slot);
    }

    private static CraftingPatternMenu decode(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos position = buffer.readBlockPos();
        BlockEntity entity = inventory.player.level().getBlockEntity(position);
        if (entity instanceof ECOCraftingPatternBusBlockEntity) {
            return new CraftingPatternMenu(id, inventory,
                    new Holder(inventory.player, entity, buffer.readVarInt(), buffer.readVarInt(), List.of(), false));
        }
        if (entity instanceof ECOMachineInterfaceBlockEntity<?>) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_WINDOW_SLOTS) throw new IllegalArgumentException("Invalid pattern window size");
            List<Address> window = new ArrayList<>(count);
            for (int slot = 0; slot < count; slot++) window.add(new Address(buffer.readLong(), buffer.readVarInt()));
            return new CraftingPatternMenu(id, inventory, new Holder(inventory.player, entity, 0, 0, window, false));
        }
        throw new IllegalStateException("No crafting pattern menu host at " + position);
    }

    private static final class Holder implements IContainerUIHolder, MenuProvider {
        private final Player player;
        private final BlockPos pos;
        private final BlockEntity entity;
        private final int page;
        private final int pageCount;
        private final boolean replacingMenu;
        private final Map<Address, PatternSlot> slots = new LinkedHashMap<>();

        Holder(Player player, BlockEntity entity, int page, int pageCount, List<Address> window, boolean replacingMenu) {
            this.player = player;
            this.pos = entity.getBlockPos();
            this.entity = entity;
            this.page = page;
            this.pageCount = pageCount;
            this.replacingMenu = replacingMenu;
            if (entity instanceof ECOCraftingPatternBusBlockEntity bus) {
                int start = page * ECOCraftingPatternBusBlockEntity.SLOTS_PER_PAGE;
                for (int slot = 0; slot < ECOCraftingPatternBusBlockEntity.SLOTS_PER_PAGE; slot++) {
                    Address address = new Address(pos.asLong(), start + slot);
                    slots.put(address, new PatternSlot(player, null, bus, address.physicalSlot()));
                }
            } else if (entity instanceof ECOMachineInterfaceBlockEntity<?> host) {
                for (Address address : window) {
                    var bus = player.level().isClientSide() ? null : host.resolvePatternMenuBus(address);
                    slots.put(address, new PatternSlot(player, host, bus, address.physicalSlot()));
                }
            }
        }

        @Override
        public ModularUI createUI(Player player) {
            if (entity instanceof ECOCraftingPatternBusBlockEntity bus) {
                return CraftingPatternBusUI.create(new CraftingPatternBusUI.Config(
                        () -> GuiTitleProvider.title(bus.getBlockState(), getDisplayName()),
                        List.copyOf(slots.values()), pageCount, page, this::changePage), player);
            }
            return CraftingInterfaceUI.create((ECOMachineInterfaceBlockEntity<?>) entity, player, slots);
        }

        private void changePage(int target) {
            if (player instanceof ServerPlayer serverPlayer && entity instanceof ECOCraftingPatternBusBlockEntity bus
                    && player.containerMenu instanceof CraftingPatternMenu menu && menu.uiHolder == this
                    && menu.stillValid(player) && menu.getCarried().isEmpty()) {
                int next = Math.clamp(target, 0, bus.getPageCount() - 1);
                if (next != page) openBus(serverPlayer, bus, next);
            }
        }

        @Override
        public boolean isStillValid(Player player) {
            return player.level() == entity.getLevel() && !entity.isRemoved() && player.level().hasChunkAt(pos)
                    && player.level().getBlockEntity(pos) == entity && player.distanceToSqr(pos.getCenter()) <= 64
                    && (entity instanceof ECOCraftingPatternBusBlockEntity bus ? page < bus.getPageCount()
                        : ((ECOMachineInterfaceBlockEntity<?>) entity).supportsCraftingInterfaceUi());
        }

        @Override
        public Component getDisplayName() {
            return entity.getBlockState().getBlock().getName();
        }

        @Override
        public ModularUIContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new CraftingPatternMenu(id, inventory, this);
        }

        @Override
        public boolean shouldTriggerClientSideContainerClosingOnOpen() {
            return !replacingMenu;
        }

        @Override
        public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            if (entity instanceof ECOCraftingPatternBusBlockEntity) {
                buffer.writeVarInt(page);
                buffer.writeVarInt(pageCount);
            } else {
                buffer.writeCollection(slots.keySet(), (buf, address) -> {
                    buf.writeLong(address.busPosition());
                    buf.writeVarInt(address.physicalSlot());
                });
            }
        }
    }

    private static final class PatternSlot extends Slot {
        private final Player player;
        private final @Nullable ECOMachineInterfaceBlockEntity<?> host;
        private final @Nullable ECOCraftingPatternBusBlockEntity bus;
        private final int physicalSlot;

        PatternSlot(Player player, @Nullable ECOMachineInterfaceBlockEntity<?> host,
                @Nullable ECOCraftingPatternBusBlockEntity bus, int physicalSlot) {
            super(new SimpleContainer(1), 0, 0, 0);
            this.player = player;
            this.host = host;
            this.bus = bus;
            this.physicalSlot = physicalSlot;
        }

        private boolean clientSide() {
            return player.level().isClientSide();
        }

        private boolean hasPhysicalSlot() {
            return bus != null && !bus.isRemoved() && physicalSlot >= 0 && physicalSlot < bus.getPatternSlotInventory().size();
        }

        private boolean mayAccess() {
            return hasPhysicalSlot() && (host == null || host.mayUsePatternMenuSlot(bus, physicalSlot));
        }

        @Override
        public ItemStack getItem() {
            if (clientSide()) return super.getItem();
            if (!hasPhysicalSlot()) return ItemStack.EMPTY;
            ItemStack stack = bus.getPatternSlotInventory().getStackInSlot(physicalSlot);
            return host != null && bus.ownsAuxiliary(stack) ? ItemStack.EMPTY : stack;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (clientSide()) return true;
            return mayAccess() && bus.getPatternSlotInventory().isItemValid(physicalSlot, stack)
                    && (host == null || host.mayPlacePatternMenuStack(bus, physicalSlot, stack));
        }

        @Override
        public boolean mayPickup(Player player) {
            if (clientSide()) return true;
            return mayAccess() && !getItem().isEmpty()
                    && !bus.getPatternSlotInventory().extractItem(physicalSlot, 1, true).isEmpty();
        }

        @Override
        public int getMaxStackSize() {
            return clientSide() ? 1 : hasPhysicalSlot() ? bus.getPatternSlotInventory().getSlotLimit(physicalSlot) : 0;
        }

        @Override
        public void set(ItemStack stack) {
            if (clientSide()) super.set(stack);
            else if (mayAccess()) bus.setPatternDirect(physicalSlot, stack);
        }

        @Override
        public ItemStack remove(int amount) {
            if (clientSide()) return super.remove(amount);
            return mayPickup(player) ? bus.getPatternSlotInventory().extractItem(physicalSlot, amount, false) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack safeInsert(ItemStack stack, int increment) {
            if (clientSide()) return super.safeInsert(stack, increment);
            if (increment <= 0 || !mayPlace(stack)) return stack;
            int offered = Math.min(stack.getCount(), increment);
            ItemStack remaining = bus.getPatternSlotInventory().insertItem(physicalSlot, stack.copyWithCount(offered), false);
            stack.shrink(offered - remaining.getCount());
            return stack;
        }
    }
}
