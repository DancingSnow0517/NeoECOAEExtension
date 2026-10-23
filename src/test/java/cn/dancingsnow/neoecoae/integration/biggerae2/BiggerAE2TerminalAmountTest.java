package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.me.storage.MEInventoryHandler;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import appeng.util.prioritylist.PrecisePriorityList;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.format.ExactAmountFormatter;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountClientCache;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountComparator;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalExactAmountSync;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.DelegatingMEInventoryAccessor;
import cn.dancingsnow.neoecoae.network.ExactMapSync;
import cn.dancingsnow.neoecoae.network.MapDelta;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.RegistryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiggerAE2TerminalAmountTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @AfterEach
    void clear() {
        ExactAmountCollector.abort();
        TerminalInventorySnapshot.clear();
        ExactAmountClientCache.clear(0);
    }

    @Test
    void terminalSyncUpdatesWhileLongProjectionIsUnchangedAndRemovesEmptiedCell() {
        var key = AEItemKey.of(Items.STICK);
        var singularity = new BiggerAE2SingularityStorageCell(BiggerAE2StorageTest.stack(true), null);
        var ordinary = new ECOStorageCell(BiggerAE2StorageTest.stack(false), null);
        var amount = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(128));
        var menu = mock(MEStorageMenu.class);
        var player = mock(ServerPlayer.class);
        var level = mock(ServerLevel.class);
        when(menu.getPlayer()).thenReturn(player);
        when(player.level()).thenReturn(level);
        player.containerMenu = menu;
        var sync = new TerminalExactAmountSync();
        var frames = new ArrayList<Frame>();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        try (var storage = mockStatic(StorageCells.class);
                var keys = mockStatic(AEKeyTypesInternal.class);
                var registrySync = mockStatic(RegistryManager.class);
                var transport = mockStatic(MenuDataTransport.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            transport.when(() -> MenuDataTransport.send(eq(player), eq(MenuDataTransport.Channel.TERMINAL), any()))
                .thenAnswer(call -> {
                    Consumer<RegistryFriendlyByteBuf> encoder = call.getArgument(2);
                    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
                    try {
                        encoder.accept(buffer);
                        var frame = new Frame(buffer.readBoolean(), ExactMapSync.read(buffer));
                        frames.add(frame);
                        ExactAmountClientCache.apply(menu, frame.full(), frame.delta());
                        assertFalse(buffer.isReadable());
                    } finally { buffer.release(); }
                    return null;
                });

            singularity.insertBigInteger(key, amount, Actionable.MODULATE, null);
            ordinary.insert(key, 64, Actionable.MODULATE, null);
            assertEquals(Long.MAX_VALUE, sync.collect(menu, () -> list(singularity, ordinary)).get(key));
            assertTrue(frames.getFirst().full());
            assertEquals(ExactAmount.finite(amount.add(BigInteger.valueOf(64))),
                ExactAmountClientCache.get(menu.containerId, key));
            assertEquals("9.22E", ExactAmountFormatter.slot(key, ExactAmountClientCache.get(menu.containerId, key)));
            assertEquals("9,223,372,036,854,775,999",
                ExactAmountFormatter.full(key, ExactAmountClientCache.get(menu.containerId, key)));

            singularity.insert(key, 1, Actionable.MODULATE, null);
            when(level.getGameTime()).thenReturn(5L);
            assertEquals(Long.MAX_VALUE, sync.collect(menu, () -> list(ordinary, singularity)).get(key));
            assertEquals(2, frames.size());
            assertFalse(frames.getLast().full());
            assertEquals("9,223,372,036,854,776,000",
                ExactAmountFormatter.full(key, ExactAmountClientCache.get(menu.containerId, key)));

            assertEquals(Long.MAX_VALUE, singularity.extract(key, Long.MAX_VALUE, Actionable.MODULATE, null));
            when(level.getGameTime()).thenReturn(10L);
            assertEquals(193, sync.collect(menu, () -> list(singularity, ordinary)).get(key));
            assertNull(ExactAmountClientCache.get(menu.containerId, key));
            assertEquals(Set.of(key), frames.getLast().delta().removed());

            singularity.clearAllStoredStacks();
            when(level.getGameTime()).thenReturn(15L);
            assertEquals(64, sync.collect(menu, () -> list(singularity, ordinary)).get(key));
            assertNull(ExactAmountClientCache.get(menu.containerId, key));
            assertEquals(3, frames.size());
        }
    }

    @Test
    void itemAndFluidTerminalDisplaysUseExactQuantitiesAndTheirOwnUnits() {
        var displayAmount = new BigInteger("12345678901234567890");
        try (var storage = mockStatic(StorageCells.class)) {
            for (AEKey key : List.of(AEItemKey.of(Items.STICK), AEFluidKey.of(Fluids.WATER))) {
                var cell = new BiggerAE2SingularityStorageCell(BiggerAE2StorageTest.stack(true, key.getType()), null);
                var rawAmount = displayAmount.multiply(BigInteger.valueOf(key.getAmountPerUnit()));
                cell.insertBigInteger(key, rawAmount, Actionable.MODULATE, null);
                ExactAmountCollector.begin();
                assertEquals(Long.MAX_VALUE, list(cell).get(key));
                var exact = ExactAmountCollector.finish().get(key);
                assertEquals(ExactAmount.finite(rawAmount), exact);
                assertEquals("12.3E", ExactAmountFormatter.slot(key, exact));
                assertEquals("12,345,678,901,234,567,890", ExactAmountFormatter.full(key, exact));
            }
        }
    }

    @Test
    void multipleSingularityCellsAndOrdinaryStockKeepExactTotalsAndOrdering() {
        var smaller = AEItemKey.of(Items.STICK);
        var larger = AEItemKey.of(Items.PAPER);
        var first = new BiggerAE2SingularityStorageCell(BiggerAE2StorageTest.stack(true), null);
        var second = new BiggerAE2SingularityStorageCell(BiggerAE2StorageTest.stack(true), null);
        var ordinary = new ECOStorageCell(BiggerAE2StorageTest.stack(false), null);
        var amount = BigInteger.TEN.pow(30);
        try (var storage = mockStatic(StorageCells.class)) {
            first.insertBigInteger(smaller, amount, Actionable.MODULATE, null);
            first.insertBigInteger(larger, amount.multiply(BigInteger.TWO).add(BigInteger.valueOf(65)),
                Actionable.MODULATE, null);
            second.insertBigInteger(smaller, amount, Actionable.MODULATE, null);
            ordinary.insert(smaller, 64, Actionable.MODULATE, null);
            for (MEStorage[] order : new MEStorage[][]{{first, second, ordinary}, {ordinary, second, first}}) {
                ExactAmountCollector.begin();
                var visible = list(order);
                assertEquals(Long.MAX_VALUE, visible.get(smaller));
                assertEquals(Long.MAX_VALUE, visible.get(larger));
                var exact = ExactAmountCollector.finish();
                assertEquals(ExactAmount.finite(amount.multiply(BigInteger.TWO).add(BigInteger.valueOf(64))),
                    exact.get(smaller));
                ExactAmountClientCache.replace(0, exact);
                var left = new GridInventoryEntry(1, smaller, visible.get(smaller), 0, false);
                var right = new GridInventoryEntry(2, larger, visible.get(larger), 0, false);
                assertTrue(ExactAmountComparator.ascending(0).compare(left, right) < 0);
                assertTrue(ExactAmountComparator.ascending(0).reversed().compare(left, right) > 0);
            }
        }
    }

    @Test
    void filteredStorageBusDoesNotExposeOtherExactKeysOrSealedContents() {
        var key = AEItemKey.of(Items.STICK);
        var hidden = AEItemKey.of(Items.PAPER);
        var stack = BiggerAE2StorageTest.stack(true);
        var cell = new BiggerAE2SingularityStorageCell(stack, null);
        var amount = BigInteger.TEN.pow(30);
        try (var storage = mockStatic(StorageCells.class)) {
            cell.insertBigInteger(key, amount, Actionable.MODULATE, null);
            cell.insertBigInteger(hidden, amount.add(BigInteger.ONE), Actionable.MODULATE, null);
            var wrapper = new TestInventoryHandler(new TestInventoryHandler(cell));
            wrapper.setExtractFiltering(true, true);
            var whitelist = new KeyCounter();
            whitelist.set(key, 1);
            wrapper.setPartitionList(new PrecisePriorityList(whitelist));
            ExactAmountCollector.begin();
            assertEquals(Set.of(key), list(wrapper).keySet());
            assertEquals(Map.of(key, ExactAmount.finite(amount)), ExactAmountCollector.finish());

            ECOInfiniteStorageMember.markMember(stack, java.util.UUID.randomUUID());
            ExactAmountCollector.begin();
            assertTrue(list(cell, wrapper).isEmpty());
            assertTrue(ExactAmountCollector.finish().isEmpty());
        }
    }

    @Test
    void bothListingsSaturateWhenAppendingToAnExistingCounter() {
        var key = AEItemKey.of(Items.STICK);
        var cell = new BiggerAE2SingularityStorageCell(BiggerAE2StorageTest.stack(true), null);
        var amount = BigInteger.TEN.pow(30);
        try (var storage = mockStatic(StorageCells.class)) {
            cell.insertBigInteger(key, amount, Actionable.MODULATE, null);
            var visible = new KeyCounter();
            visible.set(key, 64);
            cell.getAvailableStacks(visible);
            assertEquals(Long.MAX_VALUE, visible.get(key));

            visible.set(key, 64);
            Map<AEKey, ExactAmount> exact = new HashMap<>();
            cell.neoecoae$listWithExactAmounts(visible, exact::put);
            assertEquals(Long.MAX_VALUE, visible.get(key));
            assertEquals(Map.of(key, ExactAmount.finite(amount)), exact);
        }
    }

    private static KeyCounter list(MEStorage... inventories) {
        var out = new KeyCounter();
        for (MEStorage inventory : inventories) {
            var contribution = new KeyCounter();
            ExactAmountCollector.collect(inventory, contribution, inventory::getAvailableStacks);
            SaturatingStackAccumulator.addAll(out, contribution);
        }
        return out;
    }

    private record Frame(boolean full, MapDelta<AEKey, ExactAmount> delta) {}

    private static final class TestInventoryHandler extends MEInventoryHandler implements DelegatingMEInventoryAccessor {
        TestInventoryHandler(MEStorage delegate) { super(delegate); }
        @Override public MEStorage neoecoae$getDelegate() { return getDelegate(); }
    }
}
