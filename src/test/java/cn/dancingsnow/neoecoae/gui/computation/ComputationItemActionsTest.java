package cn.dancingsnow.neoecoae.gui.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.compat.ae2.ECOProviderActions;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputationItemActionsTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(net.neoforged.fml.ModList.class)) {
            var modList = mock(net.neoforged.fml.ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(net.neoforged.fml.ModList::get).thenReturn(modList);
            Class.forName(cn.dancingsnow.neoecoae.NeoECOAE.class.getName());
            com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries.init();
        }
    }

    private Object previousMissingContentItem;
    @org.junit.jupiter.api.BeforeEach void bindFallbackItem() throws Exception {
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        previousMissingContentItem = itemField.get(appeng.core.definitions.AEItems.MISSING_CONTENT);
        var missingItem = org.mockito.Mockito.mock(net.neoforged.neoforge.registries.DeferredItem.class);
        org.mockito.Mockito.when(missingItem.get()).thenReturn(Items.BARRIER);
        org.mockito.Mockito.when(missingItem.asItem()).thenReturn(Items.BARRIER);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, missingItem);
    }
    @org.junit.jupiter.api.AfterEach void restoreFallbackItem() throws Exception {
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, previousMissingContentItem);
    }

    @Test void openingRechecksTheCurrentJobAndSourceRatherThanTrustingTheClient() throws Exception {
        try (var keys = mockStatic(AEKeyTypesInternal.class); var providers = mockStatic(ECOProviderActions.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var fixture = new Fixture();
            var source = mock(ECOProviderActions.Source.class);
            when(source.id()).thenReturn("provider");
            when(source.describe(any())).thenReturn(new CompoundTag());
            when(source.open(fixture.player)).thenReturn(true);
            providers.when(() -> ECOProviderActions.find(List.of(), fixture.crafting)).thenReturn(List.of(source));
            var request = fixture.request("inspect");
            assertEquals(fixture.job, fixture.send(1, request).getUUID("job"));

            request = fixture.request("open");
            request.putUUID("job", fixture.job);
            request.putString("source", "forged-provider");
            assertEquals("gui.neoecoae.processing.open_unavailable", fixture.send(1, request).getString("error"));
            verify(source, never()).open(any());
            request.putString("source", "provider");
            assertFalse(fixture.send(1, request).contains("error"));
            verify(source).open(fixture.player);

            clearInvocations(source);
            when(fixture.logic.getProcessingJobId()).thenReturn(UUID.randomUUID());
            assertEquals("gui.neoecoae.processing.task_changed", fixture.send(1, request).getString("error"));
            verify(source, never()).open(any());
        }
    }

    @Test void removedCpuWrongSerialAndLostAccessCannotOpenAProvider() throws Exception {
        try (var keys = mockStatic(AEKeyTypesInternal.class); var providers = mockStatic(ECOProviderActions.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var fixture = new Fixture();
            var request = fixture.request("inspect");
            var previous = fixture.send(2, request);
            assertEquals("gui.neoecoae.processing.task_changed", previous.getString("error"));
            fixture.allowed.set(false);
            assertSame(previous, fixture.send(1, request));
            fixture.allowed.set(true);
            fixture.cpus.set(List.of());
            assertEquals("gui.neoecoae.processing.task_changed", fixture.send(1, request).getString("error"));
            providers.verifyNoInteractions();
        }
    }

    @Test void pendingOverheatedTasksAppearInTheSidebarAndDisappearWhenAdmissionRecovers() throws Exception {
        var fixture = new Fixture();
        var key = AEItemKey.of(Items.STONE);
        var waiting = cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.summarize(List.of(
            new cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine(
                net.minecraft.network.chat.Component.literal("Workstation"), "minecraft:overworld [62, -60, -97]",
                "", 0, 0, 0,
                cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State.WAITING,
                net.minecraft.network.chat.Component.translatable(
                    "gui.neoecoae.large_integrated_working_station.pause.overheated"))));
        var monitor = mock(cn.dancingsnow.neoecoae.crafting.execution.ECOProcessingMonitor.class);
        when(fixture.logic.getProcessingMonitor()).thenReturn(monitor);
        when(monitor.cachedProgress(key)).thenReturn(waiting);
        var pending = new ComputationCpuItemEntry(key, java.math.BigInteger.ZERO, java.math.BigInteger.ZERO,
            java.math.BigInteger.TEN, false);
        var tasks = fixture.state.getClass().getDeclaredMethod("tasks", appeng.api.networking.crafting.ICraftingCPU.class,
            List.class);
        tasks.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rows = (List<ComputationCpuItemEntry>) tasks.invoke(fixture.state, fixture.cpu, List.of(pending));
        assertEquals(1, rows.size());
        assertEquals(java.math.BigInteger.ZERO, rows.getFirst().active());
        assertEquals(java.math.BigInteger.TEN, rows.getFirst().pending());
        assertFalse(rows.getFirst().processing().hasProgress());
        assertTrue(ComputationProcessingTooltips.rows(rows.getFirst().withProcessing(waiting))
            .toString().contains("pause.overheated"));
        verify(fixture.logic, never()).getProcessingProgress(any());

        when(monitor.cachedProgress(key)).thenReturn(
            cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.EMPTY);
        assertTrue(((List<?>) tasks.invoke(fixture.state, fixture.cpu, List.of(pending))).isEmpty());
        var active = new ComputationCpuItemEntry(key, java.math.BigInteger.ZERO, java.math.BigInteger.ONE,
            java.math.BigInteger.TEN, false);
        assertEquals(1, ((List<?>) tasks.invoke(fixture.state, fixture.cpu, List.of(active))).size());
    }

    @Test void processingDeltasKeepUnchangedMachinesAndIgnoreOtherCpuSelections() throws Exception {
        var fixture = new Fixture();
        var panel = mock(ComputationCpuStatusPanel.class, CALLS_REAL_METHODS);
        var type = ComputationCpuStatusPanel.class;
        var source = type.getDeclaredField("source"); source.setAccessible(true);
        source.set(panel, ComputationCpuStatusPanel.Source.of(fixture.config));
        var selected = type.getDeclaredField("selectedSerial"); selected.setAccessible(true); selected.setInt(panel, 7);
        var cached = type.getDeclaredField("processingByKey"); cached.setAccessible(true);
        var entries = new java.util.HashMap<appeng.api.stacks.AEKey,
            cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress>();
        cached.set(panel, entries);
        var receive = type.getDeclaredMethod("applyProcessingOverview", CompoundTag.class); receive.setAccessible(true);
        var stone = AEItemKey.of(Items.STONE);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var waiting = cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.summarize(List.of(
            new cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine(
                net.minecraft.network.chat.Component.literal("Workstation"), "minecraft:overworld", "", 0, 0, 0,
                cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State.WAITING,
                net.minecraft.network.chat.Component.literal("Overheated"))));
        var reset = new CompoundTag(); reset.putInt("serial", 7);
        var rows = new net.minecraft.nbt.ListTag();
        for (var key : List.of(stone, iron)) {
            var row = new CompoundTag();
            row.put("key", key.toTagGeneric(fixture.config.registries()));
            row.put("progress", waiting.write(fixture.config.registries()));
            rows.add(row);
        }
        reset.put("items", rows);
        receive.invoke(panel, reset);
        assertEquals(java.util.Map.of(stone, waiting, iron, waiting), entries);

        var delta = new CompoundTag(); delta.putInt("serial", 7); delta.putBoolean("delta", true);
        var removed = new net.minecraft.nbt.ListTag(); removed.add(stone.toTagGeneric(fixture.config.registries()));
        delta.put("removed", removed);
        receive.invoke(panel, delta);
        assertEquals(java.util.Map.of(iron, waiting), entries);
        var late = reset.copy(); late.putInt("serial", 6);
        receive.invoke(panel, late);
        assertEquals(java.util.Map.of(iron, waiting), entries);

        var empty = new CompoundTag(); empty.putInt("serial", 7);
        receive.invoke(panel, empty);
        assertTrue(entries.isEmpty());
    }

    private static final class Fixture {
        final ComputationControllerUI.Config config;
        final ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        final ECOCraftingCPULogic logic = mock(ECOCraftingCPULogic.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final CraftingService crafting = mock(CraftingService.class);
        final AtomicBoolean allowed = new AtomicBoolean(true);
        final java.util.concurrent.atomic.AtomicReference<List<ECOCraftingCPU>> cpus =
            new java.util.concurrent.atomic.AtomicReference<>(List.of(cpu));
        final UUID job = UUID.randomUUID();
        final Object state;
        final Method action;
        final Field response;

        Fixture() throws Exception {
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            when(player.registryAccess()).thenReturn(registries);
            config = new ComputationControllerUI.Config(
                () -> true, () -> 1, () -> 0, () -> 0,
                () -> 0L, () -> 1024L, () -> 1024L,
                () -> 0, () -> 8, () -> 4, () -> 16,
                () -> appeng.api.config.CpuSelectionMode.ANY, ignored -> {},
                () -> false, () -> 0, () -> {}, () -> true, () -> {}, () -> true, () -> {},
                () -> 1, ignored -> {}, () -> net.minecraft.world.item.ItemStack.EMPTY,
                List::of, List::of, player, (pos, expected, toInventory) -> false, (pos, expected) -> false,
                () -> 0L, allowed::get, cpus::get, ignored -> {}, ignored -> {}, net.minecraft.network.chat.Component::empty,
                () -> true, () -> {});
            when(cpu.getLogic()).thenReturn(logic);
            when(logic.getProcessingJobId()).thenReturn(job);
            var grid = mock(IGrid.class);
            when(cpu.getGrid()).thenReturn(grid);
            when(grid.getCraftingService()).thenReturn(crafting);
            var type = Class.forName(ComputationCpuStatusPanel.class.getName() + "$ServerState");
            var constructor = type.getDeclaredConstructor(ComputationCpuStatusPanel.Source.class, CpuSelectionState.Identities.class);
            constructor.setAccessible(true);
            state = constructor.newInstance(ComputationCpuStatusPanel.Source.of(config), new CpuSelectionState.Identities<ECOCraftingCPU>());
            var select = type.getDeclaredMethod("select", CompoundTag.class);
            select.setAccessible(true);
            var selection = new CompoundTag();
            selection.putInt("serial", 1);
            selection.putLong("sequence", 1);
            select.invoke(state, selection);
            action = type.getDeclaredMethod("itemAction", int.class, CompoundTag.class);
            action.setAccessible(true);
            response = type.getDeclaredField("itemActionResponse");
            response.setAccessible(true);
        }

        CompoundTag request(String action) {
            var request = new CompoundTag();
            request.putString("action", action);
            request.putLong("sequence", 7);
            request.put("key", AEItemKey.of(Items.STONE).toTagGeneric(config.registries()));
            return request;
        }

        CompoundTag send(int serial, CompoundTag request) throws Exception {
            action.invoke(state, serial, request);
            var tag = (CompoundTag) response.get(state);
            assertEquals(7, tag.getLong("sequence"));
            return tag;
        }
    }
}
