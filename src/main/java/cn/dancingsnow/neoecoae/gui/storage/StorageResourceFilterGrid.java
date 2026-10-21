package cn.dancingsnow.neoecoae.gui.storage;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.impl.storage.StorageInterfaceFilter;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/** Filter samples never transfer real items; only validated keys travel through the menu RPC. */
final class StorageResourceFilterGrid extends UIElement {
    private final ECOMachineInterfaceBlockEntity<?> host;
    private final Player player;
    private final RPCEmitter edit;
    private final List<FilterSlot> slots = new ArrayList<>();

    StorageResourceFilterGrid(ECOMachineInterfaceBlockEntity<?> host, Player player) {
        this.host = host;
        this.player = player;
        edit = addRPCEvent(RPCEventBuilder.simple(Integer.class, Tag.class, (slot, value) -> applyEdit(slot, value)));
        for (int slot = 0; slot < StorageInterfaceFilter.SLOTS; slot++) {
            FilterSlot element = new FilterSlot(slot);
            slots.add(element);
            addChild(element);
            if (host.getLevel() != null && host.getLevel().isClientSide) element.xeiPhantom();
        }
        BindableValue<Tag> filters = new BindableValue<>(host.getStorageResourceFilters());
        filters.setDisplay(false);
        filters.bind(DataBindingBuilder.tagS2C(host::getStorageResourceFilters).build());
        filters.registerValueListener(this::receive);
        addChild(filters);
        receive(host.getStorageResourceFilters());
    }

    private void receive(Tag value) {
        if (!(value instanceof CompoundTag tag)) return;
        for (int slot = 0; slot < slots.size(); slot++) {
            FilterSlot element = slots.get(slot);
            element.key = StorageInterfaceFilter.get(tag, slot);
            element.unavailable = tag.contains(Integer.toString(slot)) && element.key == null;
            ItemStack display = element.key instanceof AEItemKey item ? item.toStack(1)
                : element.key == null ? ItemStack.EMPTY : GenericStack.wrapInItemStack(element.key, 1);
            element.setValue(display, false);
        }
    }

    void applyEdit(int slot, Tag value) {
        if (slot < 0 || slot >= slots.size() || !(value instanceof CompoundTag tag)
            || !StorageInterfaceUI.canEdit(this, host, player)) return;
        AEKey key = tag.isEmpty() ? null : StorageInterfaceFilter.decode(tag);
        if (!tag.isEmpty() && key == null) return;
        host.setStorageResourceFilter(slot, key);
    }

    static @Nullable AEKey sample(ItemStack stack, boolean asItem) {
        if (stack == null || stack.isEmpty()) return null;
        GenericStack generic = GenericStack.unwrapItemStack(stack);
        if (generic != null) return generic.what();
        if (!asItem) {
            var fluid = FluidUtil.getFluidContained(stack).orElse(null);
            if (fluid != null && !fluid.isEmpty()) return AEFluidKey.of(fluid);
        }
        return AEItemKey.of(stack);
    }

    private final class FilterSlot extends ItemSlot {
        private final int index;
        private @Nullable AEKey key;
        private boolean unavailable;

        FilterSlot(int index) {
            super(new LocalSlot() {
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public int getMaxStackSize() { return 0; }
                @Override public int getMaxStackSize(ItemStack stack) { return 0; }
                @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }
            });
            this.index = index;
            layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(index % 9 * 18).top(index / 9 * 18).width(18).height(18).paddingAll(1));
            style(style -> style.backgroundTexture(NETextures.ITEM_SLOT));
            slotStyle(style -> style.hoverOverlay(NETextures.AE2_SLOT_HIGHLIGHT));
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            event.hasHandler = true;
            event.stopImmediatePropagation();
            if (event.button == 1) request(null);
            else if (event.button == 0) {
                var ui = getModularUI();
                ItemStack carried = ui == null || ui.getMenu() == null ? ItemStack.EMPTY : ui.getMenu().getCarried();
                if (!carried.isEmpty()) request(sample(carried, UIElement.isShiftDown()));
            }
        }

        private void request(@Nullable AEKey key) {
            edit.send(index, key == null ? new CompoundTag() : StorageInterfaceFilter.encode(key));
        }

        @Override
        public ItemSlot setValue(@Nullable ItemStack stack, boolean notify) {
            if (notify) request(sample(stack, false));
            else super.setValue(stack, false);
            return this;
        }

        @Override
        protected void drawSlotOverlay(GUIContext context) {
            super.drawSlotOverlay(context);
            if (key == null) context.drawTexture(StoragePanelTextures.EMPTY_FILTER, 0, 0, 16, 16);
        }

        @Override
        protected void drawItemStack(GUIContext context, ItemStack stack) {
            if (key instanceof AEItemKey) super.drawItemStack(context, stack);
            else if (key != null) AEKeyRendering.drawInGui(Minecraft.getInstance(), context.graphics, 0, 0, key);
        }

        @Override
        public List<Component> getFullTooltipTexts(boolean withItemTooltips) {
            List<Component> lines = key == null ? new ArrayList<>() : AEKeyRendering.getTooltip(key);
            if (unavailable) lines.add(Component.translatable("gui.neoecoae.storage_interface.filter.unavailable"));
            lines.add(Component.translatable("gui.neoecoae.storage_interface.filter.edit"));
            lines.add(Component.translatable("gui.neoecoae.storage_interface.filter.rule"));
            return lines;
        }

        @Override
        protected void onHoverTooltips(UIEvent event) {
            event.hoverTooltips = new HoverTooltips(getFullTooltipTexts(), null, null, null);
        }
    }
}
