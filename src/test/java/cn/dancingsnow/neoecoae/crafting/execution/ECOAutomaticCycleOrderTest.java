package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.pattern.EncodedProcessingPattern;
import appeng.api.ids.AEComponents;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.ECOAutoCraftingPlanning;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Real asynchronous cycle planning, CPU submission, physical feedback and requester completion. */
class ECOAutomaticCycleOrderTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest @EnumSource(CalculationStrategy.class)
    void repeatedAutomaticGrowthOrdersReturnTheirSeedAndFinish(CalculationStrategy strategy) throws Exception {
        var seed = AEItemKey.of(Items.WHEAT_SEEDS);
        var fuel = AEItemKey.of(Items.BONE_MEAL);
        var encoded = mock(EncodedProcessingPattern.class);
        when(encoded.sparseInputs()).thenReturn(List.of(new GenericStack(seed, 1), new GenericStack(fuel, 1)));
        when(encoded.sparseOutputs()).thenReturn(List.of(new GenericStack(seed, 2)));
        var definition = mock(AEItemKey.class);
        when(definition.get(AEComponents.ENCODED_PROCESSING_PATTERN)).thenReturn(encoded);
        var pattern = new AEProcessingPattern(definition);
        var service = mock(CraftingService.class, withSettings().extraInterfaces(ECOCraftingNetworkSettings.class));
        var settings = (ECOCraftingNetworkSettings) service;
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var node = mock(IGridNode.class);
        var requester = mock(ICraftingRequester.class);
        var source = IActionSource.ofMachine(requester);
        var level = mock(Level.class);
        long[] gameTime = {0};
        when(level.getGameTime()).thenAnswer(call -> gameTime[0]);
        var cpu = mock(ECOCraftingCPU.class);
        var logic = new ECOCraftingCPULogic(cpu);
        var energy = mock(IEnergyService.class);
        var provider = mock(ICraftingProvider.class);
        var networkStock = new KeyCounter();
        networkStock.add(seed, 1);
        networkStock.add(fuel, 8);
        when(grid.getCraftingService()).thenReturn(service);
        when(node.getGrid()).thenReturn(grid);
        when(node.isActive()).thenReturn(true);
        when(requester.getActionableNode()).thenReturn(node);
        when(settings.neoecoae$isFastPlannerEnabled()).thenReturn(true);
        when(settings.neoecoae$isCyclePlanningEnabled()).thenReturn(true);
        when(settings.neoecoae$hasComputationHost(source)).thenReturn(true);
        when(service.getCraftingFor(seed)).thenReturn(List.of(pattern));
        when(service.getProviders(pattern)).thenReturn(List.of(provider));
        when(cpu.getGrid()).thenReturn(grid);
        when(cpu.getLevel()).thenReturn(level);
        when(cpu.getActionSource()).thenReturn(source);
        when(cpu.isActive()).thenReturn(true);
        when(cpu.getAvailableStorage()).thenReturn(Long.MAX_VALUE);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
        var storage = grid.getStorageService().getInventory();
        when(storage.getAvailableStacks()).thenAnswer(call -> {
            var snapshot = new KeyCounter();
            snapshot.addAll(networkStock);
            return snapshot;
        });
        when(storage.extract(any(), anyLong(), any(), any())).thenAnswer(call -> {
            AEKey key = call.getArgument(0);
            long count = Math.min(networkStock.get(key), call.<Long>getArgument(1));
            if (call.getArgument(2) == Actionable.MODULATE) networkStock.remove(key, count);
            return count;
        });
        when(storage.insert(any(), anyLong(), any(), any())).thenAnswer(call -> {
            if (call.getArgument(2) == Actionable.MODULATE)
                networkStock.add(call.getArgument(0), call.<Long>getArgument(1));
            return call.getArgument(1);
        });
        Map<UUID, CraftingLinkNexus> links = new HashMap<>();
        doAnswer(call -> {
            CraftingLink link = call.getArgument(0);
            link.setNexus(links.computeIfAbsent(link.getCraftingID(), CraftingLinkNexus::new));
            return null;
        }).when(service).addLink(any());
        long[] produced = {0}, delivered = {0};
        when(provider.pushPattern(eq(pattern), any())).thenAnswer(call -> { produced[0] += 2; return true; });
        when(requester.insertCraftedItems(any(), eq(seed), anyLong(), any())).thenAnswer(call -> {
            if (call.getArgument(3) == Actionable.MODULATE) delivered[0] += call.<Long>getArgument(2);
            return call.getArgument(2);
        });

        for (int order = 0; order < 2; order++) {
            var future = ECOAutoCraftingPlanning.begin(service, level, () -> source, seed, 4, strategy,
                    () -> { throw new AssertionError("Automatic cycle order fell through to AE2"); });
            var plan = future.get(15, TimeUnit.SECONDS);
            assertFalse(plan.simulation());
            assertNotNull(ECOPlanningResultRegistry.resolveExecutionPlan(plan));
            ICraftingSubmitResult result;
            try (var time = mockConstruction(ElapsedTimeTracker.class)) {
                result = logic.trySubmitJob(grid, plan, source, requester);
            }
            assertTrue(result.successful(), () -> String.valueOf(result.errorCode()));
            var link = result.link();
            assertNotNull(link);
            var requesterStateType = Class.forName("com.almostreliable.merequester.requester.status.LinkState");
            var requesterHostType = Class.forName("com.almostreliable.merequester.requester.RequesterBlockEntity");
            var requesterState = requesterStateType.getConstructor(ICraftingLink.class).newInstance(link);
            var pollRequester = requesterStateType.getMethod("handle", requesterHostType, int.class);
            assertSame(requesterState, pollRequester.invoke(requesterState, null, 0));
            for (int tick = 0; tick < 64 && logic.hasJob(); tick++) {
                gameTime[0]++;
                logic.tickCraftingLogic(energy, service);
                long returned = produced[0];
                produced[0] = 0;
                if (returned > 0) assertEquals(returned,
                        logic.insertForJob(link.getCraftingID(), seed, returned, Actionable.MODULATE));
            }
            assertFalse(logic.hasJob(), "Cycle order must finish after all physical feedback returns");
            assertTrue(link.isDone());
            var exportState = pollRequester.invoke(requesterState, null, 0);
            assertEquals("EXPORT", exportState.getClass().getMethod("type").invoke(exportState).toString(),
                    "The released ME Requester must leave its crafting state when ECO finishes");
            assertEquals((order + 1) * 4L, delivered[0]);
            // Idle inventory returns have a shared per-tick budget and may finish on the next tick.
            for (int tick = 0; tick < 4 && !logic.getInventory().list.isEmpty(); tick++) {
                gameTime[0]++;
                logic.tickCraftingLogic(energy, service);
            }
            assertEquals(1, networkStock.get(seed), "The next automatic order still needs its seed; planned="
                    + plan.patternTimes() + ", usedSeed=" + plan.usedItems().get(seed)
                    + ", retainedSeed=" + logic.getInventory().list.get(seed)
                    + ", retainedFuel=" + logic.getInventory().list.get(fuel));
            assertEquals(8 - (order + 1) * 4L, networkStock.get(fuel));
            assertEquals(0, produced[0]);
        }
        verify(requester, times(2)).jobStateChange(any());
    }
}
