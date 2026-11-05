package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ECOAutoCraftingPlanningTest {
    final ICraftingService service = mock(ICraftingService.class,
            withSettings().extraInterfaces(ECOCraftingNetworkSettings.class));
    final ECOCraftingNetworkSettings settings = (ECOCraftingNetworkSettings) service;
    final IGrid grid = mock(IGrid.class);
    final IGridNode node = mock(IGridNode.class);
    final IActionSource source = mock(IActionSource.class);
    final ICraftingSimulationRequester requester = mock(ICraftingSimulationRequester.class);
    final Level level = mock(Level.class);
    final AEKey goal = mock(AEKey.class);
    final Supplier<Future<ICraftingPlan>> fallback = mock(Supplier.class);

    ECOAutoCraftingPlanningTest() {
        when(requester.getActionSource()).thenReturn(source);
        when(requester.getGridNode()).thenReturn(node);
        when(node.isActive()).thenReturn(true);
        when(node.getGrid()).thenReturn(grid);
        when(grid.getCraftingService()).thenReturn(service);
        when(settings.neoecoae$isFastPlannerEnabled()).thenReturn(true);
        when(settings.neoecoae$hasComputationHost(source)).thenReturn(true);
    }

    @ParameterizedTest @EnumSource(CalculationStrategy.class)
    void automaticOrdersUseCapturedCycleAndFuzzyOptionsWithTheirOriginalStrategy(CalculationStrategy strategy) {
        var fuzzy = Set.of(ResourceLocation.parse("minecraft:diamond_pickaxe"));
        when(settings.neoecoae$isCyclePlanningEnabled()).thenReturn(true);
        when(settings.neoecoae$isIgnoringPatternSubstitutions()).thenReturn(true);
        when(settings.neoecoae$getFuzzyPlanningItemIds()).thenReturn(fuzzy);
        var options = new ECOPlannerOptions(true, true, fuzzy);
        var expected = CompletableFuture.completedFuture(mock(ICraftingPlan.class));
        try (var planner = mockStatic(ECOPlanningService.class)) {
            planner.when(() -> ECOPlanningService.begin(level, grid, source, goal, 64, strategy, options))
                    .thenReturn(expected);
            assertSame(expected, begin(strategy));
            planner.verify(() -> ECOPlanningService.begin(level, grid, source, goal, 64, strategy, options));
            verifyNoInteractions(fallback);
        }
    }

    @Test void disabledCyclePlanningIsNotEnabledByTheAutomationBridge() {
        try (var planner = mockStatic(ECOPlanningService.class)) {
            begin(CalculationStrategy.CRAFT_LESS);
            planner.verify(() -> ECOPlanningService.begin(level, grid, source, goal, 64,
                    CalculationStrategy.CRAFT_LESS, new ECOPlannerOptions(false, false, Set.of())));
        }
    }

    @Test void unavailablePlayerOnlyHostKeepsTheOriginalPlanner() {
        when(settings.neoecoae$hasComputationHost()).thenReturn(true);
        when(settings.neoecoae$hasComputationHost(source)).thenReturn(false);
        assertFallback();
    }

    @Test void disabledFastPlannerKeepsTheOriginalPlanner() {
        when(settings.neoecoae$isFastPlannerEnabled()).thenReturn(false);
        assertFallback();
    }

    @Test void disconnectedMachineKeepsTheOriginalPlanner() {
        when(node.isActive()).thenReturn(false);
        assertFallback();
    }

    @Test void changedGridDoesNotPlanAgainstTheOldService() {
        when(grid.getCraftingService()).thenReturn(mock(ICraftingService.class));
        assertFallback();
    }

    @Test void missingSourceOrNodeDoesNotCrashTheMachineTick() {
        when(requester.getActionSource()).thenReturn(null);
        when(requester.getGridNode()).thenReturn(null);
        assertFallback();
    }

    @Test void fullPlannerQueueProducesAFailedFutureThatTheCallerCanRelease() {
        try (var planner = mockStatic(ECOPlanningService.class)) {
            planner.when(() -> ECOPlanningService.begin(any(), any(), any(), any(), anyLong(), any(), any()))
                    .thenThrow(new RejectedExecutionException("busy"));
            var future = begin(CalculationStrategy.CRAFT_LESS);
            assertTrue(future.isDone());
            assertInstanceOf(RejectedExecutionException.class,
                    assertThrows(java.util.concurrent.ExecutionException.class, future::get).getCause());
            verifyNoInteractions(fallback);
        }
    }

    private Future<ICraftingPlan> begin(CalculationStrategy strategy) {
        return ECOAutoCraftingPlanning.begin(service, level, requester, goal, 64, strategy, fallback);
    }

    private void assertFallback() {
        var expected = CompletableFuture.completedFuture(mock(ICraftingPlan.class));
        when(fallback.get()).thenReturn(expected);
        try (var planner = mockStatic(ECOPlanningService.class)) {
            assertSame(expected, begin(CalculationStrategy.CRAFT_LESS));
            verify(fallback).get();
            planner.verifyNoInteractions();
        }
    }
}
