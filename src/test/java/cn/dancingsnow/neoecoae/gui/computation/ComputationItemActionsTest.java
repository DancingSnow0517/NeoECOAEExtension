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
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

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
                () -> 0L, allowed::get, cpus::get, ignored -> {}, ignored -> {}, net.minecraft.network.chat.Component::empty);
            when(cpu.getLogic()).thenReturn(logic);
            when(logic.getProcessingJobId()).thenReturn(job);
            var grid = mock(IGrid.class);
            when(cpu.getGrid()).thenReturn(grid);
            when(grid.getCraftingService()).thenReturn(crafting);
            var type = Class.forName(ComputationCpuStatusPanel.class.getName() + "$ServerState");
            var constructor = type.getDeclaredConstructor(ComputationControllerUI.Config.class, CpuSelectionState.Identities.class);
            constructor.setAccessible(true);
            state = constructor.newInstance(config, new CpuSelectionState.Identities<ECOCraftingCPU>());
            var select = type.getDeclaredMethod("select", int.class);
            select.setAccessible(true);
            select.invoke(state, 1);
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
