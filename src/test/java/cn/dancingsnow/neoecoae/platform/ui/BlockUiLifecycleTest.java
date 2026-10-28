package cn.dancingsnow.neoecoae.platform.ui;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType.BlockUIHolder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import io.netty.buffer.Unpooled;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockUiLifecycleTest {
    private static final BlockPos POS = new BlockPos(1, 64, 2);

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void openingSnapshotSelectsTheServerTreeWhileTheClientWorldStillHasTheOldState() {
        var fixture = new Fixture();
        var formed = fixture.block.defaultBlockState().setValue(SnapshotBlock.FORMED, true);
        for (var state : fixture.block.getStateDefinition().getPossibleStates()) Block.BLOCK_STATE_REGISTRY.add(state);
        var outgoing = new BlockUIHolder(fixture.player, POS, formed);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            outgoing.writeToSyncData(buffer);
            buffer.writeInt(0x12345678);
            var received = BlockUIHolder.readFromSyncData(fixture.player, buffer);
            assertEquals(formed, received.blockState);
            assertEquals(POS, received.pos);
            assertFalse(fixture.level.getBlockState(POS).getValue(SnapshotBlock.FORMED));
            assertFalse(received.isInvalid());
            assertEquals(2, received.target().createUI(received).getRootElement().getChildren().size());
            assertEquals(0x12345678, buffer.readInt(), "Holder decoding must leave initial widget data intact");
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void replacementOfTheSameBlockInvalidatesTheOldHolder() {
        var fixture = new Fixture();
        var holder = new BlockUIHolder(fixture.player, POS);
        assertFalse(holder.isInvalid());
        fixture.entity.set(mock(BlockEntity.class));
        assertTrue(holder.isInvalid());
        assertFalse(new BlockUIHolder(fixture.player, POS).isInvalid());
    }

    @Test void removedEntitiesAndUnloadedChunksInvalidateTheHolder() {
        var fixture = new Fixture();
        var holder = new BlockUIHolder(fixture.player, POS);
        when(fixture.entity.get().isRemoved()).thenReturn(true);
        assertTrue(holder.isInvalid());
        when(fixture.entity.get().isRemoved()).thenReturn(false);
        when(fixture.level.hasChunkAt(POS)).thenReturn(false);
        clearInvocations(fixture.level);
        assertTrue(holder.isInvalid());
        verify(fixture.level, never()).getBlockEntity(POS);
    }

    @Test void staleMenusRejectButtonsAndNativeSlotMutationsBeforeTheirNextBroadcast() {
        var fixture = new Fixture();
        var holder = new BlockUIHolder(fixture.player, POS);
        var actions = new AtomicInteger();
        var clicks = new AtomicInteger();
        var button = new Button().setOnServerClick(event -> actions.incrementAndGet());
        var slot = new ItemSlot(new Slot(new SimpleContainer(1), 0, 0, 0))
            .nativeClickHandler((which, kind, player) -> { clicks.incrementAndGet(); return ItemStack.EMPTY; });
        var root = new UIElement().addChildren(button, slot);
        var retained = new ModularUI(UI.of(root), fixture.player);
        var nativeUi = retained.nativeUI(holder);
        var menu = mock(ModularUIContainer.class);
        when(menu.getModularUI()).thenReturn(nativeUi);
        when(menu.stillValid(fixture.player)).thenReturn(true); // LDLib's native implementation always returns true.
        nativeUi.setModularUIContainer(menu);
        fixture.player.containerMenu = menu;
        var widget = (SlotWidget) slot.widgets.get(0);
        var item = new ItemStack(Items.STONE);
        assertTrue(root.activeMenu());
        assertTrue(widget.getHandler().mayPlace(item));
        assertTrue(widget.getHandler().mayPickup(fixture.player));
        click(button);
        widget.slotClick(0, ClickType.PICKUP, fixture.player);
        assertEquals(1, actions.get());
        assertEquals(1, clicks.get());

        fixture.entity.set(mock(BlockEntity.class));
        assertTrue(menu.stillValid(fixture.player));
        assertFalse(root.activeMenu());
        click(button);
        assertFalse(widget.getHandler().mayPlace(item));
        assertFalse(widget.getHandler().mayPickup(fixture.player));
        assertSame(ItemStack.EMPTY, widget.slotClick(0, ClickType.PICKUP, fixture.player),
            "A rejected custom click must consume LDLib's vanilla fallback");
        assertEquals(1, actions.get());
        assertEquals(1, clicks.get());
    }

    private static void click(Button button) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { buffer.writeVarInt(0); button.handleClientAction(710, buffer); }
        finally { buffer.release(); }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        final SnapshotBlock block = new SnapshotBlock();
        final AtomicReference<BlockEntity> entity = new AtomicReference<>(mock(BlockEntity.class));
        Fixture() {
            when(player.level()).thenReturn(level);
            when(level.hasChunkAt(POS)).thenReturn(true);
            when(level.getBlockState(POS)).thenReturn(block.defaultBlockState());
            when(level.getBlockEntity(POS)).thenAnswer(call -> entity.get());
        }
    }

    private static final class SnapshotBlock extends Block implements BlockUIMenuType.BlockUI {
        static final BooleanProperty FORMED = BooleanProperty.create("formed");
        SnapshotBlock() { super(BlockBehaviour.Properties.of()); registerDefaultState(stateDefinition.any().setValue(FORMED, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FORMED); }
        @Override public ModularUI createUI(BlockUIHolder holder) {
            var root = new UIElement().addChild(new UIElement());
            if (holder.blockState.getValue(FORMED)) root.addChild(new UIElement());
            return new ModularUI(UI.of(root), holder.player);
        }
    }
}
