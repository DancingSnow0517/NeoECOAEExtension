package cn.dancingsnow.neoecoae.mixins.compat.merequester;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.MachineSource;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.NativePatternTestSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MERequesterPlanningMixinTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private final IGrid grid = mock(IGrid.class);
    private final IGridNode node = mock(IGridNode.class);
    private final IActionHost host = mock(IActionHost.class);
    private final MachineSource source = new MachineSource(host);
    // ME Requester passes this method-reference-shaped requester, not its block entity.
    private final ICraftingSimulationRequester requester = () -> source;
    private final ICraftingService service = mock(ICraftingService.class,
        withSettings().extraInterfaces(ECOCraftingNetworkSettings.class));
    private final ECOCraftingNetworkSettings settings = (ECOCraftingNetworkSettings) service;
    private final KeyCounter stock = new KeyCounter();
    private final Level level = mock(Level.class);
    private final MERequesterPlanningMixin mixin = new MERequesterPlanningMixin();
    @SuppressWarnings("unchecked")
    private final Operation<Future<ICraftingPlan>> original = mock(Operation.class);

    MERequesterPlanningMixinTest() {
        when(host.getActionableNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        when(grid.getCraftingService()).thenReturn(service);
        when(grid.getMachineClasses()).thenReturn(Set.of());
        var storageService = mock(IStorageService.class);
        var inventory = mock(MEStorage.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(inventory);
        when(inventory.getAvailableStacks()).thenReturn(stock);
        when(settings.neoecoae$isFastPlannerEnabled()).thenReturn(true);
        when(settings.neoecoae$hasComputationHost(source)).thenReturn(true);
        when(settings.neoecoae$isCyclePlanningEnabled()).thenReturn(true);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void machineRequestsUseTheRealCyclePlannerAndRespectItsNetworkSwitch(boolean cycles) throws Exception {
        var seed = AEItemKey.of(Items.DIAMOND);
        var fuel = AEItemKey.of(Items.COAL);
        var growth = NativePatternTestSupport.processing(
            List.of(new GenericStack(seed, 1), new GenericStack(fuel, 1)),
            List.of(new GenericStack(seed, 2)));
        stock.add(seed, 1);
        stock.add(fuel, 10);
        when(service.getCraftingFor(seed)).thenReturn(List.of(growth));
        when(settings.neoecoae$isCyclePlanningEnabled()).thenReturn(cycles);

        var request = mixin.neoecoae$planWithEco(service, level, requester, seed, 10,
            CalculationStrategy.CRAFT_LESS, original);
        try {
            assertInstanceOf(ECOPlanningService.Request.class, request);
            var plan = request.get(10, TimeUnit.SECONDS);
            var result = ECOPlanningResultRegistry.find(plan);
            assertNotNull(result);
            if (cycles) {
                assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
                assertFalse(plan.simulation());
                assertEquals(10, plan.finalOutput().amount());
                assertTrue(plan.patternTimes().getOrDefault(growth, 0L) > 0);
                assertNotNull(result.executionContract().executionPlan());
            } else {
                assertTrue(plan.simulation(), "Disabled cycle planning must not submit a partial circuit");
                assertNotEquals(PlanningStatus.SUCCESS, result.status());
            }
            assertEquals(1, stock.get(seed));
            assertEquals(10, stock.get(fuel));
            verifyNoInteractions(original);
        } finally { request.cancel(true); }
    }

    @Test void requesterCraftLessKeepsTheLargestPhysicallyAvailableAmount() throws Exception {
        var raw = AEItemKey.of(Items.RAW_IRON);
        var output = AEItemKey.of(Items.IRON_INGOT);
        var pattern = NativePatternTestSupport.processing(List.of(new GenericStack(raw, 1)),
            List.of(new GenericStack(output, 1)));
        stock.add(raw, 3);
        when(service.getCraftingFor(output)).thenReturn(List.of(pattern));
        var request = mixin.neoecoae$planWithEco(service, level, requester, output, 10,
            CalculationStrategy.CRAFT_LESS, original);
        try {
            var plan = request.get(10, TimeUnit.SECONDS);
            assertFalse(plan.simulation());
            assertEquals(3, plan.finalOutput().amount());
            assertEquals(3, plan.usedItems().get(raw));
            assertEquals(3, stock.get(raw));
            verifyNoInteractions(original);
        } finally { request.cancel(true); }
    }

    @ParameterizedTest @ValueSource(strings = {"disabled", "sourceRejected", "detached", "differentGrid", "noEco"})
    void unavailableEcoKeepsTheOriginalFutureAndRequest(String reason) {
        ICraftingService originalService = service;
        switch (reason) {
            case "disabled" -> when(settings.neoecoae$isFastPlannerEnabled()).thenReturn(false);
            case "sourceRejected" -> when(settings.neoecoae$hasComputationHost(source)).thenReturn(false);
            case "detached" -> when(host.getActionableNode()).thenReturn(null);
            case "differentGrid" -> when(grid.getCraftingService()).thenReturn(mock(ICraftingService.class));
            case "noEco" -> originalService = mock(ICraftingService.class);
        }
        var output = AEItemKey.of(Items.DIAMOND);
        long amount = (long) Integer.MAX_VALUE + 123L;
        var future = new CompletableFuture<ICraftingPlan>();
        when(original.call(originalService, level, requester, output, amount, CalculationStrategy.CRAFT_LESS))
            .thenReturn(future);
        assertSame(future, mixin.neoecoae$planWithEco(originalService, level, requester, output, amount,
            CalculationStrategy.CRAFT_LESS, original));
        verify(original).call(originalService, level, requester, output, amount, CalculationStrategy.CRAFT_LESS);
        assertFalse(future.isCancelled());
        verifyNoInteractions(grid.getStorageService().getInventory());
    }
}
