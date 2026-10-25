package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.api.crafting.PatternDetailsHelper;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternPreviewNativeSlotsTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class); var registrate = mockStatic(NERegistrate.class)) {
            var modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.gui.theme.NETextures");
        }
    }

    @Test void vanillaPickupAndReplacementMoveRealStacksWithoutAnItemRpc() {
        var f = new Fixture();
        f.contents[0] = new ItemStack(Items.WRITABLE_BOOK);
        f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        assertTrue(f.contents[0].isEmpty());
        assertTrue(f.menu.getCarried().is(Items.WRITABLE_BOOK));
        f.contents[0] = new ItemStack(Items.PAPER);
        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(any(ItemStack.class)))
                .thenAnswer(call -> !((ItemStack) call.getArgument(0)).isEmpty());
            f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
        }
        assertTrue(f.contents[0].is(Items.WRITABLE_BOOK));
        assertTrue(f.menu.getCarried().is(Items.PAPER));
        verify(f.host, never()).rpcToServer(anyString(), any());
    }

    @Test void nativeQuickMoveReturnsPatternsToTheBackpackAndLeavesSourcesInPlace() {
        var f = new Fixture();
        f.contents[0] = new ItemStack(Items.WRITABLE_BOOK);
        f.contents[1] = new ItemStack(Items.PAPER);
        f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
        assertTrue(f.contents[0].isEmpty());
        assertTrue(f.inventory.contains(new ItemStack(Items.WRITABLE_BOOK)));
        assertTrue(f.contents[1].is(Items.PAPER));
        f.menu.clicked(1, 0, ClickType.QUICK_MOVE, f.player);
        assertTrue(f.contents[1].isEmpty());
        assertTrue(f.inventory.contains(new ItemStack(Items.PAPER)));
        verify(f.host, atLeastOnce()).nativePatternPreviewChanged(f.player, 10, 0);
        verify(f.host, atLeastOnce()).nativePatternPreviewChanged(f.player, 10, 1);
    }

    @Test void registeredNativeWrapperStaysActiveWhileLdlibOwnsTheHoveredSlot() throws Exception {
        var f = new Fixture();
        f.contents[0] = new ItemStack(Items.WRITABLE_BOOK);
        var hovered = SlotWidget.class.getDeclaredField("HOVER_SLOT");
        hovered.setAccessible(true);
        try {
            hovered.set(null, f.menu.slots.get(0));
            assertTrue(f.menu.slots.get(0).isActive());
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            assertTrue(f.contents[0].isEmpty());
            assertTrue(f.menu.getCarried().is(Items.WRITABLE_BOOK));
        } finally { hovered.set(null, null); }
    }

    @Test void queryingNativeSlotCapacityNeverTemporarilyClearsTheRealBus() {
        var f = new Fixture();
        f.contents[0] = new ItemStack(Items.WRITABLE_BOOK);
        assertEquals(1, f.menu.slots.get(0).getMaxStackSize(new ItemStack(Items.WRITABLE_BOOK)));
        assertTrue(f.contents[0].is(Items.WRITABLE_BOOK));
        verify(f.host, never()).setNativePatternPreviewStack(any(), anyLong(), anyInt(), any());
    }

    @Test void nativeLeftInsertionUsesOffscreenCapacityAndNeverReplacesAnOccupiedTarget() {
        var f = new Fixture();
        f.contents[0] = new ItemStack(Items.PAPER);
        f.menu.setCarried(new ItemStack(Items.WRITABLE_BOOK));
        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(any(ItemStack.class)))
                .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.WRITABLE_BOOK));
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        }
        assertTrue(f.contents[0].is(Items.PAPER));
        assertTrue(f.contents[1].is(Items.WRITABLE_BOOK));
        assertTrue(f.menu.getCarried().isEmpty());
    }

    @Test void nativeBackpackQuickMoveUsesTheSameGlobalInsertionContract() {
        var f = new Fixture();
        f.inventory.setItem(9, new ItemStack(Items.WRITABLE_BOOK));
        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(any(ItemStack.class)))
                .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.WRITABLE_BOOK));
            f.menu.clicked(CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS, 0, ClickType.QUICK_MOVE, f.player);
        }
        assertTrue(f.inventory.getItem(9).isEmpty());
        assertTrue(f.contents[0].is(Items.WRITABLE_BOOK));
    }

    @Test void remappingANativeSlotChangesItsPhysicalTargetAndDiskRecipesStayReadOnly() {
        var f = new Fixture();
        f.contents[0] = new ItemStack(Items.WRITABLE_BOOK);
        f.contents[1] = new ItemStack(Items.PAPER);
        f.view(1, -1);
        f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        assertTrue(f.contents[0].is(Items.WRITABLE_BOOK));
        assertTrue(f.contents[1].isEmpty());
        assertTrue(f.menu.getCarried().is(Items.PAPER));
        f.menu.setCarried(ItemStack.EMPTY);
        f.contents[1] = new ItemStack(Items.PAPER);
        f.view(1, 0);
        f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
        f.menu.clicked(0, 2, ClickType.SWAP, f.player);
        assertTrue(f.contents[1].is(Items.PAPER));
        assertTrue(f.menu.getCarried().isEmpty());
    }

    @Test void aStaleViewportAcknowledgementCannotEnableTheLatestViewport() {
        var f = new Fixture();
        var level = mock(Level.class);
        cn.dancingsnow.neoecoae.util.UiTestSupport.set(level, "isClientSide", true);
        when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(f.player.level()).thenReturn(level);
        when(f.host.getLevel()).thenReturn(level);
        when(f.host.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(f.host.getPatternPreviewSync()).thenReturn(new PatternPreviewSync(f.host));
        var state = new CraftingPatternPreviewState(f.host, f.player);
        CraftingInterfaceUI.patternPreview(state);
        var first = new FriendlyByteBuf(Unpooled.buffer());
        try {
            state.nativeSlots().writeView(first);
            long old = first.readLong();
            var next = new PatternPreviewNativeSlots.Target[CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS];
            next[0] = new PatternPreviewNativeSlots.Target(10, 1, -1, true);
            state.nativeSlots().view(next);
            state.nativeSlots().acknowledge(old);
            assertFalse(state.nativeSlots().ready());
        } finally { first.release(); }
    }

    private static final class Fixture {
        final ECOMachineInterfaceBlockEntity<?> host = mock(ECOMachineInterfaceBlockEntity.class);
        final Player player = mock(Player.class);
        final Inventory inventory = new Inventory(player);
        final ItemStack[] contents = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        final CraftingPatternPreviewState state;
        final NativeMenu menu;

        Fixture() {
            var level = mock(Level.class);
            when(level.enabledFeatures()).thenReturn(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
            when(player.level()).thenReturn(level);
            when(player.getAbilities()).thenReturn(new net.minecraft.world.entity.player.Abilities());
            when(player.getInventory()).thenReturn(inventory);
            when(host.getLevel()).thenReturn(level);
            when(host.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(host.getPatternPreviewSync()).thenReturn(new PatternPreviewSync(host));
            state = new CraftingPatternPreviewState(host, player);
            var preview = CraftingInterfaceUI.patternPreview(state);
            List<SlotWidget> widgets = new ArrayList<>();
            for (var child : preview.getChildren()) widgets.add((SlotWidget) child.widgets.get(0));
            for (int i = 0; i < 36; i++) {
                int physical = i < 27 ? i + 9 : i - 27;
                var element = new cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot(new Slot(inventory, physical, 0, 0));
                state.bindPlayerNativeClick(element);
                widgets.add((SlotWidget) element.widgets.get(0));
            }
            menu = new NativeMenu(widgets);
            player.containerMenu = menu;
            when(host.getNativePatternPreviewStack(eq(player), eq(10L), anyInt(), anyInt()))
                .thenAnswer(call -> contents[(int) call.getArgument(2)]);
            when(host.canInsertNativePatternPreview(eq(player), eq(10L), anyInt(), any()))
                .thenReturn(true);
            doAnswer(call -> { contents[(int) call.getArgument(2)] = ((ItemStack) call.getArgument(3)).copy(); return null; })
                .when(host).setNativePatternPreviewStack(eq(player), eq(10L), anyInt(), any());
            when(host.extractNativePatternPreview(eq(player), eq(10L), anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(call -> {
                    var stack = contents[(int) call.getArgument(2)];
                    int count = Math.min((int) call.getArgument(3), stack.getCount());
                    var result = stack.copyWithCount(count);
                    if (!(boolean) call.getArgument(4)) stack.shrink(count);
                    return result;
                });
            when(host.insertNativePatternPreview(eq(player), any())).thenAnswer(call -> {
                ItemStack stack = call.getArgument(1);
                for (int i = 0; i < contents.length; i++) if (contents[i].isEmpty()) {
                    contents[i] = stack.copyWithCount(1);
                    return stack.copyWithCount(stack.getCount() - 1);
                }
                return stack;
            });
            view(0, -1);
        }

        void view(int firstSlot, int recipe) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeLong(1);
                for (int i = 0; i < CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS; i++) {
                    buffer.writeBoolean(true);
                    buffer.writeLong(10);
                    buffer.writeVarInt(i == 0 ? firstSlot : i < 3 ? i : -1);
                    buffer.writeInt(i == 0 ? recipe : -1);
                    buffer.writeBoolean(i < 54);
                }
                state.nativeSlots().readView(buffer);
            } finally { buffer.release(); }
        }
    }

    /** Calls the vanilla click implementation after LDLib's normal SlotWidget hook. */
    private static final class NativeMenu extends AbstractContainerMenu {
        final List<SlotWidget> widgets;
        NativeMenu(List<SlotWidget> widgets) {
            super(null, 1);
            this.widgets = widgets;
            for (var widget : widgets) addSlot(widget.getHandler());
        }
        @Override public void clicked(int index, int button, ClickType type, Player player) {
            if (index >= 0 && widgets.get(index).slotClick(button, type, player) != null) return;
            super.clicked(index, button, type, player);
        }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) {
            Slot source = slots.get(index);
            ItemStack stack = source.getItem();
            ItemStack before = stack.copy();
            int boundary = CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS;
            if (!moveItemStackTo(stack, index < boundary ? boundary : 0,
                    index < boundary ? slots.size() : boundary, true)) return ItemStack.EMPTY;
            if (stack.isEmpty()) source.set(ItemStack.EMPTY);
            source.setChanged();
            return before;
        }
    }
}
