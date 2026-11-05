package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCellPriorities;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCellPriorities.Target;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEvent;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageCellPriorityUITest {
    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            Class.forName(NeoECOAE.class.getName());
            Class.forName(UIElement.class.getName());
            AccessorRegistries.init();
        }
    }

    @Test
    void realFloatingWindowCanBeAddedToTheSharedHostPanel() {
        try (var ldlib = mockStatic(com.lowdragmc.lowdraglib2.LDLib2.class)) {
            ldlib.when(com.lowdragmc.lowdraglib2.LDLib2::isRemote).thenReturn(true);
            var fixture = new Fixture();
            var controls = new StorageCellPriorityUI(fixture.player, fixture.access);
            var base = StoragePanelStateTest.config(List.of(), false);
            var root = StorageHostUI.create(base.withCellActions(controls.withActions(base.cellActions())));
            root.addChild(controls.window());
            assertSame(root, controls.window().getParent());
            controls.openPriority(fixture.target.position(), fixture.target.installation());
            assertEquals(dev.vfyjxf.taffy.style.TaffyDisplay.FLEX, controls.window().getLayout()
                .getValueImmediate(com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties.DISPLAY));
        }
    }

    @Test
    void serverSelectionAndFieldOrButtonEditsUseTheSharedAccess() {
        var fixture = new Fixture();
        try (var factory = mockStatic(StoragePriorityUI.class)) {
            factory.when(() -> StoragePriorityUI.createFloatingPanel(any())).thenAnswer(call -> {
                fixture.config.set(call.getArgument(0));
                return fixture.window;
            });
            var controls = new StorageCellPriorityUI(fixture.player, fixture.access);
            assertSame(fixture.window, controls.window());
            fixture.events.get(0).executor().apply(new Object[] {fixture.target.position(), fixture.target.installation()});
            verify(fixture.access).select(fixture.player, fixture.target);
            fixture.config.get().setPriority().accept(-12);
            fixture.config.get().changePriority().accept(100);
            verify(fixture.access).setPriority(fixture.player, fixture.target, -12);
            verify(fixture.access).changePriority(fixture.player, fixture.target, 100);
            fixture.events.get(1).executor().apply(new Object[] {fixture.target.position(), fixture.target.installation(), 72});
            fixture.events.get(2).executor().apply(new Object[] {fixture.target.position(), fixture.target.installation(), -10});
            verify(fixture.access).setPriority(fixture.player, fixture.target, 72);
            verify(fixture.access).changePriority(fixture.player, fixture.target, -10);

            clearInvocations(fixture.access);
            fixture.player.containerMenu = mock(AbstractContainerMenu.class);
            for (int i = 0; i < fixture.events.size(); i++) {
                fixture.events.get(i).executor().apply(i == 0
                    ? new Object[] {fixture.target.position(), fixture.target.installation()}
                    : new Object[] {fixture.target.position(), fixture.target.installation(), 10});
            }
            fixture.config.get().setPriority().accept(12);
            fixture.config.get().changePriority().accept(12);
            verifyNoInteractions(fixture.access);
        }
    }

    @Test
    void clientOpenAndEditsSendInstallationIdentityAndPreserveOtherCellActions() throws Exception {
        var fixture = new Fixture();
        var clientSide = Level.class.getDeclaredField("isClientSide");
        clientSide.setAccessible(true);
        clientSide.setBoolean(fixture.player.level(), true);
        try (var factory = mockStatic(StoragePriorityUI.class)) {
            factory.when(() -> StoragePriorityUI.createFloatingPanel(any())).thenAnswer(call -> {
                fixture.config.set(call.getArgument(0));
                return fixture.window;
            });
            var controls = new StorageCellPriorityUI(fixture.player, fixture.access);
            var actions = new StorageHostUI.CellActions((position, expected) -> {}, (position, expected) -> {},
                position -> {}, position -> new net.minecraft.nbt.CompoundTag(), (position, expected) -> {});
            var configured = controls.withActions(actions);
            assertSame(actions.migrateMarked(), configured.migrateMarked());
            assertSame(actions.openCompressionDisplay(), configured.openCompressionDisplay());
            assertSame(actions.previewMigration(), configured.previewMigration());
            assertSame(actions.openConfiguration(), configured.openConfiguration());
            configured.openPriority().accept(fixture.target.position(), fixture.target.installation());
            verify(fixture.window).setDisplay(true);
            fixture.config.get().setPriority().accept(-12);
            fixture.config.get().changePriority().accept(100);
            verify(fixture.emitters.get(0)).send(fixture.target.position(), fixture.target.installation());
            verify(fixture.emitters.get(1)).send(fixture.target.position(), fixture.target.installation(), -12);
            verify(fixture.emitters.get(2)).send(fixture.target.position(), fixture.target.installation(), 100);
            verify(fixture.access, never()).setPriority(any(), any(), anyInt());
            verify(fixture.access, never()).changePriority(any(), any(), anyInt());
        }
    }

    @Test
    void explicitActionsCanEnableALegacyConfigWithoutLosingItsOtherSettings() throws Exception {
        var base = StoragePanelStateTest.config(List.of(), false);
        var actions = new StorageHostUI.CellActions((position, expected) -> {}, (position, expected) -> {},
            position -> {}, position -> new net.minecraft.nbt.CompoundTag(), (position, expected) -> {});
        assertSame(StorageHostUI.CellActions.NONE, base.cellActions());
        var configured = base.withCellActions(actions);
        assertSame(actions, configured.cellActions());
        for (var component : StorageHostUI.Config.class.getRecordComponents()) {
            if (!component.getName().equals("cellActions")) {
                assertSame(component.getAccessor().invoke(base), component.getAccessor().invoke(configured), component.getName());
            }
        }
    }

    private static final class Fixture {
        final Player player = mock(Player.class);
        final UIElement window = mock(UIElement.class);
        final ECOStorageCellPriorities access = mock(ECOStorageCellPriorities.class);
        final Target target = new Target(BlockPos.ZERO, UUID.randomUUID());
        final List<RPCEvent> events = new ArrayList<>();
        final List<RPCEmitter> emitters = new ArrayList<>();
        final AtomicReference<StoragePriorityUI.Config> config = new AtomicReference<>();

        Fixture() {
            when(player.level()).thenReturn(mock(Level.class));
            when(player.getUUID()).thenReturn(UUID.randomUUID());
            var menu = mock(AbstractContainerMenu.class);
            player.containerMenu = menu;
            when(menu.stillValid(player)).thenReturn(true);
            var ui = mock(ModularUI.class);
            when(ui.getMenu()).thenReturn(menu);
            when(window.getModularUI()).thenReturn(ui);
            when(access.getSelectedTarget(player)).thenReturn(target);
            when(window.addRPCEvent(any(RPCEvent.class))).thenAnswer(call -> {
                events.add(call.getArgument(0));
                var emitter = mock(RPCEmitter.class);
                emitters.add(emitter);
                return emitter;
            });
        }
    }
}
